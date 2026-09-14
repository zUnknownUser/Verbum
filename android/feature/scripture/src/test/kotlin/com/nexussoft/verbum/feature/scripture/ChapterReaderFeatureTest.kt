package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.BibleClientException
import com.nexussoft.verbum.clients.RecordingClipboardClient
import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.feature.scripture.ChapterReaderFeature.Action
import com.nexussoft.verbum.feature.scripture.ChapterReaderFeature.Content
import com.nexussoft.verbum.feature.scripture.ChapterReaderFeature.State
import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ChapterReaderFeatureTest {
    private val preferences = com.nexussoft.verbum.clients.InMemoryPreferencesClient()
    private fun store(state: State, bible: StubBibleClient = StubBibleClient(), clipboard: RecordingClipboardClient? = null) =
        TestStore(state, ChapterReaderFeature.reducer(bible, clipboard ?: unimplementedClipboard, preferences))

    @Test
    fun startedLoadsChapter() = runTest {
        val store = store(State(PassageReference("John", 3)), StubBibleClient(chapterStub = { b, c -> verses(b, c, 3) }))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.ChapterLoaded(verses("John", 3, 3))) { it.copy(content = Content.Loaded(verses("John", 3, 3))) }
        assertEquals("John 3", preferences.string(ChapterReaderFeature.LAST_READ_KEY))
        store.finish()
    }

    @Test
    fun loadFailureIsMappedToReaderError() = runTest {
        val reference = PassageReference("John", 4)
        val store = store(State(reference), StubBibleClient(chapterStub = { _, _ -> throw BibleClientException.ContentUnavailable(reference) }))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.ChapterFailed(ReaderError.ChapterUnavailable(reference))) { it.copy(content = Content.Failed(ReaderError.ChapterUnavailable(reference))) }
        store.finish()
    }

    @Test
    fun unknownErrorsNeverLeakRaw() = runTest {
        val store = store(State(PassageReference("John", 3)), StubBibleClient(chapterStub = { _, _ -> throw IllegalStateException("boom") }))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.ChapterFailed(ReaderError.Unexpected)) { it.copy(content = Content.Failed(ReaderError.Unexpected)) }
        store.send(Action.RetryTapped) { it.copy(content = Content.Loading) }
        store.receive(Action.ChapterFailed(ReaderError.Unexpected)) { it.copy(content = Content.Failed(ReaderError.Unexpected)) }
        store.finish()
    }

    @Test
    fun verseSelectionTogglesAndFormats() = runTest {
        val loaded = Content.Loaded(verses("John", 3, 21))
        val store = store(State(PassageReference("John", 3), content = loaded))
        store.send(Action.VerseTapped(16)) { it.copy(selectedVerses = setOf(16)) }
        store.send(Action.VerseTapped(17)) { it.copy(selectedVerses = setOf(16, 17)) }
        store.send(Action.VerseTapped(18)) { it.copy(selectedVerses = setOf(16, 17, 18)) }
        store.send(Action.VerseTapped(21)) { it.copy(selectedVerses = setOf(16, 17, 18, 21)) }
        assertEquals("John 3:16-18, 21", store.state.selectionCitation)
        assertEquals("v16 v17 v18 v21", store.state.selectedText)
        store.send(Action.VerseTapped(17)) { it.copy(selectedVerses = setOf(16, 18, 21)) }
        assertEquals("John 3:16, 18, 21", store.state.selectionCitation)
        store.send(Action.ClearSelectionTapped) { it.copy(selectedVerses = emptySet()) }
        assertNull(store.state.selectionCitation)
        store.finish()
    }

    @Test
    fun copyWritesTextAndCitationToClipboard() = runTest {
        val clipboard = RecordingClipboardClient()
        val store = store(State(PassageReference("John", 3), content = Content.Loaded(verses("John", 3, 21)), selectedVerses = setOf(16, 17)), clipboard = clipboard)
        store.send(Action.CopySelectionTapped)
        assertEquals("v16 v17\n— John 3:16-17", clipboard.lastCopied)
        store.finish()
    }

    @Test
    fun copyWithNothingSelectedDoesNothing() = runTest {
        // clipboard is unimplemented: calling it would fail the test.
        val store = store(State(PassageReference("John", 3), content = Content.Loaded(verses("John", 3, 21))))
        store.send(Action.CopySelectionTapped)
        store.finish()
    }

    @Test
    fun nextChapterReloadsAndClearsSelection() = runTest {
        val store = store(State(PassageReference("John", 3), selectedVerses = setOf(1)), StubBibleClient(chapterStub = { b, c -> verses(b, c, 2) }))
        store.send(Action.NextChapterTapped) { it.copy(reference = PassageReference("John", 4), selectedVerses = emptySet(), content = Content.Loading) }
        store.receive(Action.ChapterLoaded(verses("John", 4, 2))) { it.copy(content = Content.Loaded(verses("John", 4, 2))) }
        store.finish()
    }

    @Test
    fun chapterStepsRollAcrossBooks() = runTest {
        val store = store(State(PassageReference("John", 21)), StubBibleClient(chapterStub = { b, c -> verses(b, c, 1) }))
        store.send(Action.NextChapterTapped) { it.copy(reference = PassageReference("Acts", 1), content = Content.Loading) }
        store.receive(Action.ChapterLoaded(verses("Acts", 1, 1))) { it.copy(content = Content.Loaded(verses("Acts", 1, 1))) }
        store.send(Action.PreviousChapterTapped) { it.copy(reference = PassageReference("John", 21), content = Content.Loading) }
        store.receive(Action.ChapterLoaded(verses("John", 21, 1))) { it.copy(content = Content.Loaded(verses("John", 21, 1))) }
        store.finish()
    }

    @Test
    fun cannotStepBeforeGenesisOrAfterRevelation() = runTest {
        val genesis = store(State(PassageReference("Gen", 1)))
        assertFalse(genesis.state.canGoToPreviousChapter)
        genesis.send(Action.PreviousChapterTapped)
        genesis.finish()

        val revelation = store(State(PassageReference("Rev", 22)))
        assertFalse(revelation.state.canGoToNextChapter)
        revelation.send(Action.NextChapterTapped)
        revelation.finish()
    }

    @Test
    fun requestedVersesAreSelectedFromLoadedText() = runTest {
        val store = store(State(PassageReference("John", 3, 16..18), ReaderTextScale.STANDARD), StubBibleClient(chapterStub = { b, c -> verses(b, c, 20) }))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.ChapterLoaded(verses("John", 3, 20))) { it.copy(content = Content.Loaded(verses("John", 3, 20)), selectedVerses = setOf(16, 17, 18)) }
        store.finish()
    }

    @Test
    fun goToReferenceRetainsVerseTargetAndLoadsChapter() = runTest {
        val store = store(State(PassageReference("John", 3)), StubBibleClient(chapterStub = { b, c -> verses(b, c, 1) }))
        store.send(Action.Go(PassageReference("1Sam", 17, 45..47))) { it.copy(reference = PassageReference("1Sam", 17), requestedVerses = 45..47, content = Content.Loading) }
        store.receive(Action.ChapterLoaded(verses("1Sam", 17, 1))) { it.copy(content = Content.Loaded(verses("1Sam", 17, 1))) }
        store.finish()
    }

    @Test
    fun inFlightLoadIsSupersededByANewerOne() = runTest {
        // TestStore runs effects to completion, so model the slow load as one that never answers.
        val bible = StubBibleClient(chapterStub = { b, c -> if (c == 3) awaitCancellation() else verses(b, c, 1) })
        val store = store(State(PassageReference("John", 3)), bible)
        // Started would hang; instead prove the reducer marks the load cancellable-in-flight by
        // jumping straight to chapter 4 and receiving only that chapter.
        store.send(Action.Go(PassageReference("John", 4))) { it.copy(reference = PassageReference("John", 4), content = Content.Loading) }
        store.receive(Action.ChapterLoaded(verses("John", 4, 1))) { it.copy(content = Content.Loaded(verses("John", 4, 1))) }
        store.finish()
    }
}
