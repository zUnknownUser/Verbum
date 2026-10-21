package com.nexussoft.verbum.clients.api

import com.nexussoft.verbum.models.ThemeStudy
import com.nexussoft.verbum.models.ThemeStudyComparison
import com.nexussoft.verbum.models.ThemeStudyEntityConnection
import com.nexussoft.verbum.models.ThemeStudyReading
import kotlinx.serialization.Serializable

@Serializable
internal data class WireThemeStudy(
    val overview: WireStudyNote,
    val readings: List<WireThemeStudyReading>,
    val comparisons: List<WireThemeStudyComparison>,
    val connections: List<WireThemeStudyEntityConnection>,
    val sourceReferenceIds: List<String>,
) {
    fun toModel() = ThemeStudy(overview.toModel(), readings.map { it.toModel() },
        comparisons.map { it.toModel() }, connections.map { it.toModel() }, sourceReferenceIds)
}

@Serializable
internal data class WireThemeStudyReading(
    val id: String,
    val title: String,
    val reference: WirePassageReference,
    val context: String,
    val observation: String,
    val significance: String,
    val question: String,
) {
    fun toModel() = ThemeStudyReading(id, title, reference.toModel(), context, observation, significance, question)
}

@Serializable
internal data class WireThemeStudyComparison(
    val id: String,
    val title: String,
    val references: List<WirePassageReference>,
    val explanation: String,
    val difference: String,
) {
    fun toModel() = ThemeStudyComparison(id, title, references.map { it.toModel() }, explanation, difference)
}

@Serializable
internal data class WireThemeStudyEntityConnection(
    val entity: WireEntity,
    val reference: WirePassageReference,
    val explanation: String,
) {
    fun toModel() = ThemeStudyEntityConnection(entity.toModel(), reference.toModel(), explanation)
}
