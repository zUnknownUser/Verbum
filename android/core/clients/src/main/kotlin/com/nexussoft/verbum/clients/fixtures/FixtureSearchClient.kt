package com.nexussoft.verbum.clients.fixtures

import com.nexussoft.verbum.clients.SearchClient
import com.nexussoft.verbum.common.BookMatcher
import com.nexussoft.verbum.common.PassageReferenceParser
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.SearchResponse

/**
 * Deterministic in-memory search: a parsed reference wins outright (§28); otherwise book
 * matches, then fixture entities whose name — or any word of it — starts with the query.
 * Never throws. Replaced by the backend in Task 11.
 */
class FixtureSearchClient(private val language: () -> BookLanguage = { BookLanguage.current }) : SearchClient {
    override suspend fun search(query: String): SearchResponse {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return SearchResponse.empty(query)
        val reference = PassageReferenceParser.parse(trimmed, language()).referenceOrNull
        val books = if (reference == null) BookMatcher.books(trimmed) else emptyList()
        val key = trimmed.lowercase()
        val entities = EntityFixtureData.entities.filter { entity ->
            if (entity.type == BibleEntityType.PASSAGE) return@filter false
            val name = entity.name.lowercase()
            name.startsWith(key) || name.split(" ").any { it.startsWith(key) }
        }
        return SearchResponse(query, listOfNotNull(reference), books, entities)
    }
}
