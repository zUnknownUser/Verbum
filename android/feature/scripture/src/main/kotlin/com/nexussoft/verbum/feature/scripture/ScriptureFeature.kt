package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.BibleClient
import com.nexussoft.verbum.clients.ClipboardClient
import com.nexussoft.verbum.clients.PreferencesClient
import com.nexussoft.verbum.common.arch.Effect
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.combine
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.pullback
import com.nexussoft.verbum.common.arch.pullbackOptional
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.models.PassageReference

/**
 * The reading destination: a chapter reader with the bookshelf beside it — a sheet over the
 * page in compact width, a column to the left in expanded width. Twin of iOS `ScriptureFeature`.
 */
object ScriptureFeature {
    data class State(
        val books: BookPickerFeature.State,
        val reader: ChapterReaderFeature.State,
        val settings: ReaderSettingsFeature.State? = null,
        /** Compact width only: the shelf presented over the page. */
        val isShelfPresented: Boolean = false,
    ) {
        companion object {
            fun initial(reference: PassageReference = PassageReference("John", 3), textScale: ReaderTextScale = ReaderTextScale.STANDARD): State {
                val reader = ChapterReaderFeature.State(reference, textScale)
                return State(books = BookPickerFeature.State(current = reader.reference), reader = reader)
            }
        }
    }

    sealed interface Action {
        data class Books(val action: BookPickerFeature.Action) : Action
        data class Reader(val action: ChapterReaderFeature.Action) : Action
        data class Settings(val action: ReaderSettingsFeature.Action) : Action
        data object TitleTapped : Action
        data object ShelfDismissed : Action
        data object SettingsButtonTapped : Action
        data object SettingsDismissed : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }

    sealed interface DelegateAction {
        data class Listen(val reference: PassageReference) : DelegateAction
        data class Talk(val reference: PassageReference) : DelegateAction
        data class OpenContext(val reference: PassageReference) : DelegateAction
    }

    fun reducer(bibleClient: BibleClient, clipboard: ClipboardClient, preferences: PreferencesClient): Reducer<State, Action> = combine(
        BookPickerFeature.reducer.pullback(
            get = { it.books }, set = { s, c -> s.copy(books = c) },
            extractAction = { (it as? Action.Books)?.action }, embedAction = { Action.Books(it) },
        ),
        ChapterReaderFeature.reducer(bibleClient, clipboard, preferences).pullback(
            get = { it.reader }, set = { s, c -> s.copy(reader = c) },
            extractAction = { (it as? Action.Reader)?.action }, embedAction = { Action.Reader(it) },
        ),
        ReaderSettingsFeature.reducer(preferences).pullbackOptional(
            get = { it.settings }, set = { s, c -> s.copy(settings = c) },
            extractAction = { (it as? Action.Settings)?.action }, embedAction = { Action.Settings(it) },
        ),
        Reducer { state, action ->
            when (action) {
                Action.TitleTapped -> state.copy(isShelfPresented = true).only()
                Action.ShelfDismissed -> state.copy(isShelfPresented = false).only()
                Action.SettingsButtonTapped -> state.copy(settings = ReaderSettingsFeature.State(state.reader.textScale)).only()
                Action.SettingsDismissed -> state.copy(settings = null).only()
                is Action.Books -> when (val a = action.action) {
                    is BookPickerFeature.Action.Delegate -> when (val d = a.delegate) {
                        is BookPickerFeature.DelegateAction.ChapterSelected ->
                            state.copy(isShelfPresented = false).with(Effect.Send(Action.Reader(ChapterReaderFeature.Action.Go(d.reference))))
                    }
                    else -> state.only()
                }
                is Action.Reader -> {
                    val synced = state.copy(books = state.books.copy(current = state.reader.reference))
                    when (val delegate = (action.action as? ChapterReaderFeature.Action.Delegate)?.delegate) {
                        is ChapterReaderFeature.DelegateAction.Listen -> synced.with(Effect.Send(Action.Delegate(DelegateAction.Listen(delegate.reference))))
                        is ChapterReaderFeature.DelegateAction.Talk -> synced.with(Effect.Send(Action.Delegate(DelegateAction.Talk(delegate.reference))))
                        is ChapterReaderFeature.DelegateAction.OpenContext -> synced.with(Effect.Send(Action.Delegate(DelegateAction.OpenContext(delegate.reference))))
                        null -> synced.only()
                    }
                }
                is Action.Delegate -> state.only()
                is Action.Settings -> when (val a = action.action) {
                    is ReaderSettingsFeature.Action.TextScaleChanged ->
                        state.with(Effect.Send(Action.Reader(ChapterReaderFeature.Action.TextScaleChanged(a.scale))))
                }
            }
        },
    )
}
