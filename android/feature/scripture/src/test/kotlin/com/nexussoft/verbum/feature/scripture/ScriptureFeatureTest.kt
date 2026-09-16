package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.InMemoryPreferencesClient
import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.feature.scripture.ScriptureFeature.Action
import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScriptureFeatureTest {
    private val preferences = InMemoryPreferencesClient()

    private fun store(bible: StubBibleClient = StubBibleClient(), reference: PassageReference = PassageReference("John", 3)) =
        TestStore(ScriptureFeature.State.initial(reference), ScriptureFeature.reducer(bible, preferences))

    @Test
    fun startsOnTheReaderWithTheShelfInStep() {
        val state = ScriptureFeature.State.initial(PassageReference("John", 3, 16..16))
        assertFalse(state.isShelfPresented)
        assertEquals(PassageReference("John", 3), state.reader.reference)
        assertEquals(PassageReference("John", 3), state.books.current)
        assertNull(state.settings)
    }

    @Test
    fun titleOpensTheShelfAndAChapterClosesItIntoTheReader() = runTest {
        val store = store(StubBibleClient(chapterStub = { b, c -> verses(b, c, 1) }))
        val samuel = assertNotNull(BibleBook.book("1Sam"))
        store.send(Action.TitleTapped) { it.copy(isShelfPresented = true) }
        store.send(Action.Books(BookPickerFeature.Action.BookTapped(samuel))) { it.copy(books = it.books.copy(selectedBook = samuel)) }
        store.send(Action.Books(BookPickerFeature.Action.ChapterTapped(17)))
        store.receive(Action.Books(BookPickerFeature.Action.Delegate(BookPickerFeature.DelegateAction.ChapterSelected(PassageReference("1Sam", 17))))) {
            it.copy(isShelfPresented = false)
        }
        store.receive(Action.Reader(ChapterReaderFeature.Action.Go(PassageReference("1Sam", 17)))) {
            it.copy(
                reader = it.reader.copy(reference = PassageReference("1Sam", 17), content = ChapterReaderFeature.Content.Loading),
                books = it.books.copy(current = PassageReference("1Sam", 17)),
            )
        }
        store.receive(Action.Reader(ChapterReaderFeature.Action.ChapterLoaded(verses("1Sam", 17, 1)))) {
            it.copy(reader = it.reader.copy(content = ChapterReaderFeature.Content.Loaded(verses("1Sam", 17, 1))))
        }
        assertEquals("1Sam 17", preferences.string(ChapterReaderFeature.LAST_READ_KEY))
        assertEquals(samuel, store.state.books.selectedBook)
        store.finish()
    }

    @Test
    fun swipingTheShelfAwayIsRecorded() = runTest {
        val store = store()
        store.send(Action.TitleTapped) { it.copy(isShelfPresented = true) }
        store.send(Action.ShelfDismissed) { it.copy(isShelfPresented = false) }
        store.finish()
    }

    @Test
    fun textScaleChosenInSettingsReachesReaderAndPersists() = runTest {
        val store = store()
        store.send(Action.SettingsButtonTapped) { it.copy(settings = ReaderSettingsFeature.State(ReaderTextScale.STANDARD)) }
        store.send(Action.Settings(ReaderSettingsFeature.Action.TextScaleChanged(ReaderTextScale.LARGE))) {
            it.copy(settings = ReaderSettingsFeature.State(ReaderTextScale.LARGE))
        }
        store.receive(Action.Reader(ChapterReaderFeature.Action.TextScaleChanged(ReaderTextScale.LARGE))) {
            it.copy(reader = it.reader.copy(textScale = ReaderTextScale.LARGE))
        }
        assertEquals("LARGE", preferences.string(ReaderTextScale.PREFERENCE_KEY))
        store.send(Action.SettingsDismissed) { it.copy(settings = null) }
        store.finish()
    }
}
