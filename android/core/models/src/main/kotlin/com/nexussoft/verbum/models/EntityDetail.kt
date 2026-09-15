package com.nexussoft.verbum.models

/** Provenance for a contextual claim (docs/PRODUCT.md §33). */
data class SourceReference(val id: String, val citation: String, val url: String?)

/** One entity's neighbourhood (§44): the root, the nodes one hop away, and the edges between them. */
data class GraphSnapshot(val root: BibleEntity, val nodes: List<BibleEntity>, val edges: List<BibleRelationship>) {
    fun nodes(type: BibleEntityType): List<BibleEntity> = nodes.filter { it.type == type }
}

/** What an entity page shows beyond the graph (§9). Absent is better than invented. */
data class EntityDetail(
    val entity: BibleEntity,
    val aliases: List<String> = emptyList(),
    /** Always hedged: "c. 1010–970 BC (commonly dated)". */
    val approximateDates: String? = null,
    val role: String? = null,
    val modernGeography: String? = null,
    val keyPassages: List<PassageReference> = emptyList(),
    val sources: List<SourceReference> = emptyList(),
    val originalTerm: OriginalTermPresentation? = null,
)

data class OriginalTermPresentation(val language: String, val transliteration: String, val strong: String)
