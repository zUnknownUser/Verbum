package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.ScriptureAnswer
import com.nexussoft.verbum.models.SourceReference

/**
 * Ask Scripture (docs/PRODUCT.md §38 `AskScriptureClient`, §13): a question in the user's words,
 * answered by the server's retrieval-first pipeline (§29). The client never generates anything
 * itself and never caches a question (§47). Throws [AskScriptureException].
 */
fun interface AskScriptureClient {
    suspend fun ask(question: String): ScriptureAnswer
    suspend fun askAbout(question: String, reference: PassageReference): ScriptureAnswer = ask("${reference.formatted}: $question")
}

/** Why a question could not be answered, in the states the page shows (§52). */
sealed class AskScriptureException : Exception() {
    data class Limited(val restriction: com.nexussoft.verbum.models.UsageRestriction) : AskScriptureException()
    /** The server has no synthesis configured (`503 ask_unavailable`) — or the feature is not offered by this backend at all. */
    data object Unavailable : AskScriptureException() {
        private fun readResolve(): Any = Unavailable
    }
    /** Could not reach the server. Ask needs a connection (§39: no local model). */
    data object NetworkUnavailable : AskScriptureException() {
        private fun readResolve(): Any = NetworkUnavailable
    }
    /** The server tried and could not answer (`502`), or answered outside the contract. */
    data object Failed : AskScriptureException() {
        private fun readResolve(): Any = Failed
    }

    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int
}

/** A canned, sourced answer so previews render every section. */
object PreviewAskScriptureClient : AskScriptureClient {
    val answer = ScriptureAnswer(
        answer = "David refused Saul's armour and met Goliath with a sling and his trust in the LORD, striking him on the forehead with a stone; the narrative frames the victory as the LORD's, not the weapon's.",
        summary = "David killed Goliath with a sling and a stone, crediting the LORD.",
        passageReferences = listOf(PassageReference("1Sam", 17, 45..47), PassageReference("1Sam", 17, 49..50)),
        entityReferences = listOf("fixture.person.david", "fixture.person.goliath"),
        sourceReferences = listOf(SourceReference("fixture.source.web", "World English Bible (public domain) — the passages cited", "https://worldenglish.bible")),
        confidence = ScriptureAnswer.Confidence.HIGH,
        interpretiveVariance = false,
    )

    override suspend fun ask(question: String): ScriptureAnswer = answer
}
