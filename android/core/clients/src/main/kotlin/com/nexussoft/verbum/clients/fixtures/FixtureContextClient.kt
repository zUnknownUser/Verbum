package com.nexussoft.verbum.clients.fixtures

import com.nexussoft.verbum.clients.ContextClient
import com.nexussoft.verbum.models.*

/** Same explicit fixture associations as iOS; never recursively expands the graph. */
object FixtureContextClient : ContextClient {
    override suspend fun chapter(reference: PassageReference): PassageContext? {
        val chapter = PassageReference(reference.bookId, reference.chapter)
        val id = EntityFixtureData.passageNode(chapter).id
        val edges = EntityFixtureData.relationships.filter { it.sourceId == id || it.targetId == id }
        val details = EntityFixtureData.details.values.filter { detail ->
            detail.keyPassages.any { it.bookId == chapter.bookId && it.chapter == chapter.chapter }
        }
        val ids = (edges.map { if (it.sourceId == id) it.targetId else it.sourceId } + details.map { it.entity.id }).toSet()
        if (ids.isEmpty()) return null
        val entities = EntityFixtureData.entities.filter { it.id in ids && it.type != BibleEntityType.PASSAGE }
        val sources = (edges.flatMap { it.sourceReferenceIds } + details.flatMap { it.sources.map { source -> source.id } }).toMutableSet()
        val related = linkedSetOf<PassageReference>()
        for (edge in EntityFixtureData.relationships) {
            val other = when {
                edge.sourceId in ids -> edge.targetId
                edge.targetId in ids -> edge.sourceId
                else -> continue
            }
            val node = EntityFixtureData.entities.firstOrNull { it.id == other } ?: continue
            val passage = EntityFixtureData.passageReference(node) ?: continue
            if (passage == chapter) continue
            related += passage
            sources += edge.sourceReferenceIds
        }
        return PassageContext(chapter, entities, related.toList(), EntityFixtureData.sources.filter { it.id in sources }, true)
    }
}
