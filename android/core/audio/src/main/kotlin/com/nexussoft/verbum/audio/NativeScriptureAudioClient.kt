package com.nexussoft.verbum.audio

import android.content.Context
import com.nexussoft.verbum.clients.BibleClient
import com.nexussoft.verbum.clients.api.VerbumApi
import com.nexussoft.verbum.clients.api.decodeAudioCues
import com.nexussoft.verbum.clients.api.encodeAudioCues
import com.nexussoft.verbum.clients.helloao.HelloAOScriptureAudioClient
import com.nexussoft.verbum.clients.helloao.ScriptureAudioClient
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Production audio: in Portuguese the backend's Google Cloud voice reads the translation on
 * screen aloud (no Portuguese recordings exist for these translations); in English the helloao
 * recordings. If the cloud voice cannot be reached, the English recordings are offered,
 * labelled as such — never passed off as the reading. Twin of iOS `ScriptureAudioClient.live`.
 */
class LiveScriptureAudioClient(language: BookLanguage, context: Context, bible: BibleClient, api: VerbumApi) : ScriptureAudioClient {
    private val recordings = HelloAOScriptureAudioClient(language)
    private val cloud: ScriptureAudioClient? = if (language == BookLanguage.PORTUGUESE) CloudScriptureAudioClient(context, bible, api) else null

    override suspend fun chapterAudio(bookId: BookId, chapter: Int): ChapterAudio? {
        val cloud = cloud ?: return recordings.chapterAudio(bookId, chapter)
        return try {
            cloud.chapterAudio(bookId, chapter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordings.chapterAudio(bookId, chapter)
        }
    }
}

/**
 * Generates chapter speech via the backend's Google Cloud TTS (`POST /v1/tts`), then uses the
 * existing MediaSession player for pause, seek, speed and background playback. The next chapter
 * is rendered in the background once this one is, so chaining does not wait.
 */
class CloudScriptureAudioClient(context: Context, private val bible: BibleClient, private val api: VerbumApi) : ScriptureAudioClient {
    private val prefetch = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val context = context.applicationContext
    private val mutex = Mutex()

    override suspend fun chapterAudio(bookId: BookId, chapter: Int): ChapterAudio? {
        val audio = renderChapter(bookId, chapter)
        nextChapter(bookId, chapter)?.let { next -> prefetch.launch { runCatching { renderChapter(next.bookId, next.chapter) } } }
        return audio
    }

    private suspend fun renderChapter(bookId: BookId, chapter: Int): ChapterAudio? = mutex.withLock {
        val verses = bible.chapter(bookId, chapter)
        val first = verses.firstOrNull() ?: return@withLock null
        val text = verses.joinToString("\n") { it.text }
        val (file,cues) = render(verses)
        ChapterAudio(first.translationId, "Leitura automática · Português", PassageReference(bookId, chapter),
            listOf(AudioNarrator(AudioNarrator.SYNTHESISED_PREFIX + "pt-BR", "Leitura automática", file.toURI().toString(), null,cues)))
    }

    private fun nextChapter(bookId: BookId, chapter: Int): PassageReference? {
        val book = BibleBook.book(bookId) ?: return null
        if (chapter < book.chapterCount) return PassageReference(bookId, chapter + 1)
        val nextBook = BibleBook.canon.firstOrNull { it.order == book.order + 1 } ?: return null
        return PassageReference(nextBook.id, 1)
    }

    /** One MP3 per exact chapter text and server voice version, cached on disk. The backend also caches server-side by
     * the same text, so a cold local cache (after reinstall, or a pruned entry) still answers
     * without paying for a new generation — only the round trip. */
    private suspend fun render(verses:List<BiblePassage>): Pair<File,List<AudioCue>> {
        val text=verses.joinToString("\n") {it.text}
        val directory = File(context.cacheDir, "cloud-speech-pt-BR-v1").also { check(it.isDirectory || it.mkdirs()) }
        // One manifest check/hour, with the API cache's offline fallback. Older servers
        // keep using legacy files until the version endpoint is deployed.
        val version = try { api.speechVersion("pt-BR") }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        val identity = "sync-v1\n"+verses.joinToString(",") {it.verseStart.toString()}+"\n"+(version?.let { "$it\npt-BR\n$text" } ?: text)
        val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val output = File(directory, "$key.mp3")
        if (output.exists()) {
            output.setLastModified(System.currentTimeMillis())
            return output to decodeAudioCues(runCatching {File(output.path+".json").readText()}.getOrNull())
        }
        val (audio,cues) = try {withContext(Dispatchers.IO) {api.synthesizeChapterSpeech(verses,"pt-BR",revision=version)}}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {withContext(Dispatchers.IO) {api.synthesizeSpeech(text,"pt-BR",revision=version)} to emptyList<AudioCue>()}
        check(audio.isNotEmpty())
        currentCoroutineContext().ensureActive()
        val temporary = File(directory, "${UUID.randomUUID()}.partial.mp3")
        temporary.writeBytes(audio)
        if (output.exists()) temporary.delete() else check(temporary.renameTo(output))
        val metadata=File(output.path+".json");val metadataTemp=File(directory,"${UUID.randomUUID()}.partial.json")
        runCatching {metadataTemp.writeText(encodeAudioCues(cues));check(metadataTemp.renameTo(metadata))}.onFailure {metadataTemp.delete()}
        // Newest first: at most 40 chapters (MP3 at 128 kbit/s is ~1 MB/minute; Psalm 119 is ~13 MB).
        var bytes = 0L
        directory.listFiles().orEmpty().filter { it.extension == "mp3" && !it.name.endsWith(".partial.mp3") }
            .sortedByDescending { it.lastModified() }.forEachIndexed { index, file ->
                bytes += file.length()
                if (file != output && (index >= 40 || bytes > 150_000_000L)) {file.delete();File(file.path+".json").delete()}
            }
        return output to cues
    }
}
