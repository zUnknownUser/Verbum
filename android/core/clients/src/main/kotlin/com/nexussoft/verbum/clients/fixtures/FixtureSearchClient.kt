package com.nexussoft.verbum.clients.fixtures

import com.nexussoft.verbum.clients.SearchClient
import com.nexussoft.verbum.clients.api.LocalSearch
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.SearchResponse

/**
 * Deterministic in-memory search: a parsed reference wins outright (§28); otherwise book
 * matches, then fixture entities whose name — or any word of it — starts with the query.
 * Never throws. The preview/test double of `LiveSearchClient`.
 */
class FixtureSearchClient(private val language: () -> BookLanguage = { BookLanguage.current }) : SearchClient {
    override suspend fun search(query: String): SearchResponse {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return SearchResponse.empty(query)
        val local = LocalSearch.of(trimmed, language())
        val key = trimmed.lowercase()
        val entities = EntityFixtureData.entities.filter { entity ->
            if (entity.type == BibleEntityType.PASSAGE) return@filter false
            val name = entity.name.lowercase()
            name.startsWith(key) || name.split(" ").any { it.startsWith(key) }
        }
        return SearchResponse(query, local.passages, local.books, entities)
    }
}
