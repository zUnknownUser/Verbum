package com.nexussoft.verbum.common

import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.localizedAbbreviations
import com.nexussoft.verbum.models.localizedName
import com.nexussoft.verbum.models.PassageReference

/**
 * Why a string could not be read as a passage reference (docs/PRODUCT.md §52:
 * the UI distinguishes error kinds, it never shows raw parser output).
 */
sealed interface PassageReferenceParseError {
    data object Empty : PassageReferenceParseError
    /** The text does not have the shape `Book chapter[:verse[-verse]]`. */
    data object Malformed : PassageReferenceParseError
    /** The book part did not match any canon name or abbreviation. */
    data class UnknownBook(val text: String) : PassageReferenceParseError
    /** Chapter is 0 or exceeds the book's chapter count. */
    data class ChapterOutOfRange(val chapter: Int, val book: BibleBook) : PassageReferenceParseError
    /** Verse is 0, or the range end precedes its start. */
    data object InvalidVerseRange : PassageReferenceParseError
}

sealed interface PassageReferenceParseResult {
    data class Parsed(val reference: PassageReference) : PassageReferenceParseResult
    data class Failed(val error: PassageReferenceParseError) : PassageReferenceParseResult

    val referenceOrNull: PassageReference? get() = (this as? Parsed)?.reference
    val errorOrNull: PassageReferenceParseError? get() = (this as? Failed)?.error
}

/**
 * Deterministic parser for human-typed references (docs/PRODUCT.md §60 Task 3).
 * Mirrors the iOS `PassageReferenceParser` rule for rule.
 *
 * Accepted shapes, whitespace-insensitive and case-insensitive:
 *
 *     John 3          John 3:16       John 3:16-18
 *     Jn 3:16         Jn. 3:16        1 Samuel 17
 *     1Sam 17         I Samuel 17     II Sam 5:3–5
 *     Psalm 23        Song of Solomon 2:1
 *
 * Single-chapter books read `Jude 3` as verse 3 of chapter 1, the usual
 * convention. `:` and `.` both separate chapter from verse; `-`, `–` and `—`
 * all separate a verse range.
 *
 * Verse numbers are validated for shape only (≥ 1, start ≤ end). The model
 * has no per-chapter verse counts; the `BibleClient` is the authority there.
 */
object PassageReferenceParser {
    /**
     * Parses in the device language: English names and abbreviations are always accepted;
     * the current language's are accepted too and win on conflict (`Jn` is Jonas on a
     * Portuguese device, John elsewhere).
     */
    fun parse(input: String): PassageReferenceParseResult = parse(input, BookLanguage.current)

    fun parse(input: String, language: BookLanguage): PassageReferenceParseResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return fail(PassageReferenceParseError.Empty)

        val match = SHAPE.matchEntire(trimmed) ?: return fail(PassageReferenceParseError.Malformed)
        val (bookText, chapterText, verseStartText, verseEndText) = match.destructured

        val book = bookMatching(bookText, language)
            ?: return fail(PassageReferenceParseError.UnknownBook(bookText.trim()))
        val chapter = chapterText.toIntOrNull() ?: return fail(PassageReferenceParseError.Malformed)

        var verses: IntRange? = null
        if (verseStartText.isNotEmpty()) {
            val start = verseStartText.toIntOrNull()
            if (start == null || start < 1) return fail(PassageReferenceParseError.InvalidVerseRange)
            val end = if (verseEndText.isNotEmpty()) {
                val parsedEnd = verseEndText.toIntOrNull()
                if (parsedEnd == null || parsedEnd < start) return fail(PassageReferenceParseError.InvalidVerseRange)
                parsedEnd
            } else {
                start
            }
            verses = start..end
        }

        // "Jude 3" means verse 3 of the only chapter.
        if (book.chapterCount == 1 && verses == null && chapter > 1) {
            return PassageReferenceParseResult.Parsed(PassageReference(book.id, 1, chapter..chapter))
        }

        if (chapter !in 1..book.chapterCount) {
            return fail(PassageReferenceParseError.ChapterOutOfRange(chapter, book))
        }
        return PassageReferenceParseResult.Parsed(PassageReference(book.id, chapter, verses))
    }

    private fun fail(error: PassageReferenceParseError) = PassageReferenceParseResult.Failed(error)

    // Shape: book, chapter, optional verse start, optional verse end.
    // Letters include accented ones so `Gênesis 1`, `Jó 1`, `Êxodo 3` parse.
    private val SHAPE = Regex("""^\s*([1-3]?\s*\p{L}[\p{L}. ]*?)\s*(\d+)(?:\s*[:.]\s*(\d+)(?:\s*[-–—]\s*(\d+))?)?\s*$""")
    private val ROMAN_PREFIX = Regex("""^(I{1,3})\s+""")

    private fun bookMatching(text: String, language: BookLanguage): BibleBook? {
        val key = normalizedKey(text)
        // Exact accented names win: Jó is Job, while Jo is John.
        booksByKey(language)[key]?.let { return it }
        return BibleBook.canon.firstOrNull { book ->
            (listOf(book.name, book.id, book.localizedName(language)) + book.abbreviations + book.localizedAbbreviations(language))
                .any { BookMatcher.normalize(it) == BookMatcher.normalize(key) }
        }
    }

    /**
     * Lowercased, periods and whitespace removed, leading Roman numeral (when
     * followed by a space, so "Isaiah" is untouched) converted to a digit.
     */
    internal fun normalizedKey(text: String): String {
        var s = text.trim()
        ROMAN_PREFIX.find(s)?.let { roman ->
            s = roman.groupValues[1].length.toString() + s.substring(roman.value.length)
        }
        return s.lowercase().filterNot { it == '.' || it.isWhitespace() }
    }

    private val englishKeys: Map<String, BibleBook> = buildMap {
        for (book in BibleBook.canon) {
            put(normalizedKey(book.name), book)
            put(normalizedKey(book.id), book)
            for (abbreviation in book.abbreviations) put(normalizedKey(abbreviation), book)
        }
    }

    /** English keys first, then the language's own on top so they win conflicts. */
    private fun booksByKey(language: BookLanguage): Map<String, BibleBook> {
        if (language == BookLanguage.ENGLISH) return englishKeys
        return buildMap {
            putAll(englishKeys)
            for (book in BibleBook.canon) {
                put(normalizedKey(book.localizedName(language)), book)
                for (abbreviation in book.localizedAbbreviations(language)) put(normalizedKey(abbreviation), book)
            }
        }
    }
}
