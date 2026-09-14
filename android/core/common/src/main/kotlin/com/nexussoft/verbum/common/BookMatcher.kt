package com.nexussoft.verbum.common

import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.localizedName
import com.nexussoft.verbum.models.localizedAbbreviations
import java.text.Normalizer
import java.util.Locale

/**
 * Finds books a partial query could mean: `sam` → 1 Samuel, 2 Samuel; `jn` → John;
 * `song` → Song of Solomon. Pure, deterministic, canon-ordered. Mirrors iOS.
 */
object BookMatcher {
    /**
     * Books whose name, any word of the name, or any abbreviation starts with the query
     * (case-insensitive, periods and spaces ignored). Books the query names exactly come
     * first (`jn` → John before Jonah); within each group, canon order. Empty query → nothing.
     */
    fun books(matching: String, language: BookLanguage = BookLanguage.ENGLISH): List<BibleBook> {
        val key = normalize(matching)
        if (key.isEmpty()) return emptyList()
        val exact = mutableListOf<BibleBook>()
        val prefix = mutableListOf<BibleBook>()
        for (book in BibleBook.canon) {
            val keys = listOf(book.name, book.localizedName(language)) + book.abbreviations + book.localizedAbbreviations(language)
            when {
                keys.any { normalize(it) == key } -> exact += book
                keys.any { normalize(it).startsWith(key) } ||
                    listOf(book.name, book.localizedName(language)).flatMap { it.split(" ") }.any { word -> normalize(word).startsWith(key) && !word.all(Char::isDigit) } -> prefix += book
            }
        }
        return exact + prefix
    }

    internal fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).lowercase(Locale.ROOT).filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() || it == '.' || it.isWhitespace() }
}
