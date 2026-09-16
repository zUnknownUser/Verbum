package com.nexussoft.verbum.clients.helloao

import com.nexussoft.verbum.clients.BibleClient
import com.nexussoft.verbum.clients.BibleClientException
import com.nexussoft.verbum.clients.BundledBibleClient
import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.BiblePassage
import com.nexussoft.verbum.models.BookId
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Fetches the text at a URL; injected so tests never touch the network. */
fun interface Transport {
    suspend fun get(url: String): String
}

/** Plain `HttpURLConnection`: a GET of a static file is all this API needs. */
object UrlConnectionTransport : Transport {
    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            if (connection.responseCode == 404) throw BibleClientException.ContentUnavailable(PassageReference(url.substringAfterLast('/'), 0))
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Reads Scripture from bible.helloao.org and keeps every chapter it has seen in [cache]
 * so it reads again offline (§39). Mirrors iOS `HelloAOBibleClient`.
 */
class HelloAOBibleClient(
    val translationId: String,
    private val transport: Transport = UrlConnectionTransport,
    private val cache: ChapterCache = ChapterCache(null),
) : BibleClient {
    companion object {
        const val BASE_URL = "https://bible.helloao.org/api"
    }

    override suspend fun chapter(bookId: BookId, chapter: Int): List<BiblePassage> = withContext(Dispatchers.IO) {
        val book = BibleBook.book(bookId) ?: throw BibleClientException.UnknownBook(bookId)
        val reference = PassageReference(bookId, chapter)
        val usfm = HelloAOBooks.usfmByOsis[bookId]
        if (chapter !in 1..book.chapterCount || usfm == null) throw BibleClientException.ContentUnavailable(reference)
        cache.read(translationId, reference)?.takeIf { it.isNotEmpty() && it.all { verse -> verse.translationId == translationId } }?.let { return@withContext it }
        val body = try {
            transport.get("$BASE_URL/$translationId/$usfm/$chapter.json")
        } catch (e: BibleClientException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw BibleClientException.NetworkUnavailable
        }
        val passages = HelloAOChapter.passages(body, bookId)
        if (passages.isEmpty() || passages.any { it.translationId != translationId }) throw BibleClientException.ContentUnavailable(reference)
        cache.write(translationId, reference, passages)
        passages
    }

    override suspend fun passage(reference: PassageReference): BiblePassage {
        val verses = chapter(reference.bookId, reference.chapter)
        val range = reference.verses ?: 1..verses.size
        if (range.last > verses.size) throw BibleClientException.VerseOutOfRange(reference, verses.size)
        val rangeText = if (range.first == range.last) "${range.first}" else "${range.first}-${range.last}"
        return BiblePassage(
            "$translationId:${reference.bookId}.${reference.chapter}.$rangeText", translationId, reference.bookId, reference.chapter,
            range.first, range.last, verses.subList(range.first - 1, range.last).joinToString(" ") { it.text },
        )
    }
}

/** Chapters already read, as JSON files in [directory] (null = memory only). */
class ChapterCache(private val directory: File?) {
    private val memory = object : LinkedHashMap<String, List<BiblePassage>>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<BiblePassage>>): Boolean = size > 24
    }
    private val json = Json
    private val serializer = ListSerializer(BiblePassageJson.serializer())

    @Synchronized
    fun read(translationId: String, reference: PassageReference): List<BiblePassage>? {
        val key = key(translationId, reference)
        memory[key]?.let { return it }
        val file = file(key)?.takeIf { it.exists() } ?: return null
        return runCatching { json.decodeFromString(serializer, file.readText()).map { it.toModel() } }.getOrNull()?.also { memory[key] = it }
    }

    @Synchronized
    fun write(translationId: String, reference: PassageReference, passages: List<BiblePassage>) {
        val key = key(translationId, reference)
        memory[key] = passages
        val file = file(key) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(serializer, passages.map { BiblePassageJson.of(it) }))
        }
    }

    private fun key(translationId: String, reference: PassageReference) = "${translationId}_${reference.bookId}_${reference.chapter}"
    private fun file(key: String) = directory?.let { File(it, "$key.json") }
}

@kotlinx.serialization.Serializable
internal data class BiblePassageJson(val id: String, val translationId: String, val bookId: String, val chapter: Int, val verseStart: Int, val verseEnd: Int, val text: String) {
    fun toModel() = BiblePassage(id, translationId, bookId, chapter, verseStart, verseEnd, text)
    companion object {
        fun of(p: BiblePassage) = BiblePassageJson(p.id, p.translationId, p.bookId, p.chapter, p.verseStart, p.verseEnd, p.text)
    }
}

/**
 * Production client: helloao in the device language, with two safety nets — chapters read
 * before (cache) and, for English, the bundled WEB — so the reader never dead-ends offline.
 */
class LiveBibleClient(language: BookLanguage, private val remote: BibleClient = HelloAOBibleClient(HelloAOTranslation.id(language))) : BibleClient {
    private val offline: BibleClient? = if (language == BookLanguage.ENGLISH) BundledBibleClient else null

    override suspend fun chapter(bookId: BookId, chapter: Int): List<BiblePassage> = try {
        remote.chapter(bookId, chapter)
    } catch (e: BibleClientException.NetworkUnavailable) {
        offline?.chapter(bookId, chapter) ?: throw e
    }

    override suspend fun passage(reference: PassageReference): BiblePassage = try {
        remote.passage(reference)
    } catch (e: BibleClientException.NetworkUnavailable) {
        offline?.passage(reference) ?: throw e
    }
}
