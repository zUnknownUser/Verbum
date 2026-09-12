package com.nexussoft.verbum.clients

import com.nexussoft.verbum.clients.fixtures.EntityFixtureData
import com.nexussoft.verbum.clients.fixtures.FixtureGraphClient
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FixtureGraphClientTest {
    private val client: GraphClient = FixtureGraphClient

    @Test
    fun entityLookup() = runTest {
        assertEquals("David", client.entity("fixture.person.david").name)
        assertFailsWith<GraphClientException.UnknownEntity> { client.entity("nope") }
    }

    @Test
    fun davidNeighbourhoodIsTheGoldenPath() = runTest {
        val s = client.neighbors("fixture.person.david", 12)
        assertEquals("David", s.root.name)
        assertEquals(12, s.nodes.size)
        assertEquals(listOf("Goliath", "Saul", "Samuel", "Bathsheba", "Solomon"), s.nodes(BibleEntityType.PERSON).map { it.name })
        assertEquals(listOf("1 Samuel 16", "1 Samuel 17", "2 Samuel 5", "Psalms 23", "Psalms 51"), s.nodes(BibleEntityType.PASSAGE).map { it.name })
        assertTrue(s.edges.all { it.sourceReferenceIds.isNotEmpty() })
    }

    @Test
    fun limitCapsNodesAndEdgesFollow() = runTest {
        val s = client.neighbors("fixture.person.david", 3)
        assertEquals(3, s.nodes.size)
        val ids = s.nodes.map { it.id }.toSet()
        assertTrue(s.edges.all { it.sourceId in ids || it.targetId in ids })
    }

    @Test
    fun neighbourhoodIsUndirected() = runTest {
        val s = client.neighbors("fixture.person.goliath", 12)
        assertTrue(s.nodes.any { it.name == "David" })
        assertTrue(s.nodes.any { it.name == "1 Samuel 17" })
    }

    @Test
    fun detailsAreHedgedAndSourced() = runTest {
        val david = client.detail("fixture.person.david")
        assertEquals("Second king of Israel", david.role)
        assertTrue(david.approximateDates!!.contains("commonly dated"))
        assertEquals(PassageReference("1Sam", 16), david.keyPassages.first())
        assertTrue(david.sources.isNotEmpty())
        val mary = client.detail("fixture.person.mary")
        assertEquals(listOf(EntityFixtureData.editorialSource), mary.sources)
    }

    @Test
    fun entitiesByKindAreAlphabeticalAndNeverPassages() = runTest {
        val people = client.entities(BibleEntityType.PERSON)
        assertEquals(13, people.size)
        assertEquals(people.map { it.name }.sorted(), people.map { it.name })
        assertTrue(client.entities(BibleEntityType.PASSAGE).isEmpty())
    }

    @Test
    fun everyRelationshipPointsAtKnownEntitiesAndSources() {
        val ids = EntityFixtureData.entities.map { it.id }.toSet()
        val sourceIds = EntityFixtureData.sources.map { it.id }.toSet()
        for (e in EntityFixtureData.relationships) {
            assertTrue(e.sourceId in ids, e.id)
            assertTrue(e.targetId in ids, e.id)
            assertTrue(e.sourceReferenceIds.all { it in sourceIds }, e.id)
        }
        assertEquals(EntityFixtureData.relationships.size, EntityFixtureData.relationships.map { it.id }.toSet().size)
    }
}
