package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.feature.scripture.BookPickerFeature.Action
import com.nexussoft.verbum.feature.scripture.BookPickerFeature.DelegateAction
import com.nexussoft.verbum.feature.scripture.BookPickerFeature.State
import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BookPickerFeatureTest {
    @Test fun liveReferenceSearchAndClose() = runTest {
        val store = TestStore(State(PassageReference("Gen", 1)), BookPickerFeature.reducer)
        val reference = PassageReference("John", 3, 16..18)
        store.send(Action.ToggleSearch) { it.copy(searchVisible = true) }
        store.send(Action.QueryChanged("John 3:16-18")) { it.copy(query = "John 3:16-18", matchingReference = reference) }
        store.send(Action.SearchSubmitted)
        store.receive(Action.Delegate(DelegateAction.ChapterSelected(reference)))
        store.send(Action.ToggleSearch) { it.copy(searchVisible = false, query = "", matchingReference = null) }
        store.send(Action.QueryChanged("John 999")) { it.copy(query = "John 999") }
        store.send(Action.SearchSubmitted)
        store.finish()
    }
    private val start = State(PassageReference("John", 3))

    @Test
    fun canonIsSplitByTestament() {
        assertEquals(39, start.oldTestament.size)
        assertEquals(27, start.newTestament.size)
    }

    @Test
    fun bookThenChapterEmitsDelegate() = runTest {
        val store = TestStore(start, BookPickerFeature.reducer)
        val samuel = assertNotNull(BibleBook.book("1Sam"))
        store.send(Action.BookTapped(samuel)) { it.copy(selectedBook = samuel) }
        store.send(Action.ChapterTapped(17))
        store.receive(Action.Delegate(DelegateAction.ChapterSelected(PassageReference("1Sam", 17))))
        store.finish()
    }

    @Test
    fun backReturnsToBooks() = runTest {
        val store = TestStore(start, BookPickerFeature.reducer)
        val samuel = assertNotNull(BibleBook.book("1Sam"))
        store.send(Action.BookTapped(samuel)) { it.copy(selectedBook = samuel) }
        store.send(Action.BackToBooksTapped) { it.copy(selectedBook = null) }
        store.finish()
    }

    @Test
    fun chapterOutOfBookRangeIsIgnored() = runTest {
        val store = TestStore(start, BookPickerFeature.reducer)
        val jude = assertNotNull(BibleBook.book("Jude"))
        store.send(Action.BookTapped(jude)) { it.copy(selectedBook = jude) }
        store.send(Action.ChapterTapped(2))
        store.send(Action.ChapterTapped(0))
        store.finish()
    }

    @Test
    fun chapterWithoutBookIsIgnored() = runTest {
        val store = TestStore(start, BookPickerFeature.reducer)
        store.send(Action.ChapterTapped(1))
        store.finish()
    }
}
