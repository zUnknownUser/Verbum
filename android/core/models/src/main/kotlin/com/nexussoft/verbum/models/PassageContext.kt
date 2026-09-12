package com.nexussoft.verbum.models

/** Chapter-level context; no coverage is a null client response, never invented content. */
data class PassageContext(
    val reference: PassageReference,
    val entities: List<BibleEntity>,
    val relatedPassages: List<PassageReference>,
    val sources: List<SourceReference>,
    val isFixture: Boolean,
)
