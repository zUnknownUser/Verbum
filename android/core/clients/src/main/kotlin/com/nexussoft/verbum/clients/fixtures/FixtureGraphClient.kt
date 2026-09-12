package com.nexussoft.verbum.clients.fixtures

import com.nexussoft.verbum.clients.GraphClient
import com.nexussoft.verbum.clients.GraphClientException
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.EntityDetail
import com.nexussoft.verbum.models.EntityId
import com.nexussoft.verbum.models.GraphSnapshot

/** In-memory [GraphClient] over [EntityFixtureData]. Replaced by the backend in Task 11. */
object FixtureGraphClient : GraphClient {
    override suspend fun entity(id: EntityId): BibleEntity =
        EntityFixtureData.entity(id) ?: throw GraphClientException.UnknownEntity(id)

    /** Undirected one-hop neighbourhood, in fixture order, capped at [limit]. */
    override suspend fun neighbors(id: EntityId, limit: Int): GraphSnapshot {
        val root = entity(id)
        val edges = EntityFixtureData.relationships.filter { it.sourceId == id || it.targetId == id }
        val seen = LinkedHashSet<EntityId>()
        val nodes = ArrayList<BibleEntity>()
        for (edge in edges) {
            val otherId = if (edge.sourceId == id) edge.targetId else edge.sourceId
            if (otherId in seen) continue
            val other = EntityFixtureData.entity(otherId) ?: continue
            seen += otherId
            nodes += other
            if (nodes.size == limit) break
        }
        return GraphSnapshot(root, nodes, edges.filter { it.sourceId in seen || it.targetId in seen })
    }

    override suspend fun entities(type: BibleEntityType): List<BibleEntity> =
        if (type == BibleEntityType.PASSAGE) emptyList() else EntityFixtureData.entities.filter { it.type == type }.sortedBy { it.name }

    override suspend fun detail(id: EntityId): EntityDetail =
        EntityFixtureData.details[id] ?: EntityDetail(entity(id), sources = listOf(EntityFixtureData.editorialSource))
}
