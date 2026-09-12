package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.EntityDetail
import com.nexussoft.verbum.models.EntityId
import com.nexussoft.verbum.models.GraphSnapshot

/** Entities and their neighbourhoods (docs/PRODUCT.md §38, §44). `detail` adds the page-level facts of §9. */
interface GraphClient {
    suspend fun entity(id: EntityId): BibleEntity
    suspend fun neighbors(id: EntityId, limit: Int): GraphSnapshot
    suspend fun detail(id: EntityId): EntityDetail
    /** Every entity of one kind, for the Explore lists (§7). Passage nodes are never listed. */
    suspend fun entities(type: BibleEntityType): List<BibleEntity>
}

sealed class GraphClientException : Exception() {
    data class UnknownEntity(val id: EntityId) : GraphClientException()

    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int
}
