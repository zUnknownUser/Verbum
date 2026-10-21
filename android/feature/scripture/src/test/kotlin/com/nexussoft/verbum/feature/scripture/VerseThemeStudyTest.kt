package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.AskScriptureClient
import com.nexussoft.verbum.clients.BibleComparisonClient
import com.nexussoft.verbum.clients.ContextClient
import com.nexussoft.verbum.clients.InMemoryPreferencesClient
import com.nexussoft.verbum.clients.PreferenceReaderAnnotationsClient
import com.nexussoft.verbum.common.arch.Store
import com.nexussoft.verbum.feature.scripture.VerseStudyFeature.Action
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.EntityDetail
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.ReaderAnnotation
import com.nexussoft.verbum.models.StudyNote
import com.nexussoft.verbum.models.ThemeStudy
import com.nexussoft.verbum.models.ThemeStudyReading
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VerseThemeStudyTest {
    private val passage = verses("Exod", 14, 1).first()
    private val reference = PassageReference("Exod", 14, 10..31)
    private val extra = PassageReference("Heb", 11, 1..3)
    private val theme = BibleEntity("fixture.theme.faith", BibleEntityType.THEME, "Faith", "Trust in God.")
    private val study = ThemeStudy(StudyNote("Overview", listOf(reference)),
        listOf(ThemeStudyReading("sea", "At the sea", reference, "Context", "Observation", "Meaning", "Question?")),
        emptyList(), emptyList(), emptyList())
    private val detail = EntityDetail(theme, keyPassages = listOf(reference, extra), themeStudy = study)
    private val initial = VerseStudyFeature.State.initial(passage, ReaderAnnotation(reference, note = "Keep my note"), null, listOf(theme))
        .copy(selectedEntity = theme, entity = detail, entityPassageLimit = 12)
    private fun reducer(graph: StubGraphClient = StubGraphClient(detailStub = { EntityFixtures.page.detail })) =
        VerseStudyFeature.reducer(ContextClient { null }, graph, AskScriptureClient { error("unexpected ask") },
            PreferenceReaderAnnotationsClient(InMemoryPreferencesClient()), BibleComparisonClient { error("unexpected compare") },
            StubBibleClient(passageStub = { passage }))

    @Test
    fun inlineGuideKeepsOnlyAdditionalLegacyReadings() {
        assertEquals(listOf(extra), initial.keyPassages)
    }

    @Test
    fun relatedEntityReturnsToLoadedGuideWithoutLosingNoteOrReadingLimit() = runTest {
        val store = Store(initial, reducer(), this)
        store.send(Action.ConnectedEntityTapped(EntityFixtures.david)); runCurrent()
        assertEquals(detail, store.state.value.entityHistory.single().detail)
        assertEquals(EntityFixtures.david, store.state.value.selectedEntity)
        val request = assertNotNull(store.state.value.entityRequest)
        store.send(Action.CloseEntity); runCurrent()
        val returned = store.state.value
        assertEquals(detail, returned.entity)
        assertEquals(theme, returned.selectedEntity)
        assertEquals(12, returned.entityPassageLimit)
        assertEquals(initial.annotation, returned.annotation)
        assertEquals(initial.candidates, returned.candidates)
        assertTrue(returned.entityHistory.isEmpty())
        store.send(Action.EntityLoaded(request, EntityFixtures.page.detail))
        assertEquals(returned, store.state.value)
    }

    @Test
    fun backFromLoadingConnectionCancelsAndRestoresParent() = runTest {
        val wait = CompletableDeferred<Unit>()
        var cancelled = false
        val store = Store(initial, reducer(StubGraphClient(detailStub = {
            try { wait.await(); EntityFixtures.page.detail } finally { cancelled = true }
        })), this)
        store.send(Action.ConnectedEntityTapped(EntityFixtures.david)); runCurrent()
        assertTrue(store.state.value.entityLoading)
        store.send(Action.CloseEntity); runCurrent()
        assertTrue(cancelled)
        assertEquals(detail, store.state.value.entity)
        assertFalse(store.state.value.entityLoading)
        assertNull(store.state.value.entityRequest)
    }

    @Test
    fun previewFromInlineGuideReturnsToSameEntityAndNote() = runTest {
        val store = Store(initial, reducer(), this)
        store.send(Action.PreviewPassage(reference)); runCurrent()
        assertEquals(detail, store.state.value.entity)
        store.send(Action.ClosePreview); runCurrent()
        assertEquals(initial, store.state.value)
    }
}
