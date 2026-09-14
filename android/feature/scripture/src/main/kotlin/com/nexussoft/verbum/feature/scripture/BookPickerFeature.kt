package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.common.arch.Effect
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.Testament
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.common.BookMatcher
import com.nexussoft.verbum.common.PassageReferenceParser

/** Book, then chapter. Emits a delegate action; the parent decides. Twin of iOS `BookPickerFeature`. */
object BookPickerFeature {
    data class State(
        val current: PassageReference,
        val selectedBook: BibleBook? = null,
        val searchVisible: Boolean = false,
        val query: String = "",
        val matchingBooks: List<BibleBook> = emptyList(),
        val matchingReference: PassageReference? = null,
    ) {
        val oldTestament: List<BibleBook> get() = BibleBook.canon.filter { it.testament == Testament.OLD }
        val newTestament: List<BibleBook> get() = BibleBook.canon.filter { it.testament == Testament.NEW }
    }

    sealed interface Action {
        data object ToggleSearch : Action
        data object SearchSubmitted : Action
        data class QueryChanged(val query: String) : Action
        data class BookTapped(val book: BibleBook) : Action
        data object BackToBooksTapped : Action
        data class ChapterTapped(val chapter: Int) : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }

    sealed interface DelegateAction {
        data class ChapterSelected(val reference: PassageReference) : DelegateAction
    }

    val reducer: Reducer<State, Action> = Reducer { state, action ->
        when (action) {
            Action.ToggleSearch -> state.copy(searchVisible = !state.searchVisible, query = "", matchingBooks = emptyList(), matchingReference = null).only()
            is Action.QueryChanged -> {
                val query = action.query.take(200)
                val reference = PassageReferenceParser.parse(query).referenceOrNull
                state.copy(query = query, matchingReference = reference, matchingBooks = if (reference == null) BookMatcher.books(query, BookLanguage.current) else emptyList()).only()
            }
            Action.SearchSubmitted -> {
                val reference = state.matchingReference
                if (reference != null) state.with(Effect.Send(Action.Delegate(DelegateAction.ChapterSelected(reference))))
                else if (state.matchingBooks.size == 1) state.with(Effect.Send(Action.BookTapped(state.matchingBooks.first())))
                else state.only()
            }
            is Action.BookTapped -> state.copy(selectedBook = action.book, searchVisible = false, query = "", matchingBooks = emptyList(), matchingReference = null).only()
            Action.BackToBooksTapped -> state.copy(selectedBook = null).only()
            is Action.ChapterTapped -> {
                val book = state.selectedBook
                if (book == null || action.chapter !in 1..book.chapterCount) state.only()
                else state.with(Effect.Send(Action.Delegate(DelegateAction.ChapterSelected(PassageReference(book.id, action.chapter)))))
            }
            is Action.Delegate -> state.only()
        }
    }
}
