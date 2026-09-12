package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.SearchResponse

/** Search across references, books and entities (docs/PRODUCT.md §27, §38). */
fun interface SearchClient {
    suspend fun search(query: String): SearchResponse
}
