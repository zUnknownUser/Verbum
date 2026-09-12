package com.nexussoft.verbum.models

/** docs/PRODUCT.md §22.3. A contiguous run of verses in one chapter of one translation. */
data class BiblePassage(
    val id: String,
    val translationId: String,
    val bookId: String,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int,
    val text: String,
) {
    /** The location of this passage, independent of translation. */
    val reference: PassageReference
        get() = PassageReference(bookId, chapter, verseStart..verseEnd)
}
