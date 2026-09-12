package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.common.arch.Effect
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.Testament

/** Book, then chapter. Emits a delegate action; the parent decides. Twin of iOS `BookPickerFeature`. */
object BookPickerFeature {
    data class State(
        val current: PassageReference,
        val selectedBook: BibleBook? = null,
    ) {
        val oldTestament: List<BibleBook> get() = BibleBook.canon.filter { it.testament == Testament.OLD }
        val newTestament: List<BibleBook> get() = BibleBook.canon.filter { it.testament == Testament.NEW }
    }

    sealed interface Action {
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
            is Action.BookTapped -> state.copy(selectedBook = action.book).only()
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
