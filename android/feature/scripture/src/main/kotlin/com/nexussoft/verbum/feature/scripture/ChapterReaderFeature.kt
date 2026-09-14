package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.BibleClient
import com.nexussoft.verbum.clients.ClipboardClient
import com.nexussoft.verbum.clients.PreferencesClient
import com.nexussoft.verbum.common.arch.Effect
import com.nexussoft.verbum.common.arch.Reduced
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.runEffect
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.BiblePassage
import com.nexussoft.verbum.models.PassageReference

/** One chapter on screen. Twin of the iOS `ChapterReaderFeature`. */
object ChapterReaderFeature {
    data class State(
        val reference: PassageReference,
        val content: Content = Content.Idle,
        val selectedVerses: Set<Int> = emptySet(),
        val textScale: ReaderTextScale = ReaderTextScale.STANDARD,
        val requestedVerses: IntRange? = reference.verses,
    ) {
        constructor(reference: PassageReference, textScale: ReaderTextScale) :
            this(PassageReference(reference.bookId, reference.chapter), Content.Idle, emptySet(), textScale, reference.verses)

        val book: BibleBook? get() = BibleBook.book(reference.bookId)
        val title: String get() = reference.formatted
        val canGoToNextChapter: Boolean get() = ChapterNavigation.next(reference) != null
        val canGoToPreviousChapter: Boolean get() = ChapterNavigation.previous(reference) != null

        val selectionCitation: String?
            get() = SelectionFormatter.format(reference.bookId, reference.chapter, selectedVerses)

        val selectedText: String?
            get() {
                val verses = (content as? Content.Loaded)?.verses ?: return null
                if (selectedVerses.isEmpty()) return null
                return verses.filter { it.verseStart in selectedVerses }.joinToString(" ") { it.text }
            }
    }

    sealed interface Content {
        data object Idle : Content
        data object Loading : Content
        data class Loaded(val verses: List<BiblePassage>) : Content
        data class Failed(val error: ReaderError) : Content
    }

    sealed interface Action {
        data object Started : Action
        data object RetryTapped : Action
        data class ChapterLoaded(val verses: List<BiblePassage>) : Action
        data class ChapterFailed(val error: ReaderError) : Action
        data class VerseTapped(val verse: Int) : Action
        data object ClearSelectionTapped : Action
        data object CopySelectionTapped : Action
        data object NextChapterTapped : Action
        data object PreviousChapterTapped : Action
        /** Parent-driven jump (book picker). Reloads. */
        data class Go(val reference: PassageReference) : Action
        /** Parent-driven; text scale lives with the settings feature. */
        data class TextScaleChanged(val scale: ReaderTextScale) : Action
        data object ListenTapped : Action
        data object TalkTapped : Action
        data object ContextTapped : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }

    sealed interface DelegateAction {
        data class Listen(val reference: PassageReference) : DelegateAction
        /** Start a spoken conversation about this chapter. */
        data class Talk(val reference: PassageReference) : DelegateAction
        data class OpenContext(val reference: PassageReference) : DelegateAction
    }

    private object LoadId

    /** Key under which the last-read reference is persisted (`bookId chapter`), read by Home. */
    const val LAST_READ_KEY = "lastRead"

    fun reducer(bibleClient: BibleClient, clipboard: ClipboardClient, preferences: PreferencesClient): Reducer<State, Action> = Reducer { state, action ->
        when (action) {
            Action.Started -> if (state.content is Content.Loaded) state.only() else load(state, bibleClient)
            Action.RetryTapped -> load(state, bibleClient)

            is Action.ChapterLoaded -> state.copy(content = Content.Loaded(action.verses),
                selectedVerses = state.requestedVerses?.let { range -> action.verses.map { it.verseStart }.filter { it in range }.toSet() } ?: state.selectedVerses).with(
                runEffect { preferences.setString(LAST_READ_KEY, LastRead.encode(state.reference)) },
            )
            is Action.ChapterFailed -> state.copy(content = Content.Failed(action.error)).only()

            is Action.VerseTapped -> state.copy(
                selectedVerses = if (action.verse in state.selectedVerses) state.selectedVerses - action.verse else state.selectedVerses + action.verse,
            ).only()

            Action.ClearSelectionTapped -> state.copy(selectedVerses = emptySet()).only()

            Action.CopySelectionTapped -> {
                val citation = state.selectionCitation
                val text = state.selectedText
                if (citation == null || text == null) state.only()
                else state.with(runEffect { clipboard.copy("$text\n— $citation") })
            }

            Action.NextChapterTapped -> ChapterNavigation.next(state.reference)?.let { jump(state, it, bibleClient) } ?: state.only()
            Action.PreviousChapterTapped -> ChapterNavigation.previous(state.reference)?.let { jump(state, it, bibleClient) } ?: state.only()
            is Action.Go -> jump(state, action.reference, bibleClient)
            is Action.TextScaleChanged -> state.copy(textScale = action.scale).only()
            Action.ListenTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.Listen(state.reference))))
            Action.TalkTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.Talk(state.reference))))
            Action.ContextTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenContext(state.reference))))
            is Action.Delegate -> state.only()
        }
    }

    private fun jump(state: State, reference: PassageReference, bibleClient: BibleClient): Reduced<State, Action> =
        load(state.copy(reference = PassageReference(reference.bookId, reference.chapter), requestedVerses = reference.verses, selectedVerses = emptySet()), bibleClient)

    private fun load(state: State, bibleClient: BibleClient): Reduced<State, Action> {
        val reference = state.reference
        return state.copy(content = Content.Loading).with(
            runEffect(id = LoadId, cancelInFlight = true) { send ->
                val result = runCatching { bibleClient.chapter(reference.bookId, reference.chapter) }
                result.fold(
                    onSuccess = { send(Action.ChapterLoaded(it)) },
                    onFailure = { if (it is kotlinx.coroutines.CancellationException) throw it else send(Action.ChapterFailed(ReaderError.from(it))) },
                )
            },
        )
    }
}
