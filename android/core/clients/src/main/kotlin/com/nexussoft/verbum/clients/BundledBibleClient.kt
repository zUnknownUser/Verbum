package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.BiblePassage
import com.nexussoft.verbum.models.BookId
import com.nexussoft.verbum.models.PassageReference

/**
 * The World English Bible, bundled with the app (`web.tsv`: `bookId ⇥ chapter ⇥ verse ⇥ text`).
 * Public domain, so every chapter reads offline from day one (docs/PRODUCT.md §39). Not a
 * fixture: real text of a real translation. Parsed once, on first use.
 */
object BundledBibleClient : BibleClient {
    const val TRANSLATION_ID = "web"

    private data class ChapterKey(val bookId: BookId, val chapter: Int)

    private val chapters: Map<ChapterKey, List<BiblePassage>> by lazy {
        val stream = BundledBibleClient::class.java.classLoader.getResourceAsStream("web.tsv")
            ?: error("web.tsv is missing from the clients resources")
        val grouped = LinkedHashMap<ChapterKey, MutableList<BiblePassage>>()
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val f = line.split('\t', limit = 4)
                if (f.size != 4) continue
                val chapter = f[1].toIntOrNull() ?: continue
                val verse = f[2].toIntOrNull() ?: continue
                grouped.getOrPut(ChapterKey(f[0], chapter)) { mutableListOf() } +=
                    BiblePassage(passageId(f[0], chapter, verse..verse), TRANSLATION_ID, f[0], chapter, verse, verse, f[3])
            }
        }
        grouped.mapValues { (_, v) -> v.sortedBy { it.verseStart } }
    }

    /** Chapters the bundled translation can serve — all 1,189 of the canon. */
    val availableChapters: List<PassageReference>
        get() = chapters.keys.map { PassageReference(it.bookId, it.chapter) }
            .sortedWith(compareBy({ BibleBook.book(it.bookId)?.order ?: Int.MAX_VALUE }, { it.chapter }))

    override suspend fun chapter(bookId: BookId, chapter: Int): List<BiblePassage> {
        requireKnownBook(bookId)
        return chapters[ChapterKey(bookId, chapter)] ?: throw BibleClientException.ContentUnavailable(PassageReference(bookId, chapter))
    }

    override suspend fun passage(reference: PassageReference): BiblePassage {
        val verses = chapter(reference.bookId, reference.chapter)
        val range = reference.verses ?: 1..verses.size
        if (range.last > verses.size) throw BibleClientException.VerseOutOfRange(reference, verses.size)
        return BiblePassage(
            id = passageId(reference.bookId, reference.chapter, range),
            translationId = TRANSLATION_ID, bookId = reference.bookId, chapter = reference.chapter,
            verseStart = range.first, verseEnd = range.last,
            text = verses.subList(range.first - 1, range.last).joinToString(" ") { it.text },
        )
    }

    private fun passageId(bookId: BookId, chapter: Int, verses: IntRange): String {
        val range = if (verses.first == verses.last) "${verses.first}" else "${verses.first}-${verses.last}"
        return "$TRANSLATION_ID:$bookId.$chapter.$range"
    }
}
