package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.*
import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ReadingCollectionFeatureTest {
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
