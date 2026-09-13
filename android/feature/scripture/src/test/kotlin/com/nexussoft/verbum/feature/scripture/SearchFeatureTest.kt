package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.SearchClient
import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.feature.scripture.SearchFeature.Action
import com.nexussoft.verbum.feature.scripture.SearchFeature.DelegateAction
import com.nexussoft.verbum.feature.scripture.SearchFeature.Phase
import com.nexussoft.verbum.feature.scripture.SearchFeature.State
import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.SearchResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchFeatureTest {
    private fun response(query: String, books: List<BibleBook> = emptyList(), entities: List<BibleEntity> = emptyList()) =
        SearchResponse(query, emptyList(), books, entities)

    private val david = BibleEntity("fixture.person.david", BibleEntityType.PERSON, "David", null)

    // Debounce is 0 in tests: TestStore runs effects to completion, and runTest's virtual clock skips delays anyway.
    private fun store(state: State = State(), client: SearchClient = unimplementedSearch) =
        TestStore(state, SearchFeature.reducer(client, debounceMs = 0, language = { BookLanguage.ENGLISH }))

    @Test
    fun typingSearches() = runTest {
        val store = store(client = SearchClient { response(it, entities = listOf(david)) })
        store.send(Action.QueryChanged("dav")) { it.copy(query = "dav", phase = Phase.SEARCHING) }
        store.receive(Action.SearchResponded(response("dav", entities = listOf(david)))) {
            it.copy(phase = Phase.IDLE, results = response("dav", entities = listOf(david)))
        }
        store.finish()
    }

    @Test
    fun eachQueryIsSearchedAndTheLatestWins() = runTest {
        // TestStore runs effects to completion, so cancellation cannot be observed here;
        // the live Store cancels via Effect.Run(id, cancelInFlight). What we can prove is
        // that a response for an older query is never shown over a newer one (see staleResponsesAreIgnored).
        val searched = mutableListOf<String>()
        val store = store(client = SearchClient { searched += it; response(it) })
        store.send(Action.QueryChanged("d")) { it.copy(query = "d", phase = Phase.SEARCHING) }
        store.receive(Action.SearchResponded(response("d"))) { it.copy(phase = Phase.IDLE, results = response("d")) }
        store.send(Action.QueryChanged("da")) { it.copy(query = "da", phase = Phase.SEARCHING) }
        store.receive(Action.SearchResponded(response("da"))) { it.copy(phase = Phase.IDLE, results = response("da")) }
        assertEquals(listOf("d", "da"), searched)
        store.finish()
    }

    @Test
    fun clearingTheQueryClearsResults() = runTest {
        val store = store(State(query = "dav", results = response("dav", entities = listOf(david))))
        store.send(Action.QueryChanged("")) { it.copy(query = "", phase = Phase.IDLE, results = null) }
        store.finish()
    }

    @Test
    fun staleResponsesAreIgnored() = runTest {
        val store = store(State(query = "david", phase = Phase.SEARCHING))
        store.send(Action.SearchResponded(response("dav")))
        assertNull(store.state.results)
        assertEquals(Phase.SEARCHING, store.state.phase)
        store.finish()
    }

    /** §21.3, §52: when the content service can't be reached the field still answers with what the device knows, and says so. */
    @Test
    fun clientFailureBecomesDeviceOnlyResults() = runTest {
        val store = store(client = SearchClient { throw IllegalStateException("boom") })
        store.send(Action.QueryChanged("zzz")) { it.copy(query = "zzz", phase = Phase.SEARCHING) }
        store.receive(Action.SearchUnreachable(SearchResponse.empty("zzz"))) { it.copy(phase = Phase.IDLE, results = SearchResponse.empty("zzz"), isOffline = true) }
        assertTrue(store.state.showsNoResults)
        store.finish()
    }

    @Test
    fun unreachableServiceStillParsesAReferenceAndALaterAnswerClearsTheNotice() = runTest {
        val store = store(client = SearchClient { throw IllegalStateException("boom") })
        store.send(Action.QueryChanged("Jn 3:16")) { it.copy(query = "Jn 3:16", phase = Phase.SEARCHING) }
        val local = SearchResponse("Jn 3:16", listOf(PassageReference("John", 3, 16..16)), emptyList(), emptyList())
        store.receive(Action.SearchUnreachable(local)) { it.copy(phase = Phase.IDLE, results = local, isOffline = true) }
        store.send(Action.SearchResponded(response("Jn 3:16"))) { it.copy(results = response("Jn 3:16"), isOffline = false) }
        store.send(Action.QueryChanged("")) { it.copy(query = "", results = null) }
        store.finish()
    }

    @Test
    fun returnKeyOpensAParsedReferenceImmediately() = runTest {
        val store = store(State(query = "Jn 3:16", phase = Phase.SEARCHING))
        store.send(Action.Submitted)
        store.receive(Action.Delegate(DelegateAction.OpenPassage(PassageReference("John", 3, 16..16))))
        store.finish()
    }

    @Test
    fun returnKeyOpensTheFirstBookMatch() = runTest {
        val samuel = assertNotNull(BibleBook.book("1Sam"))
        val store = store(State(query = "sam", results = response("sam", books = listOf(samuel))))
        store.send(Action.Submitted)
        store.receive(Action.Delegate(DelegateAction.OpenPassage(PassageReference("1Sam", 1))))
        store.finish()
    }

    @Test
    fun returnKeyWithNothingToOpenDoesNothing() = runTest {
        // Not a reference, not a book, and too short to be a question for Ask.
        val store = store(State(query = "Elah"))
        store.send(Action.Submitted)
        store.finish()
    }

    @Test
    fun tapsBecomeDelegates() = runTest {
        val romans = assertNotNull(BibleBook.book("Rom"))
        val store = store()
        store.send(Action.PassageTapped(PassageReference("Rom", 8, 28..28)))
        store.receive(Action.Delegate(DelegateAction.OpenPassage(PassageReference("Rom", 8, 28..28))))
        store.send(Action.BookTapped(romans))
        store.receive(Action.Delegate(DelegateAction.OpenPassage(PassageReference("Rom", 1))))
        store.send(Action.EntityTapped(david))
        store.receive(Action.Delegate(DelegateAction.OpenEntity(david)))
        store.finish()
    }

    /** §6: search and ask share the field. A question is offered to Ask at once, opened on return, and the search still runs underneath. */
    @Test
    fun aQuestionIsOfferedToAskAndSubmittedToIt() = runTest {
        val store = store(client = SearchClient { response(it) })
        store.send(Action.QueryChanged("why did Job suffer")) { it.copy(query = "why did Job suffer", phase = Phase.SEARCHING) }
        assertEquals("why did Job suffer", store.state.askSuggestion)
        store.receive(Action.SearchResponded(response("why did Job suffer"))) { it.copy(phase = Phase.IDLE, results = response("why did Job suffer")) }
        store.send(Action.Submitted)
        store.receive(Action.Delegate(DelegateAction.Ask("why did Job suffer")))
        store.send(Action.AskTapped)
        store.receive(Action.Delegate(DelegateAction.Ask("why did Job suffer")))
        store.finish()
    }

    @Test
    fun aLookupIsNotOfferedToAsk() = runTest {
        val store = store(State(query = "David"))
        assertEquals(null, store.state.askSuggestion)
        store.send(Action.AskTapped)
        store.finish()
    }
}
