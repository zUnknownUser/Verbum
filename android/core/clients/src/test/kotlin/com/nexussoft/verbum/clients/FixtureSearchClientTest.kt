package com.nexussoft.verbum.clients

import com.nexussoft.verbum.clients.fixtures.EntityFixtureData
import com.nexussoft.verbum.clients.fixtures.FixtureSearchClient
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixtureSearchClientTest {
    private val client: SearchClient = FixtureSearchClient { BookLanguage.ENGLISH }

    @Test
    fun referenceQueryRanksThePassageFirst() = runTest {
        val r = client.search("Jn 3:16")
        assertEquals(listOf(PassageReference("John", 3, 16..16)), r.passages)
        assertTrue(r.books.isEmpty())
        assertTrue(r.entities.isEmpty())
    }

    @Test
    fun bookNameQuery() = runTest {
        val r = client.search("sam")
        assertTrue(r.passages.isEmpty())
        assertEquals(listOf("1Sam", "2Sam"), r.books.map { it.id })
        assertEquals(listOf("Samuel"), r.entities.map { it.name })
    }

    @Test
    fun entityQueryGroupsByType() = runTest {
        val r = client.search("j")
        assertEquals(listOf("Jesus", "John"), r.entities(BibleEntityType.PERSON).map { it.name })
        assertEquals(listOf("Jerusalem"), r.entities(BibleEntityType.PLACE).map { it.name })
        assertEquals(listOf("Justice"), r.entities(BibleEntityType.THEME).map { it.name })
        assertTrue(r.books.isNotEmpty())
    }

    @Test
    fun nameCanBeMatchedByAnyWord() = runTest {
        assertEquals(listOf("David"), client.search("dav").entities(BibleEntityType.PERSON).map { it.name })
        assertEquals(listOf("Anointing of David", "David and Goliath", "David takes Jerusalem"), client.search("dav").entities(BibleEntityType.EVENT).map { it.name })
        assertEquals(listOf("Forgiveness"), client.search("forgive").entities.map { it.name })
    }

    @Test
    fun emptyAndNoMatch() = runTest {
        assertTrue(client.search("").isEmpty)
        assertTrue(client.search("   ").isEmpty)
        assertTrue(client.search("why did job suffer").isEmpty)
    }

    @Test
    fun fixtureEntityIdsAreLabelled() {
        assertTrue(EntityFixtureData.entities.filter { it.type != BibleEntityType.PASSAGE }.all { it.id.startsWith("fixture.") })
        assertEquals(EntityFixtureData.entities.size, EntityFixtureData.entities.map { it.id }.toSet().size)
    }
}
