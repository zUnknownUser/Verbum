package com.nexussoft.verbum.models

/**
 * What a search returns (docs/PRODUCT.md §27, §38). Groups are kept separate so the UI
 * can render them as `Passages · Books · People · Places · Themes` and rank direct
 * reference matches first (§28).
 */
data class SearchResponse(
    val query: String,
    /** A passage the query names directly, e.g. `Jn 3:16`. */
    val passages: List<PassageReference>,
    /** Books whose name or abbreviation the query starts. */
    val books: List<BibleBook>,
    /** People, places, themes, events. Grouped by `type` in the UI. */
    val entities: List<BibleEntity>,
) {
    val isEmpty: Boolean get() = passages.isEmpty() && books.isEmpty() && entities.isEmpty()

    fun entities(type: BibleEntityType): List<BibleEntity> = entities.filter { it.type == type }

    companion object {
        fun empty(query: String) = SearchResponse(query, emptyList(), emptyList(), emptyList())
    }
}
