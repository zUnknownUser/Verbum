package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.*
import com.nexussoft.verbum.common.arch.Store
import com.nexussoft.verbum.feature.scripture.ChapterReaderFeature.Action
import com.nexussoft.verbum.feature.scripture.ChapterReaderFeature.State
import com.nexussoft.verbum.feature.scripture.ChapterReaderFeature.Content
import com.nexussoft.verbum.feature.scripture.ChapterReaderFeature.Visit
import com.nexussoft.verbum.feature.scripture.ChapterReaderFeature.Position
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChapterReaderFeatureTest {
    private val preferences=InMemoryPreferencesClient()
    private fun TestScope.reader(state:State,bible:StubBibleClient=StubBibleClient(chapterStub={b,c->verses(b,c,3)})) =
        Store(state,ChapterReaderFeature.reducer(bible,RecordingClipboardClient(),preferences),this)

    @Test fun loadsCurrentAndAdjacentChaptersOnly()=runTest {
        val calls=mutableListOf<String>()
        val store=reader(State(PassageReference("John",3)),StubBibleClient(chapterStub={b,c->calls+="$b.$c";verses(b,c,3)}))
        store.send(Action.Started);advanceUntilIdle()
        assertEquals(Content.Loaded(verses("John",3,3)),store.state.value.content)
        assertEquals(setOf("John.2","John.3","John.4"),calls.toSet())
        assertEquals(3,calls.size)
    }
    @Test fun lateChapterCannotReplaceCurrentPage()=runTest {
        val old=CompletableDeferred<Unit>()
        val store=reader(State(PassageReference("John",3)),StubBibleClient(chapterStub={b,c->if(c==3) old.await();verses(b,c,3)}))
        store.send(Action.Started);runCurrent()
        store.send(Action.Go(PassageReference("John",4)));runCurrent()
        old.complete(Unit);advanceUntilIdle()
        assertEquals(PassageReference("John",4),store.state.value.reference)
        assertEquals(Content.Loaded(verses("John",4,3)),store.state.value.content)
        assertNotNull(store.state.value.chapters["John.3"])
    }
    @Test fun verseOpensStudyWithLocalAnnotationWithoutLeavingReading()=runTest {
        val ref=PassageReference("John",3,2..2)
        val state=State(PassageReference("John",3),chapters=mapOf("John.3" to verses("John",3,3)),annotations=mapOf("John.3.2" to ReaderAnnotation(ref,HighlightColor.SAGE,"Minha nota")))
        val store=reader(state)
        store.send(Action.VerseTapped(2))
        assertEquals(ref,store.state.value.study?.reference)
        assertEquals("Minha nota",store.state.value.study?.annotation?.note)
        assertEquals(state.reference,store.state.value.reference)
        assertNull(store.state.value.study?.ask)
    }
    @Test fun returningRestoresPositionAndContinuousFlow()=runTest {
        val previous=PassageReference("Rom",8)
        val flow=listOf(previous,PassageReference("Rom",9))
        val state=State(PassageReference("Gen",50),history=listOf(Visit(previous,flow,28,73)),chapters=mapOf("Rom.8" to verses("Rom",8,3)))
        val store=reader(state);store.send(Action.BackToReading);advanceUntilIdle()
        assertEquals(previous,store.state.value.reference)
        assertEquals(Position(28,73),store.state.value.restorePosition)
        assertEquals(flow,store.state.value.flow)
        assertTrue(store.state.value.history.isEmpty())
    }
    @Test fun preferencesSurviveReopening()=runTest {
        val store=reader(State(PassageReference("John",1)))
        store.send(Action.ModeChanged(ReadingMode.CONTINUOUS));store.send(Action.FocusChanged(true));advanceUntilIdle()
        val reopened=reader(State(PassageReference("John",1)));reopened.send(Action.Started);advanceUntilIdle()
        assertEquals(ReadingMode.CONTINUOUS,reopened.state.value.readingMode)
        assertTrue(reopened.state.value.focusMode)
    }
    @Test fun canonBoundariesDoNotNavigate()=runTest {
        val first=reader(State(PassageReference("Gen",1)));first.send(Action.PreviousChapterTapped)
        assertEquals(PassageReference("Gen",1),first.state.value.reference)
        val last=reader(State(PassageReference("Rev",22)));last.send(Action.NextChapterTapped)
        assertEquals(PassageReference("Rev",22),last.state.value.reference)
    }
}
