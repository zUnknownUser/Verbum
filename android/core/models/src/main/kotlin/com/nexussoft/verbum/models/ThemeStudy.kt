package com.nexussoft.verbum.models

/** An authored, source-backed study belonging to an existing theme. */
data class ThemeStudy(
    val overview: StudyNote,
    val readings: List<ThemeStudyReading>,
    val comparisons: List<ThemeStudyComparison>,
    val connections: List<ThemeStudyEntityConnection>,
    val sourceReferenceIds: List<String>,
)

data class ThemeStudyReading(
    val id: String,
    val title: String,
    val reference: PassageReference,
    val context: String,
    val observation: String,
    val significance: String,
    val question: String,
)

data class ThemeStudyComparison(
    val id: String,
    val title: String,
    val references: List<PassageReference>,
    val explanation: String,
    val difference: String,
)

data class ThemeStudyEntityConnection(
    val entity: BibleEntity,
    val reference: PassageReference,
    val explanation: String,
)
