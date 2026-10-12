package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.GraphClient
import com.nexussoft.verbum.clients.fixtures.FixtureGraphClient
import com.nexussoft.verbum.common.arch.Store
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EntityListFeatureTest {
    private val abel = BibleEntity("a", BibleEntityType.PERSON, "Abel", null)
    private val anna = BibleEntity("b", BibleEntityType.PERSON, "Anna", null)
    private fun client(body: suspend (EntityCatalogRequest) -> EntityCatalogPage) = object : GraphClient by FixtureGraphClient {
        override suspend fun entityPage(request: EntityCatalogRequest) = body(request)
    }

    @Test fun themeCategoriesResetSearchAndUseServerPagination() = runTest {
        val requests = mutableListOf<EntityCatalogRequest>()
        val client = client { request -> requests += request; EntityCatalogPage(emptyList(), emptyList()) }
        val store = Store(EntityListFeature.State(BibleEntityType.THEME, query = "old", letter = "A", hasLoaded = true, nextOffset = 30), EntityListFeature.reducer(client), backgroundScope)
        store.send(EntityListFeature.Action.CategoryChanged("emotions")); runCurrent()
        assertEquals(EntityCatalogRequest(BibleEntityType.THEME, category = "emotions"), requests.single())
        assertTrue(store.state.value.browsingCatalog)
        assertNull(store.state.value.nextOffset)
        store.send(EntityListFeature.Action.DiscoverCategories); runCurrent()
        assertEquals("", requests.last().category)
        assertFalse(store.state.value.browsingCatalog)
    }

    @Test fun eventPeriodSurvivesPagingSearchAndReturnAndRejectsStaleResults() = runTest {
        val requests = mutableListOf<EntityCatalogRequest>()
        val client = client { request -> requests += request; EntityCatalogPage(emptyList(), emptyList(), if (request.offset == 0) 30 else null) }
        val store = Store(EntityListFeature.State(BibleEntityType.EVENT, query = "old", letter = "A"), EntityListFeature.reducer(client), backgroundScope)
        store.send(EntityListFeature.Action.CategoryChanged("exodus")); runCurrent()
        assertEquals(EntityCatalogRequest(BibleEntityType.EVENT, category = "exodus", order = "narrative"), requests.last())
        store.send(EntityListFeature.Action.LoadMore); runCurrent()
        assertEquals(EntityCatalogRequest(BibleEntityType.EVENT, offset = 30, category = "exodus", order = "narrative"), requests.last())
        store.send(EntityListFeature.Action.QueryChanged("moises")); advanceUntilIdle(); runCurrent()
        advanceTimeBy(301); runCurrent()
        assertEquals("exodus", requests.last().category)
        store.send(EntityListFeature.Action.Stopped); runCurrent()
        store.send(EntityListFeature.Action.Started); runCurrent()
        assertEquals("exodus", store.state.value.category)
        store.send(EntityListFeature.Action.CategoryChanged("monarchy")); runCurrent()
        store.send(EntityListFeature.Action.PageLoaded(1, 0, EntityCatalogPage(listOf(abel), listOf("A"))))
        assertTrue(store.state.value.entities.isEmpty())
        assertEquals("monarchy", requests.last().category)
        store.send(EntityListFeature.Action.DiscoverCategories); runCurrent()
        assertFalse(store.state.value.browsingCatalog)
        store.send(EntityListFeature.Action.CategoryChanged("")); runCurrent()
        assertTrue(store.state.value.browsingCatalog)
        assertEquals("", requests.last().category)
    }

    @Test fun sequenceOrderSwitchClearsLetterAndKeepsSearch() = runTest {
        val requests = mutableListOf<EntityCatalogRequest>()
        val client = client { request -> requests += request; EntityCatalogPage(emptyList(), emptyList()) }
        val store = Store(EntityListFeature.State(BibleEntityType.EVENT, category = "exodus", order = "alphabetical", query = "moises", letter = "M", nextOffset = 30), EntityListFeature.reducer(client), backgroundScope)
        store.send(EntityListFeature.Action.OrderChanged("narrative")); runCurrent()
        assertEquals(EntityCatalogRequest(BibleEntityType.EVENT, query = "moises", category = "exodus", order = "narrative"), requests.single())
        assertEquals("", store.state.value.letter)
        assertNull(store.state.value.nextOffset)
        store.send(EntityListFeature.Action.PageLoaded(0, 0, EntityCatalogPage(listOf(abel), listOf("A"))))
        assertTrue(store.state.value.entities.isEmpty())
    }

    @Test fun eventsUseBoundedSearchInsteadOfTheLegacyList() = runTest {
        val requests = mutableListOf<EntityCatalogRequest>()
        val client = client { request -> requests += request; EntityCatalogPage(emptyList(), emptyList()) }
        val store = Store(EntityListFeature.State(BibleEntityType.EVENT, query = "exodo"), EntityListFeature.reducer(client), backgroundScope)
        store.send(EntityListFeature.Action.Started); runCurrent()
        assertEquals(EntityCatalogRequest(BibleEntityType.EVENT, query = "exodo", limit = 30), requests.single())
    }

    @Test fun placesUseServerPaginationAndKeepTheirStateOnReturn() = runTest {
        val jerusalem = BibleEntity("place.jerusalem", BibleEntityType.PLACE, "Jerusalém", "Cidade")
        var calls = 0
        val client = client { request ->
            calls++
            assertEquals(EntityCatalogRequest(BibleEntityType.PLACE, "jerusalem", "J"), request)
            EntityCatalogPage(listOf(jerusalem), listOf("J"), 30)
        }
        val store = Store(EntityListFeature.State(BibleEntityType.PLACE, query = "jerusalem", letter = "J"), EntityListFeature.reducer(client), backgroundScope)
        store.send(EntityListFeature.Action.Started); runCurrent()
        assertEquals(listOf(jerusalem), store.state.value.entities)
        assertEquals(30, store.state.value.nextOffset)
        store.send(EntityListFeature.Action.Stopped); runCurrent()
        store.send(EntityListFeature.Action.Started); runCurrent()
        assertEquals(1, calls)
        assertEquals("jerusalem", store.state.value.query)
        assertEquals("J", store.state.value.letter)
    }

    @Test fun pagesDeduplicateAndReturnKeepsFilterAndPosition() = runTest {
        val requests = mutableListOf<EntityCatalogRequest>()
        val client = client { request ->
            requests += request
            if (request.offset == 0) EntityCatalogPage(listOf(abel), listOf("A"), 30)
            else EntityCatalogPage(listOf(abel, anna), listOf("A"))
        }
        val store = Store(EntityListFeature.State(BibleEntityType.PERSON), EntityListFeature.reducer(client), backgroundScope)
        store.send(EntityListFeature.Action.Started); runCurrent()
        assertEquals(listOf(abel), store.state.value.entities)
        store.send(EntityListFeature.Action.LoadMore); runCurrent()
        assertEquals(listOf(abel, anna), store.state.value.entities)
        assertNull(store.state.value.nextOffset)
        store.send(EntityListFeature.Action.ScrollChanged(1, 12))
        store.send(EntityListFeature.Action.Stopped); runCurrent()
        store.send(EntityListFeature.Action.Started); runCurrent()
        assertEquals(2, requests.size)
        assertEquals(listOf(0, 30), requests.map { it.offset })
        assertEquals(1, store.state.value.scrollIndex)
        assertEquals(12, store.state.value.scrollOffset)
    }

    @Test fun fastSearchAndLetterChangesCancelStaleWork() = runTest {
        val requests = mutableListOf<EntityCatalogRequest>()
        val client = client { request -> requests += request; EntityCatalogPage(listOf(abel), listOf("A")) }
        val store = Store(EntityListFeature.State(BibleEntityType.PERSON), EntityListFeature.reducer(client), backgroundScope)
        store.send(EntityListFeature.Action.QueryChanged("A")); runCurrent()
        advanceTimeBy(100)
        store.send(EntityListFeature.Action.QueryChanged("Ab")); runCurrent()
        store.send(EntityListFeature.Action.LetterChanged("A")); runCurrent()
        advanceTimeBy(400); runCurrent()
        assertEquals(1, requests.size)
        assertEquals("Ab", requests.single().query)
        assertEquals("A", requests.single().letter)
        store.send(EntityListFeature.Action.PageLoaded(1, 0, EntityCatalogPage(emptyList(), emptyList())))
        store.send(EntityListFeature.Action.Failed(2))
        assertEquals(listOf(abel), store.state.value.entities)
        assertFalse(store.state.value.failed)
    }

    @Test fun failedNextPageKeepsRowsAndRetriesSameOffset() = runTest {
        var fail = true
        val client = client { request ->
            assertEquals(30, request.offset)
            if (fail) throw java.io.IOException("offline")
            EntityCatalogPage(listOf(anna), listOf("A"))
        }
        val store = Store(EntityListFeature.State(BibleEntityType.PERSON, entities = listOf(abel), hasLoaded = true, nextOffset = 30), EntityListFeature.reducer(client), backgroundScope)
        store.send(EntityListFeature.Action.LoadMore); runCurrent()
        assertTrue(store.state.value.failed)
        assertEquals(listOf(abel), store.state.value.entities)
        fail = false
        store.send(EntityListFeature.Action.Retry); runCurrent()
        assertEquals(listOf(abel, anna), store.state.value.entities)
        assertFalse(store.state.value.failed)
    }

    @Test fun leavingDuringDebounceCanRestartOnReturn() = runTest {
        var requests = 0
        val client = client { requests++; EntityCatalogPage(emptyList(), emptyList()) }
        val store = Store(EntityListFeature.State(BibleEntityType.PERSON), EntityListFeature.reducer(client), backgroundScope)
        store.send(EntityListFeature.Action.QueryChanged("unknown")); runCurrent()
        store.send(EntityListFeature.Action.Stopped); runCurrent()
        advanceTimeBy(500); runCurrent()
        assertEquals(0, requests)
        store.send(EntityListFeature.Action.Started); runCurrent()
        assertEquals(1, requests)
        assertTrue(store.state.value.hasLoaded)
        assertFalse(store.state.value.isLoading)
    }
}
