package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.*
import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ProfileFeatureTest {
    @Test fun collectionCanContainTheSameVerseInEachCategory() = runTest {
        val preferences = InMemoryPreferencesClient()
        val annotation = ReaderAnnotation(PassageReference("John", 1, 14..14), HighlightColor.GOLD, "Minha nota", bookmarked = true)
        PreferenceReaderAnnotationsClient(preferences).save(annotation)
        val store = TestStore(ProfileFeature.State(), ProfileFeature.reducer(preferences))
        store.send(ProfileFeature.Action.Started) { it.copy(loading = true) }
        store.receive(ProfileFeature.Action.Loaded(ReadingActivity(), listOf(annotation), ReaderSettingsFeature.State(ReaderTextScale.STANDARD), null)) {
            it.copy(loading = false, annotations = listOf(annotation))
        }
        store.receive(ProfileFeature.Action.UsageLoaded(null))
        val state = ProfileFeature.State(annotations = listOf(annotation))
        assertEquals(listOf(annotation), state.bookmarks)
        assertEquals(listOf(annotation), state.highlights)
        assertEquals(listOf(annotation), state.notes)
        store.finish()
    }
    @Test fun corruptDataIsAnErrorNotAnEmptyCollection() = runTest {
        val preferences = InMemoryPreferencesClient()
        preferences.setString("readerAnnotations", "broken-json")
        val store = TestStore(ProfileFeature.State(), ProfileFeature.reducer(preferences))
        store.send(ProfileFeature.Action.Started) { it.copy(loading = true) }
        store.receive(ProfileFeature.Action.Failed) { it.copy(loading = false, failed = true) }
        store.receive(ProfileFeature.Action.UsageLoaded(null))
        store.finish()
        assertEquals("broken-json", preferences.string("readerAnnotations"))
    }
    @Test fun appearanceSurvivesReopeningTheApp() = runTest {
        val preferences = InMemoryPreferencesClient()
        val store = TestStore(ProfileFeature.State(), ProfileFeature.reducer(preferences))
        store.send(ProfileFeature.Action.AppearanceChanged(ProfileFeature.Appearance.DARK)) { it.copy(appearance = ProfileFeature.Appearance.DARK) }
        store.finish()
        assertEquals(ProfileFeature.Appearance.DARK, ProfileFeature.initial(preferences).appearance)
    }
}
