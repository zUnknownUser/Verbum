package com.nexussoft.verbum.models

/**
 * Where a passage is, independent of any translation: a book, a chapter, and
 * optionally a verse range. Produced by the reference parser (docs/PRODUCT.md
 * §60 Task 3) and consumed by the `BibleClient` (§38).
 *
 * `verses == null` means the whole chapter (`"John 3"`).
 */
data class PassageReference(
    val bookId: BookId,
    val chapter: Int,
    val verses: IntRange? = null,
) {
    val isWholeChapter: Boolean get() = verses == null

    /**
     * Human-readable form in the device language: `John 3:16-18`, `1 Samuel 17` — or
     * `João 3:16-18` on a Portuguese device. Falls back to the raw book id if the book is
     * not in the canon. See [formatted] with an explicit [BookLanguage] to pin it.
     */
    val formatted: String get() = formatted(BookLanguage.current)
}
