package com.nexussoft.verbum.models

/** docs/PRODUCT.md §22.2. `wireValue` is the string contract shared with iOS and the backend. */
enum class RelationshipType(val wireValue: String) {
    APPEARS_IN("appearsIn"),
    PARTICIPATES_IN("participatesIn"),
    OCCURS_AT("occursAt"),
    OCCURS_DURING("occursDuring"),
    REFERENCES("references"),
    RELATED_TO_THEME("relatedToTheme"),
    RELATED_TO("relatedTo"),
    PRECEDES("precedes"),
    FOLLOWS("follows"),
    FULFILLS("fulfills"),
    QUOTES("quotes");

    companion object {
        fun fromWireValue(value: String): RelationshipType? = entries.firstOrNull { it.wireValue == value }
    }
}

/** docs/PRODUCT.md §22.2. Directed edge from [sourceId] to [targetId]. */
data class BibleRelationship(
    val id: String,
    val sourceId: String,
    val targetId: String,
    val type: RelationshipType,
    /** Editorial confidence when known (§58). `null` means unrated. */
    val confidence: Double?,
    /** Provenance (§33). Every relationship shown to users should carry at least one. */
    val sourceReferenceIds: List<String>,
)
