package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.*
import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ReadingCollectionFeatureTest {
    @Test fun historyPaginatesAndSearchResetsWithoutLosingVisits() {
        val activity = ReadingActivity(visits = (45 downTo 1).map {
            ReadingActivity.Visit(PassageReference("Gen", it), it.toLong())
        } + ReadingActivity.Visit(PassageReference("John", 3), 0))
        val reducer = ReadingCollectionFeature.reducer(InMemoryPreferencesClient())
        var state = ReadingCollectionFeature.State(activity = activity)
        assertEquals(20, state.visibleVisits.size)
        state = reducer.reduce(state, ReadingCollectionFeature.Action.HistoryShowMore).state
        assertEquals(40, state.visibleVisits.size)
        state = reducer.reduce(state, ReadingCollectionFeature.Action.HistoryShowMore).state
        assertEquals(46, state.visibleVisits.size)
        assertFalse(state.hasMore)
        state = reducer.reduce(state, ReadingCollectionFeature.Action.HistoryQueryChanged("  joao 3 ")).state
        assertEquals(listOf(PassageReference("John", 3)), state.visibleVisits.map { it.reference })
        assertEquals(20, state.historyVisibleCount)
        state = reducer.reduce(state, ReadingCollectionFeature.Action.HistoryQueryChanged("Gênesis 3")).state
        assertEquals(listOf(PassageReference("Gen", 3)), state.visibleVisits.map { it.reference })
        state = reducer.reduce(state, ReadingCollectionFeature.Action.HistoryQueryChanged("xyz")).state
        assertTrue(state.visibleVisits.isEmpty())
        state = reducer.reduce(state, ReadingCollectionFeature.Action.HistoryQueryChanged("")).state
        assertEquals(20, state.visibleVisits.size)
        assertEquals(46, state.activity.visits.size)
    }

    @Test fun historyStaysBelowReaderAndPreservesSearchOnReturn() {
        val deps = AppFeature.Dependencies(bibleClient = StubBibleClient(), preferences = InMemoryPreferencesClient(), searchClient = unimplementedSearch, graphClient = StubGraphClient(), audioClient = unimplementedAudio, player = FakePlayer())
        val reducer = AppFeature.reducer(deps)
        var state = AppFeature.State(tab = AppFeature.Tab.JOURNEY, contentTab = AppFeature.Tab.JOURNEY)
        state = reducer.reduce(state, AppFeature.Action.Collection(ReadingCollectionFeature.Action.Delegate(ReadingCollectionFeature.DelegateAction.History))).state
        assertIs<AppFeature.Destination.History>(state.journeyPath.single())
        state = reducer.reduce(state, AppFeature.Action.JourneyPath(0, AppFeature.DestinationAction.History(ReadingCollectionFeature.Action.HistoryQueryChanged("João")))).state
        val reference = PassageReference("John", 3)
        state = reducer.reduce(state, AppFeature.Action.JourneyPath(0, AppFeature.DestinationAction.History(ReadingCollectionFeature.Action.Delegate(ReadingCollectionFeature.DelegateAction.Open(reference))))).state
        assertEquals(2, state.journeyPath.size)
        assertIs<AppFeature.Destination.Reader>(state.journeyPath.last())
        state = reducer.reduce(state, AppFeature.Action.Pop(AppFeature.Tab.JOURNEY)).state
        assertEquals("João", (state.journeyPath.single() as AppFeature.Destination.History).state.historyQuery)
        assertTrue(state.libraryPath.isEmpty())
    }

    @Test fun reloadsExistingAnnotationsAndKeepsSearchFilter() = runTest {
        val prefs = InMemoryPreferencesClient()
        val annotations = PreferenceReaderAnnotationsClient(prefs)
        val item = ReaderAnnotation(PassageReference("John", 3, 16..16), note = "Amor", bookmarked = true)
        annotations.save(item)
        val store = TestStore(ReadingCollectionFeature.State(query = "amor", filter = ReadingCollectionFilter.NOTES), ReadingCollectionFeature.reducer(prefs))
        store.send(ReadingCollectionFeature.Action.Started) { it.copy(loading = true) }
        store.receive(ReadingCollectionFeature.Action.Loaded(ReadingActivity(), null, listOf(item))) { it.copy(annotations = listOf(item), loading = false) }
        annotations.save(item.copy(note = "", bookmarked = false))
        store.send(ReadingCollectionFeature.Action.Started) { it.copy(loading = true) }
        store.receive(ReadingCollectionFeature.Action.Loaded(ReadingActivity(), null, emptyList())) { it.copy(annotations = emptyList(), loading = false) }
        assertEquals("amor", store.state.query)
        assertEquals(ReadingCollectionFilter.NOTES, store.state.filter)
        store.finish()
    }
    @Test fun corruptStoragePreservesExistingStateAndReportsFailure() = runTest {
        val prefs = InMemoryPreferencesClient()
        prefs.setString("readerAnnotations", "broken-json")
        val item = ReaderAnnotation(PassageReference("Job", 38, 4..4), bookmarked = true)
        val store = TestStore(ReadingCollectionFeature.State(annotations = listOf(item)), ReadingCollectionFeature.reducer(prefs))
        store.send(ReadingCollectionFeature.Action.Started) { it.copy(loading = true) }
        store.receive(ReadingCollectionFeature.Action.Failed) { it.copy(loading = false, failed = true) }
        assertEquals(listOf(item), store.state.annotations)
        assertEquals("broken-json", prefs.string("readerAnnotations"))
        store.finish()
    }
    @Test fun journeyLoadsEvenWhenAnnotationsAreCorrupt() = runTest {
        val prefs=InMemoryPreferencesClient()
        prefs.setString("readerAnnotations","broken-json")
        prefs.setString("lastRead","Job 38")
        ReadingActivityClient(prefs).record(PassageReference("Job",38))
        val activity=ReadingActivityClient(prefs).load()
        val store=TestStore(ReadingCollectionFeature.State(),ReadingCollectionFeature.reducer(prefs))
        store.send(ReadingCollectionFeature.Action.JourneyStarted) { it.copy(loading=true) }
        store.receive(ReadingCollectionFeature.Action.JourneyLoaded(activity,PassageReference("Job",38))) { it.copy(activity=activity,lastRead=PassageReference("Job",38),loading=false) }
        assertEquals("broken-json",prefs.string("readerAnnotations"))
        store.finish()
    }
    @Test fun libraryAndJourneyKeepTheirOwnNavigation() {
        val deps = AppFeature.Dependencies(bibleClient = StubBibleClient(), preferences = InMemoryPreferencesClient(), searchClient = unimplementedSearch, graphClient = StubGraphClient(), audioClient = unimplementedAudio, player = FakePlayer())
        val reducer = AppFeature.reducer(deps)
        for (tab in listOf(AppFeature.Tab.LIBRARY, AppFeature.Tab.JOURNEY)) {
            var state = reducer.reduce(AppFeature.State(), AppFeature.Action.TabChanged(tab)).state
            state = reducer.reduce(state, AppFeature.Action.Collection(ReadingCollectionFeature.Action.Delegate(ReadingCollectionFeature.DelegateAction.Open(PassageReference("John", 3, 16..16))))).state
            assertEquals(tab, state.tab)
            assertEquals(1, with(AppFeature) { state.path(tab).size })
            assertTrue(state.homePath.isEmpty())
            state = reducer.reduce(state, AppFeature.Action.Pop(tab)).state
            assertTrue(with(AppFeature) { state.path(tab).isEmpty() })
        }
    }
}
