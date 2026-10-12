package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.*
import com.nexussoft.verbum.common.arch.*
import com.nexussoft.verbum.feature.scripture.VerseStudyFeature.Action
import com.nexussoft.verbum.feature.scripture.VerseStudyFeature.State
import com.nexussoft.verbum.feature.scripture.VerseStudyFeature.Tab
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class VerseStudyFeatureTest {
    private val reference=PassageReference("John",3)
    private val target=PassageReference("Rom",8,1..1)
    private val passage=verses("John",3,1).first()
    private val preview=verses("Rom",8,1).first()
    private val preferences=InMemoryPreferencesClient()
    private fun initial(context:PassageContext?=null,guide:Boolean=false)=State.initial(passage,null,context,emptyList(),chapterGuide=guide)
    private fun reducer(bible:BibleClient=StubBibleClient(passageStub={preview}),graph:GraphClient=StubGraphClient())=
        VerseStudyFeature.reducer(ContextClient {null},graph,AskScriptureClient {throw AssertionError("unexpected ask")},PreferenceReaderAnnotationsClient(preferences),BibleComparisonClient {throw AssertionError("unexpected comparison")},bible)

    @Test fun contextGroupsEveryTypeDeduplicatesAndCanReachBeyondFormerCap() {
        val entities=(1..31).map {BibleEntity("person-$it",BibleEntityType.PERSON,"Person $it",null)} +
            BibleEntityType.entries.filter {it!=BibleEntityType.PERSON}.map {BibleEntity(it.wireValue,it,it.wireValue,null)}
        val state=initial(PassageContext(reference,entities+entities.first(),emptyList(),emptyList(),false),guide=true)
        assertEquals(entities.size,state.contextEntities.size)
        assertEquals(BibleEntityType.entries,state.entityTypes)
        assertEquals(6,state.visibleEntities.size)
        var expanded=state
        repeat(5) {expanded=reducer().reduce(expanded,Action.MoreEntities).state}
        assertEquals(31,expanded.visibleEntities.size)
        val changed=reducer().reduce(expanded,Action.EntityTypeChanged(BibleEntityType.HISTORICAL_PERIOD)).state
        assertEquals(6,changed.entityLimit)
        assertEquals(BibleEntityType.HISTORICAL_PERIOD,changed.visibleEntities.single().type)
    }

    @Test fun referencesAreDeduplicatedAndExpandInSixes() {
        val refs=(1..19).map {PassageReference("Ps",it)}
        var state=initial(PassageContext(reference,emptyList(),refs+refs.first(),emptyList(),false))
        assertEquals(19,state.relatedPassages.size)
        assertEquals(6,state.referenceLimit)
        state=reducer().reduce(state,Action.MoreReferences).state
        assertEquals(12,state.referenceLimit)
        repeat(3) {state=reducer().reduce(state,Action.MoreReferences).state}
        assertEquals(19,state.referenceLimit)
    }

    @Test fun previewLoadsInPlaceAndBackPreservesStudySelectionAndNote()=runTest {
        val state=initial().copy(tab=Tab.REFERENCES,selectedType=BibleEntityType.THEME,entityLimit=12,annotation=ReaderAnnotation(PassageReference("John",3,1..1),note="draft"))
        val store=Store(state,reducer(),this)
        store.send(Action.PreviewPassage(target));runCurrent()
        assertEquals(preview,store.state.value.preview)
        assertFalse(store.state.value.previewLoading)
        assertEquals("draft",store.state.value.annotation.note)
        assertNull(preferences.string("readerAnnotations"))
        store.send(Action.ClosePreview)
        assertEquals(state,store.state.value)
        advanceUntilIdle()
    }

    @Test fun previewFailureCanRetryAndIgnoresOlderSuccessForSameReference()=runTest {
        var fail=true
        val store=Store(initial(),reducer(StubBibleClient(passageStub={if(fail) throw Exception("offline") else preview})),this)
        store.send(Action.PreviewPassage(target));runCurrent()
        assertTrue(store.state.value.previewFailed)
        val oldRequest=store.state.value.previewRequest!!
        fail=false
        store.send(Action.RetryPreview);runCurrent()
        assertNotEquals(oldRequest,store.state.value.previewRequest)
        assertEquals(preview,store.state.value.preview)
        store.send(Action.PreviewLoaded(target,oldRequest,preview.copy(text="stale")))
        store.send(Action.PreviewFailed(target,oldRequest))
        assertEquals(preview,store.state.value.preview)
        assertFalse(store.state.value.previewFailed)
    }

    @Test fun closingPreviewCancelsLoadingAndRejectsLateResult()=runTest {
        val wait=CompletableDeferred<Unit>()
        var cancelled=false
        val store=Store(initial(),reducer(StubBibleClient(passageStub={try {wait.await();preview} finally {cancelled=true}})),this)
        store.send(Action.PreviewPassage(target));runCurrent()
        val request=store.state.value.previewRequest!!
        store.send(Action.ClosePreview);runCurrent()
        assertTrue(cancelled)
        store.send(Action.PreviewLoaded(target,request,preview))
        assertNull(store.state.value.previewReference)
        assertNull(store.state.value.preview)
        assertFalse(store.state.value.previewLoading)
    }

    @Test fun differentPreviewRejectsPreviousReference()=runTest {
        val store=Store(initial(),reducer(),this)
        store.send(Action.PreviewPassage(target));runCurrent()
        val request=store.state.value.previewRequest!!
        val second=PassageReference("Ps",23)
        store.send(Action.PreviewPassage(second));runCurrent()
        store.send(Action.PreviewFailed(target,request))
        assertEquals(second,store.state.value.previewReference)
        assertFalse(store.state.value.previewFailed)
    }

    @Test fun entityFailureRetriesAndClosedEntityRejectsStaleResponse()=runTest {
        val entity=BibleEntity("moses",BibleEntityType.PERSON,"Moses",null)
        var calls=0
        val graph=StubGraphClient(detailStub={calls++;throw Exception("offline")})
        val store=Store(initial(),reducer(graph=graph),this)
        store.send(Action.EntityTapped(entity));runCurrent()
        val request=store.state.value.entityRequest!!
        assertTrue(store.state.value.entityFailed)
        store.send(Action.RetryEntity);runCurrent()
        assertEquals(2,calls)
        store.send(Action.CloseEntity);runCurrent()
        store.send(Action.EntityFailed(request))
        assertFalse(store.state.value.entityFailed)
        assertNull(store.state.value.selectedEntity)
        store.send(Action.EntityLoaded(request,EntityDetail(entity)))
        assertNull(store.state.value.entity)
    }

    @Test fun entityPassagesAreDeduplicatedAndExpandWithoutDroppingAny() {
        val entity=BibleEntity("moses",BibleEntityType.PERSON,"Moses",null)
        val refs=(1..20).map {PassageReference("Exod",it)}
        var state=initial().copy(entity=EntityDetail(entity,keyPassages=refs+refs.first()))
        assertEquals(20,state.keyPassages.size)
        assertEquals(6,state.entityPassageLimit)
        repeat(3) {state=reducer().reduce(state,Action.MoreEntityPassages).state}
        assertEquals(20,state.entityPassageLimit)
    }

    @Test fun explicitOpenSavesPendingNoteBeforeDelegatingNavigation()=runTest {
        val note=ReaderAnnotation(PassageReference("John",3,1..1),note="keep this")
        val state=initial().copy(annotation=note,previewReference=target)
        val store=TestStore(state,reducer())
        store.send(Action.OpenPreviewInReader)
        store.receive(Action.PassageTapped(target)) {it.copy(passageAfterSave=target)}
        store.receive(Action.Save) {it.copy(saving=true)}
        store.receive(Action.Saved(note)) {it.copy(saving=false,saved=true,savedNote=note.note)}
        store.receive(Action.Delegate(VerseStudyFeature.DelegateAction.AnnotationSaved(note)))
        store.receive(Action.Delegate(VerseStudyFeature.DelegateAction.OpenPassage(target)))
        store.finish()
        assertEquals(note,PreferenceReaderAnnotationsClient(preferences).load().single())
    }

    @Test fun chapterGuideUsesChapterScopeAndOnlyStudyTabs() {
        val state=initial(guide=true)
        assertEquals(reference,state.reference)
        assertEquals("",state.text)
        assertEquals(Tab.CONTEXT,state.tab)
        assertEquals(listOf(Tab.CONTEXT,Tab.REFERENCES),state.availableTabs)
        assertEquals(state,reducer().reduce(state,Action.TabChanged(Tab.NOTE)).state)
        assertEquals(Effect.None,reducer().reduce(state,Action.BookmarkToggled).effect)
        assertEquals(state,reducer().reduce(state,Action.BookmarkToggled).state)
    }
}
