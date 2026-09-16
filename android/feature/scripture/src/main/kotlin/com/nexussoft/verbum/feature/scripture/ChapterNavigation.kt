package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.PassageReference

/** Chapter arithmetic over the canon; steps roll into the next/previous book. Mirrors iOS. */
internal object ChapterNavigation {
    fun next(after: PassageReference): PassageReference? {
        val book = BibleBook.book(after.bookId) ?: return null
        if (after.chapter < book.chapterCount) return PassageReference(book.id, after.chapter + 1)
        val nextBook = BibleBook.canon.firstOrNull { it.order == book.order + 1 } ?: return null
        return PassageReference(nextBook.id, 1)
    }

    fun previous(before: PassageReference): PassageReference? {
        val book = BibleBook.book(before.bookId) ?: return null
        if (before.chapter > 1) return PassageReference(book.id, before.chapter - 1)
        val previousBook = BibleBook.canon.firstOrNull { it.order == book.order - 1 } ?: return null
        return PassageReference(previousBook.id, previousBook.chapterCount)
    }
}
