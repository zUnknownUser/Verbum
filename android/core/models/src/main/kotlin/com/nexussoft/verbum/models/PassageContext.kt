package com.nexussoft.verbum.models

/** Chapter-level context; no coverage is a null client response, never invented content. */
data class PassageContext(
    val reference: PassageReference,
    val entities: List<BibleEntity>,
    val relatedPassages: List<PassageReference>,
    val sources: List<SourceReference>,
    val isFixture: Boolean,
    val study: ChapterStudy? = null,
    val bookOrientation: BookOrientation? = null,
)

data class StudyNote(val text: String, val references: List<PassageReference>)
data class StudyOutline(val title: String, val reference: PassageReference)
data class StudyConnection(val reference: PassageReference, val explanation: String)
data class ChapterStudy(
    val title: String,
    val summary: StudyNote,
    val participants: StudyNote,
    val setting: StudyNote,
    val literaryContext: StudyNote,
    val outline: List<StudyOutline>,
    val connections: List<StudyConnection>,
    val sourceReferenceIds: List<String>,
)
