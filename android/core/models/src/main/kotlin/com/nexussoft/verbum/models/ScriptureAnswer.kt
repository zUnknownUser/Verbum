package com.nexussoft.verbum.models

/**
 * What Ask Scripture returns (docs/PRODUCT.md §30, the AI response data contract, verbatim; the
 * wire shape is `AskResponse` in api/openapi.yaml). References are structured fields, never
 * parsed out of prose (§30): the client renders and navigates from [passageReferences], and the
 * server guarantees each one was retrieved and verified before the model could cite it (§31).
 * Twin of iOS `ScriptureAnswer`.
 */
data class ScriptureAnswer(
    /** Empty when no reliable, citable answer was found (§51: trust over always answering). */
    val answer: String,
    val summary: String,
    /** Only passages the server itself retrieved and the model actually cited. */
    val passageReferences: List<PassageReference>,
    /** Ids of graph entities whose key passages cover a cited passage; may be empty. */
    val entityReferences: List<String>,
    val sourceReferences: List<SourceReference>,
    val confidence: Confidence,
    /** True when traditions or scholars meaningfully disagree on the question (§31). */
    val interpretiveVariance: Boolean,
    val fallback: UsageRestriction? = null,
) {
    /** How sure the server is that the answer is grounded in what it retrieved (§31). */
    enum class Confidence(val wireValue: String) {
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high");

        companion object {
            fun fromWireValue(value: String): Confidence? = entries.firstOrNull { it.wireValue == value }
        }
    }

    /** The server found nothing it could stand behind: show the §51 fallback, and the closest passages if it named any. */
    val isEmpty: Boolean get() = answer.isBlank()
}
