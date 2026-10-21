package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.BibleClient
import com.nexussoft.verbum.common.arch.Store
import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.feature.scripture.EntityDetailFeature.Action
import com.nexussoft.verbum.feature.scripture.EntityDetailFeature.Content
import com.nexussoft.verbum.feature.scripture.EntityDetailFeature.DelegateAction
import com.nexussoft.verbum.feature.scripture.EntityDetailFeature.Page
import com.nexussoft.verbum.feature.scripture.EntityDetailFeature.State
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.EntityDetail
import com.nexussoft.verbum.models.GraphSnapshot
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.StudyNote
import com.nexussoft.verbum.models.SourceReference
import com.nexussoft.verbum.models.ThemeStudy
import com.nexussoft.verbum.models.ThemeStudyEntityConnection
import com.nexussoft.verbum.models.ThemeStudyReading
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeStudyFeatureTest {
    private val theme = BibleEntity("theme.faith", BibleEntityType.THEME, "Faith", "Trust in God.")
    private val target = PassageReference("Gen", 15, 1..6)
    private val extra = PassageReference("Heb", 11, 1..3)
    private val passage = verses("Gen", 15, 6).first()
    private val connection = ThemeStudyEntityConnection(EntityFixtures.david, PassageReference("1Sam", 17), "Trust amid danger.")
    private val guide = ThemeStudy(
        StudyNote("Overview", listOf(target)),
        listOf(ThemeStudyReading("promise", "Trust and promise", target, "Context", "Observation", "Meaning", "Question?")),
        emptyList(), listOf(connection), emptyList(),
    )
    private val page = Page(EntityDetail(theme, keyPassages = listOf(target, target, extra), themeStudy = guide),
        GraphSnapshot(theme, emptyList(), emptyList()))
    private val initial = State(theme.id, Content.Loaded(page))
    private fun reducer(bible: BibleClient = StubBibleClient(passageStub = { passage })) =
        EntityDetailFeature.reducer(StubGraphClient(), bible)

    @Test
    fun guideReadingsRemoveOnlyDuplicateLegacyReadings() {
        assertEquals(listOf(extra), page.additionalPassages)
        assertEquals(listOf(target, extra), page.copy(detail = page.detail.copy(themeStudy = null)).additionalPassages)
        assertEquals(listOf(PassageReference("Gen", 15)), page.copy(detail = page.detail.copy(keyPassages = emptyList()),
            neighborhood = GraphSnapshot(theme, listOf(BibleEntity("passage.Gen.15", BibleEntityType.PASSAGE, "Genesis 15", null)), emptyList()))
            .passages)
    }

    @Test
    fun explainedConnectionsAndStudySourcesAreNotRepeatedInLegacySections() {
        val guideSource = SourceReference("guide", "Scripture", "https://example.test/source")
        val legacySource = SourceReference("legacy", "Existing attribution", null)
        val enriched = page.copy(
            detail = page.detail.copy(sources = listOf(guideSource, legacySource),
                themeStudy = guide.copy(sourceReferenceIds = listOf(guideSource.id))),
            neighborhood = GraphSnapshot(theme, listOf(EntityFixtures.david, EntityFixtures.goliath), emptyList()),
        )
        assertEquals(listOf(EntityFixtures.goliath), enriched.otherRelated(BibleEntityType.PERSON))
        assertEquals(listOf(EntityFixtures.david, EntityFixtures.goliath), enriched.related(BibleEntityType.PERSON))
        assertEquals(listOf(legacySource), enriched.otherSources)
        assertEquals(listOf(guideSource, legacySource), enriched.sources)
    }

    @Test
    fun previewLoadsAndClosingRestoresIdenticalThemePage() = runTest {
        val store = Store(initial, reducer(), this)
        store.send(Action.PreviewPassage(target)); runCurrent()
        val preview = assertNotNull(store.state.value.preview)
        assertEquals(passage, preview.passage)
        assertFalse(preview.loading)
        assertEquals(initial.content, store.state.value.content)
        store.send(Action.ClosePreview); runCurrent()
        assertEquals(initial, store.state.value)
    }

    @Test
    fun previewRetriesAndRejectsStaleResultForSameReference() = runTest {
        var fail = true
        val store = Store(initial, reducer(StubBibleClient(passageStub = { if (fail) error("offline") else passage })), this)
        store.send(Action.PreviewPassage(target)); runCurrent()
        val old = assertNotNull(store.state.value.preview)
        assertTrue(old.failed)
        fail = false
        store.send(Action.RetryPreview); runCurrent()
        val current = assertNotNull(store.state.value.preview)
        assertNotEquals(old.request, current.request)
        assertEquals(passage, current.passage)
        store.send(Action.PreviewLoaded(target, old.request, passage.copy(text = "stale")))
        store.send(Action.PreviewFailed(target, old.request))
        assertEquals(current, store.state.value.preview)
    }

    @Test
    fun closingCancelsLoadAndRejectsLateResponse() = runTest {
        val wait = CompletableDeferred<Unit>()
        var cancelled = false
        val store = Store(initial, reducer(StubBibleClient(passageStub = {
            try { wait.await(); passage } finally { cancelled = true }
        })), this)
        store.send(Action.PreviewPassage(target)); runCurrent()
        val request = assertNotNull(store.state.value.preview).request
        store.send(Action.ClosePreview); runCurrent()
        assertTrue(cancelled)
        store.send(Action.PreviewLoaded(target, request, passage))
        store.send(Action.PreviewFailed(target, request))
        assertEquals(initial, store.state.value)
    }

    @Test
    fun changingReferenceCancelsPreviousLoadAndGuardsIdentity() = runTest {
        val first = CompletableDeferred<Unit>()
        var cancelled = false
        val store = Store(initial, reducer(StubBibleClient(passageStub = {
            if (it == target) try { first.await() } finally { cancelled = true }
            passage
        })), this)
        store.send(Action.PreviewPassage(target)); runCurrent()
        val request = assertNotNull(store.state.value.preview).request
        store.send(Action.PreviewPassage(extra)); runCurrent()
        assertTrue(cancelled)
        val current = assertNotNull(store.state.value.preview)
        store.send(Action.PreviewLoaded(target, request, passage))
        store.send(Action.PreviewFailed(target, request))
        assertEquals(extra, current.reference)
        assertEquals(current, store.state.value.preview)
    }

    @Test
    fun onlyExplicitReaderActionNavigatesAndClearsPreview() = runTest {
        val preview = EntityDetailFeature.Preview(target, "request", passage, loading = false)
        val store = TestStore(initial.copy(preview = preview), reducer())
        store.send(Action.OpenPreviewInReader) { it.copy(preview = null) }
        store.receive(Action.Delegate(DelegateAction.OpenPassage(target)))
        assertEquals(initial.content, store.state.content)
        assertNull(store.state.preview)
        store.finish()
    }

    @Test
    fun explainedConnectionUsesExistingEntityNavigation() = runTest {
        val store = TestStore(initial, reducer())
        store.send(Action.EntityTapped(connection.entity))
        store.receive(Action.Delegate(DelegateAction.OpenEntity(connection.entity)))
        assertEquals(initial, store.state)
        store.finish()
    }
}
