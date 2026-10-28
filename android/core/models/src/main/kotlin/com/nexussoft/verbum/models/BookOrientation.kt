package com.nexussoft.verbum.models

/** Guidance for the whole book, not commentary on the selected chapter. */
data class BookOrientation(
    val genre: String, val context: String, val purpose: String,
    val outline: List<BookSection>, val readings: List<PassageReference>,
    val sourceReferenceIds: List<String>,
)
data class BookSection(val title: String, val chapterStart: Int, val chapterEnd: Int)
