package com.nexussoft.verbum.clients.api

import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.BibleRelationship
import com.nexussoft.verbum.models.EntityDetail
import com.nexussoft.verbum.models.GraphSnapshot
import com.nexussoft.verbum.models.PassageContext
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.RelationshipType
import com.nexussoft.verbum.models.ScriptureAnswer
import com.nexussoft.verbum.models.SourceReference
import com.nexussoft.verbum.models.TimelineDatePrecision
import com.nexussoft.verbum.models.TimelineEvent
import kotlinx.serialization.Serializable

// The wire shapes of api/openapi.yaml, one class per schema, each mapping to its model.
// A value outside the contract's enums is a malformed response, never a guess.

private fun malformed(what: String): Nothing = throw IllegalArgumentException("not in the contract: $what")

@Serializable
internal data class WireProblem(val code: String, val message: String = "", val retryAt: String? = null)

@Serializable
internal data class WirePassageReference(val bookId: String, val chapter: Int, val verseStart: Int? = null, val verseEnd: Int? = null) {
    fun toModel(): PassageReference {
        val verses = when {
            verseStart == null && verseEnd == null -> null
            verseStart != null && verseEnd != null -> if (verseStart >= 1 && verseEnd >= verseStart) verseStart..verseEnd else malformed("verse range")
            verseStart != null -> if (verseStart >= 1) verseStart..verseStart else malformed("verseStart")
            else -> malformed("verseEnd without verseStart")
        }
        return PassageReference(bookId, chapter, verses)
    }

    companion object {
        fun of(reference: PassageReference) = WirePassageReference(reference.bookId, reference.chapter, reference.verses?.first, reference.verses?.last)
    }
}

@Serializable
internal data class WireEntity(val id: String, val type: String, val name: String, val summary: String? = null) {
    fun toModel() = BibleEntity(id, BibleEntityType.fromWireValue(type) ?: malformed("entity type $type"), name, summary)
}

@Serializable
internal data class WireRelationship(
    val id: String,
    val sourceId: String,
    val targetId: String,
    val type: String,
    val confidence: Double? = null,
    val sourceReferenceIds: List<String>,
) {
    fun toModel() = BibleRelationship(id, sourceId, targetId, RelationshipType.fromWireValue(type) ?: malformed("relationship type $type"), confidence, sourceReferenceIds)
}

@Serializable
internal data class WireSourceReference(val id: String, val citation: String, val url: String? = null) {
    fun toModel() = SourceReference(id, citation, url)
}

@Serializable
internal data class WireEntityDetail(
    val entity: WireEntity,
    val aliases: List<String> = emptyList(),
    val approximateDates: String? = null,
    val role: String? = null,
    val modernGeography: String? = null,
    val keyPassages: List<WirePassageReference> = emptyList(),
    val sources: List<WireSourceReference> = emptyList(),
    val originalTerm: WireOriginalTerm? = null,
) {
    fun toModel() = EntityDetail(entity.toModel(), aliases, approximateDates, role, modernGeography, keyPassages.map { it.toModel() }, sources.map { it.toModel() }, originalTerm?.toModel())
}

@Serializable
internal data class WireOriginalTerm(val language: String, val transliteration: String, val strong: String) {
 fun toModel() = com.nexussoft.verbum.models.OriginalTermPresentation(language, transliteration, strong)
}

@Serializable
internal data class WireEntities(val entities: List<WireEntity>)

@Serializable
internal data class WireGraphSnapshot(val root: WireEntity, val nodes: List<WireEntity>, val edges: List<WireRelationship>) {
    fun toModel() = GraphSnapshot(root.toModel(), nodes.map { it.toModel() }, edges.map { it.toModel() })
}

@Serializable
internal data class WirePassageContext(
    val reference: WirePassageReference,
    val entities: List<WireEntity>,
    val relatedPassages: List<WirePassageReference>,
    val sources: List<WireSourceReference>,
) {
    fun toModel() = PassageContext(reference.toModel(), entities.map { it.toModel() }, relatedPassages.map { it.toModel() }, sources.map { it.toModel() }, isFixture = false)
}

@Serializable
internal data class WireTimelineEvent(
    val id: String,
    val title: String,
    val startYear: Int? = null,
    val endYear: Int? = null,
    val datePrecision: String,
    val summary: String? = null,
    val entityIds: List<String>,
    val sourceReferenceIds: List<String> = emptyList(),
) {
    fun toModel() = TimelineEvent(id, title, startYear, endYear, TimelineDatePrecision.fromWireValue(datePrecision) ?: malformed("date precision $datePrecision"), summary, entityIds, sourceReferenceIds)
}

@Serializable
internal data class WireTimeline(val events: List<WireTimelineEvent>, val entityNames: Map<String, String> = emptyMap())

@Serializable
internal data class WireBookHit(val id: String)

@Serializable
internal data class WireSearchResponse(val query: String, val passages: List<WirePassageReference>, val books: List<WireBookHit>, val entities: List<WireEntity>)

@Serializable
internal data class WireDailyVerse(val date: String, val reference: WirePassageReference)

@Serializable
internal data class WireDailyVerses(val verses: List<WireDailyVerse>)

@Serializable
internal data class WireAskRequest(val question: String, val reference: WirePassageReference? = null)

@Serializable
internal data class WireAskResponse(
    val answer: String,
    val summary: String,
    val passageReferences: List<WirePassageReference>,
    val entityReferences: List<String>,
    val sourceReferences: List<WireSourceReference>,
    val confidence: String,
    val interpretiveVariance: Boolean,
    val fallback: WireAvailability? = null,
) {
    fun toModel() = ScriptureAnswer(
        answer, summary, passageReferences.map { it.toModel() }, entityReferences, sourceReferences.map { it.toModel() },
        ScriptureAnswer.Confidence.fromWireValue(confidence) ?: malformed("confidence $confidence"), interpretiveVariance,
        fallback?.let { com.nexussoft.verbum.models.UsageRestriction(it.code, it.retryAt) },
    )
}

@Serializable
internal data class WireRealtimeSession(val clientSecret: String, val expiresAt: Long, val model: String, val relayPath: String? = null, val maxDurationSeconds: Int? = null)

@Serializable
internal data class WireSpeechRequest(val text: String, val language: String, val revision: String? = null)

@kotlinx.serialization.Serializable
internal data class WireSpeechConfiguration(val version: String)

@Serializable internal data class WireUsageStatus(val plan:String, val resetsAt:String, val remaining:Map<String,Int>, val voiceSeconds:Int, val restricted:Boolean)

@Serializable internal data class WireAvailability(val code: String, val retryAt: String? = null)
