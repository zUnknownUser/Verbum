package com.nexussoft.verbum.models

/** Voluntary, ephemeral intent. Never a diagnosis or persisted emotional profile. */
enum class ArrivalFeeling { ANXIOUS, LOST, GRATEFUL, TIRED, AFRAID, ALONE, ANGRY, HOPELESS, PEACEFUL }
data class ExplorationRequest(val feeling: ArrivalFeeling, val language: BookLanguage)
data class ExplorationPlan(
    val request: ExplorationRequest,
    val guidingQuestion: String,
    val passages: List<PassageReference>,
    val isEditorialPreview: Boolean,
)
