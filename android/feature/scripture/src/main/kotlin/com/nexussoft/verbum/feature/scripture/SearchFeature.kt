package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.SearchClient
import com.nexussoft.verbum.clients.api.LocalSearch
import com.nexussoft.verbum.common.PassageReferenceParser
import com.nexussoft.verbum.common.arch.Effect
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.runEffect
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.SearchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Type a reference, a book, a person, a place or a theme (docs/PRODUCT.md §27).
 * Debounced; a newer query cancels the search in flight. Twin of the iOS `SearchFeature`.
 */
object SearchFeature {
    data class State(
        val query: String = "",
        val phase: Phase = Phase.IDLE,
        val results: SearchResponse? = null,
        /**
         * The last search could not reach the content service; [results] is what this device
         * knows on its own (a reference, a book) — never shown as if it were the full answer (§52).
         */
        val isOffline: Boolean = false,
    ) {
        /**
         * The question to offer Ask Scripture for, when the query reads as one (§6: search and ask
         * share the field). Offered as soon as it is typed, before results, so the answer never
         * waits on the debounce.
         */
        val askSuggestion: String? get() = query.trim().takeIf { AskFeature.looksLikeQuestion(it) }

        /** `true` while the user has typed something that produced nothing. */
        val showsNoResults: Boolean
            get() = phase == Phase.IDLE && results?.isEmpty == true && query.isNotBlank()
    }

    enum class Phase { IDLE, SEARCHING }

    sealed interface Action {
        data class QueryChanged(val query: String) : Action
        data class SearchResponded(val response: SearchResponse) : Action
        /** The service could not be reached; carries the device-only results. */
        data class SearchUnreachable(val response: SearchResponse) : Action
        data object Submitted : Action
        data class PassageTapped(val reference: PassageReference) : Action
        data class BookTapped(val book: BibleBook) : Action
        data class EntityTapped(val entity: BibleEntity) : Action
        data object AskTapped : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }

    sealed interface DelegateAction {
        data class OpenPassage(val reference: PassageReference) : DelegateAction
        data class OpenEntity(val entity: BibleEntity) : DelegateAction
        /** Ask Scripture with the field's question (§13). */
        data class Ask(val question: String) : DelegateAction
    }

    /** How long typing may pause before we search. */
    const val DEBOUNCE_MS = 250L

    private object SearchId

    fun reducer(
        searchClient: SearchClient,
        debounceMs: Long = DEBOUNCE_MS,
        language: () -> BookLanguage = { BookLanguage.current },
    ): Reducer<State, Action> = Reducer { state, action ->
        when (action) {
            is Action.QueryChanged -> {
                val query = action.query.trim()
                if (query.isEmpty()) {
                    // Cancels any search in flight by superseding its id with a no-op.
                    state.copy(query = action.query, phase = Phase.IDLE, results = null, isOffline = false)
                        .with(runEffect(id = SearchId, cancelInFlight = true) {})
                } else {
                    state.copy(query = action.query, phase = Phase.SEARCHING).with(
                        runEffect(id = SearchId, cancelInFlight = true) { send ->
                            delay(debounceMs)
                            try {
                                send(Action.SearchResponded(searchClient.search(query)))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // §21.3, §52: degrade to what the device knows, and say so.
                                send(Action.SearchUnreachable(LocalSearch.of(query, language())))
                            }
                        },
                    )
                }
            }

            is Action.SearchResponded ->
                // Ignore answers to a query the user has since moved past.
                if (action.response.query != state.query.trim()) state.only()
                else state.copy(results = action.response, phase = Phase.IDLE, isOffline = false).only()

            is Action.SearchUnreachable ->
                if (action.response.query != state.query.trim()) state.only()
                else state.copy(results = action.response, phase = Phase.IDLE, isOffline = true).only()

            Action.Submitted -> {
                val reference = PassageReferenceParser.parse(state.query, language()).referenceOrNull
                val firstBook = state.results?.books?.firstOrNull()
                val question = state.askSuggestion
                when {
                    reference != null -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenPassage(reference))))
                    firstBook != null -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenPassage(PassageReference(firstBook.id, 1)))))
                    // A question submitted is a question asked.
                    question != null -> state.with(Effect.Send(Action.Delegate(DelegateAction.Ask(question))))
                    else -> state.only()
                }
            }

            Action.AskTapped -> state.askSuggestion?.let { state.with(Effect.Send(Action.Delegate(DelegateAction.Ask(it)))) } ?: state.only()

            is Action.PassageTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenPassage(action.reference))))
            is Action.BookTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenPassage(PassageReference(action.book.id, 1)))))
            is Action.EntityTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenEntity(action.entity))))
            is Action.Delegate -> state.only()
        }
    }

}
