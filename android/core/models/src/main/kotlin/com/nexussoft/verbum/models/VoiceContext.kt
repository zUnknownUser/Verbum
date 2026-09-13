package com.nexussoft.verbum.models

/**
 * What a spoken conversation is about: the page the user started it from. The companion is told
 * this and nothing else about the user (§47). Twin of iOS `VoiceContext`.
 */
sealed interface VoiceContext {
    /** A chapter open in the reader; its text is read through `BibleClient` when the conversation starts. */
    data class Chapter(val reference: PassageReference) : VoiceContext
    /** An entity page (§9): the facts and key passages shown there. */
    data class Entity(val detail: EntityDetail) : VoiceContext
    /** An Ask Scripture answer (§30), to go on from. */
    data class Answer(val question: String, val answer: ScriptureAnswer) : VoiceContext

    /** A short title for the sheet: `1 Samuel 17`, `David`, the question. */
    val title: String
        get() = when (this) {
            is Chapter -> reference.formatted
            is Entity -> detail.entity.name
            is Answer -> question
        }
}
