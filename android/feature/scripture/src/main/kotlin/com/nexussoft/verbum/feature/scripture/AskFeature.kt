package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.AskScriptureClient
import com.nexussoft.verbum.clients.AskScriptureException
import com.nexussoft.verbum.clients.GraphClient
import com.nexussoft.verbum.common.arch.Effect
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.runEffect
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.ScriptureAnswer
import kotlinx.coroutines.CancellationException

/**
 * One question, one answer (§13, §21.3). The page is a destination pushed from Search; it asks
 * once when it appears, renders the §30 contract from its structured fields, and sends every
 * passage to the reader and every entity to its page. When the server has nothing it can stand
 * behind, the page says so and offers the search results instead (§51). Twin of iOS `AskFeature`.
 */
object AskFeature {
    data class State(val question: String, val content: Content = Content.Idle) {
        constructor(question: String) : this(question.trim(), Content.Idle)
    }

    sealed interface Content {
        data object Idle : Content
        data object Asking : Content
        data class Answered(val page: Page) : Content
        data class Failed(val error: AskScriptureException) : Content
    }

    /**
     * The answer plus the entities it points at, resolved to names for the "Explore further"
     * links (§13.2). Resolution is best-effort: an id the graph does not know is simply not shown.
     */
    data class Page(val answer: ScriptureAnswer, val entities: List<BibleEntity> = emptyList())

    sealed interface Action {
        data object Started : Action
        data object RetryTapped : Action
        data class Response(val answer: ScriptureAnswer) : Action
        data class Failed(val error: AskScriptureException) : Action
        data class EntitiesResolved(val entities: List<BibleEntity>) : Action
        data class PassageTapped(val reference: PassageReference) : Action
        data class EntityTapped(val entity: BibleEntity) : Action
        data object SearchInsteadTapped : Action
        data object TalkTapped : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }

    sealed interface DelegateAction {
        data class OpenPassage(val reference: PassageReference) : DelegateAction
        data class OpenEntity(val entity: BibleEntity) : DelegateAction
        /** §21.3: "failure gracefully falls back to search results". */
        data class SearchInstead(val question: String) : DelegateAction
        /** Go on from this answer out loud. */
        data class Talk(val question: String, val answer: ScriptureAnswer) : DelegateAction
    }

    private object AskId
    private object EntitiesId

    fun reducer(askClient: AskScriptureClient, graphClient: GraphClient): Reducer<State, Action> = Reducer { state, action ->
        fun ask(): Pair<State, Effect<Action>> = state.copy(content = Content.Asking) to runEffect(id = AskId, cancelInFlight = true) { send ->
            try {
                send(Action.Response(askClient.ask(state.question)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AskScriptureException) {
                send(Action.Failed(e))
            } catch (e: Exception) {
                send(Action.Failed(AskScriptureException.Failed))
            }
        }
        when (action) {
            Action.Started -> if (state.content != Content.Idle) state.only() else ask().let { (s, e) -> s.with(e) }
            Action.RetryTapped -> ask().let { (s, e) -> s.with(e) }

            is Action.Response -> {
                val ids = action.answer.entityReferences.take(8)
                val next = state.copy(content = Content.Answered(Page(action.answer)))
                if (ids.isEmpty()) next.only()
                else next.with(runEffect(id = EntitiesId, cancelInFlight = true) { send ->
                    val entities = ids.mapNotNull { id ->
                        try { graphClient.entity(id) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    }
                    send(Action.EntitiesResolved(entities))
                })
            }

            is Action.Failed -> state.copy(content = Content.Failed(action.error)).only()

            is Action.EntitiesResolved -> {
                val answered = state.content as? Content.Answered ?: return@Reducer state.only()
                state.copy(content = Content.Answered(answered.page.copy(entities = action.entities))).only()
            }

            is Action.PassageTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenPassage(action.reference))))
            is Action.EntityTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenEntity(action.entity))))
            Action.SearchInsteadTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.SearchInstead(state.question))))
            Action.TalkTapped -> (state.content as? Content.Answered)?.page?.answer?.takeIf { !it.isEmpty }
                ?.let { state.with(Effect.Send(Action.Delegate(DelegateAction.Talk(state.question, it)))) } ?: state.only()
            is Action.Delegate -> state.only()
        }
    }

    private val questionWords = setOf(
        "why", "what", "how", "who", "where", "when", "which", "does", "did", "is", "are", "can", "should",
        "por", "porque", "o", "que", "como", "quem", "onde", "quando", "qual", "quais",
    )

    /**
     * Whether a query reads as a question for Ask rather than a lookup for Search (§6: both are
     * reachable from the same field): a question mark, a question word, or several words that
     * are not a reference or a name. Same rule as iOS.
     */
    fun looksLikeQuestion(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.length < 8) return false
        if (trimmed.endsWith("?")) return true
        val words = trimmed.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val first = words.firstOrNull() ?: return false
        return first in questionWords || words.size >= 4
    }
}
