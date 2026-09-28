package com.nexussoft.verbum.models

import java.text.Normalizer
import java.util.Locale

enum class ReadingCollectionFilter { ALL, SAVED, HIGHLIGHTS, NOTES }

/** Local references and personal notes only; no network search. */
object ReadingCollection {
    fun entries(annotations: List<ReaderAnnotation>, filter: ReadingCollectionFilter, query: String): List<ReaderAnnotation> {
        val terms = normalized(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return annotations.filter { item ->
            val included = when (filter) {
                ReadingCollectionFilter.ALL -> item.bookmarked || item.highlight != null || item.note.isNotEmpty()
                ReadingCollectionFilter.SAVED -> item.bookmarked
                ReadingCollectionFilter.HIGHLIGHTS -> item.highlight != null
                ReadingCollectionFilter.NOTES -> item.note.isNotEmpty()
            }
            val book = BibleBook.book(item.reference.bookId)
            val names = BookLanguage.entries.map { book?.localizedName(it).orEmpty() }
            val reference = "${item.reference.chapter}:${item.reference.verses?.first ?: 1}"
            val haystack = normalized((listOf(item.reference.bookId, reference, item.note) + names).joinToString(" "))
            included && terms.all { it in haystack }
        }.sortedWith(compareBy({ ReaderCanon.index(it.reference) }, { it.reference.verses?.first ?: 1 }))
    }
    private fun normalized(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
}
