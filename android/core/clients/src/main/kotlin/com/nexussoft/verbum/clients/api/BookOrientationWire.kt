package com.nexussoft.verbum.clients.api

import kotlinx.serialization.Serializable
import com.nexussoft.verbum.models.*

@Serializable
internal data class WireBookOrientation(
    val genre: String, val context: String, val purpose: String,
    val outline: List<WireBookSection>, val readings: List<WirePassageReference>,
    val sourceReferenceIds: List<String>,
) {
    fun toModel() = BookOrientation(genre, context, purpose, outline.map { it.toModel() }, readings.map { it.toModel() }, sourceReferenceIds)
}
@Serializable
internal data class WireBookSection(val title: String, val chapterStart: Int, val chapterEnd: Int) {
    fun toModel() = BookSection(title, chapterStart, chapterEnd)
}
