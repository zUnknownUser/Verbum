package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.GraphClient
import com.nexussoft.verbum.common.arch.*
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

object EntityListFeature {
    data class State(
        val type: BibleEntityType, val entities: List<BibleEntity> = emptyList(), val isLoading: Boolean = false,
        val query: String = "", val letter: String = "", val letters: List<String> = emptyList(),
        val nextOffset: Int? = null, val hasLoaded: Boolean = false, val failed: Boolean = false,
        val generation: Int = 0, val loadingOffset: Int = 0, val scrollIndex: Int = 0, val scrollOffset: Int = 0,
    )
    sealed interface Action {
        data object Started : Action
        data object Stopped : Action
        data object Retry : Action
        data object LoadMore : Action
        data class QueryChanged(val query: String) : Action
        data class LetterChanged(val letter: String) : Action
        data class ScrollChanged(val index: Int, val offset: Int) : Action
        data class PageLoaded(val generation: Int, val offset: Int, val page: EntityCatalogPage) : Action
        data class Failed(val generation: Int) : Action
        data class EntitiesLoaded(val entities: List<BibleEntity>) : Action
        data class EntityTapped(val entity: BibleEntity) : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }
    sealed interface DelegateAction { data class OpenEntity(val entity: BibleEntity) : DelegateAction }
    private const val LOAD = "entity-catalog"

    fun reducer(graphClient: GraphClient): Reducer<State, Action> {
        fun load(state: State, offset: Int = 0, debounce: Boolean = false): Reduced<State, Action> {
            val next = state.copy(isLoading = true, failed = false, loadingOffset = offset, generation = state.generation + 1)
            val request = EntityCatalogRequest(state.type, state.query, state.letter, offset)
            return next.with(runEffect(id = LOAD, cancelInFlight = true) { send ->
                try {
                    if (debounce) delay(300)
                    val response = if (state.type == BibleEntityType.PERSON || state.type == BibleEntityType.PLACE) Action.PageLoaded(next.generation, offset, graphClient.entityPage(request))
                        else Action.EntitiesLoaded(graphClient.entities(state.type))
                    currentCoroutineContext().ensureActive()
                    send(response)
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { send(Action.Failed(next.generation)) }
            })
        }
        fun reset(state: State, debounce: Boolean = false) = load(state.copy(entities = emptyList(), nextOffset = null, hasLoaded = false, scrollIndex = 0, scrollOffset = 0), debounce = debounce)
        return Reducer { state, action ->
            when (action) {
                Action.Started -> if (state.hasLoaded || state.isLoading) state.only() else load(state)
                Action.Stopped -> state.copy(isLoading = false, generation = state.generation + 1).with(runEffect(id = LOAD, cancelInFlight = true) {})
                Action.Retry -> if (state.isLoading) state.only() else load(state, state.loadingOffset)
                Action.LoadMore -> if (state.isLoading || state.nextOffset == null) state.only() else load(state, state.nextOffset)
                is Action.QueryChanged -> if (action.query == state.query) state.only() else reset(state.copy(query = action.query.take(100), letter = ""), debounce = true)
                is Action.LetterChanged -> if (action.letter == state.letter) state.only() else reset(state.copy(letter = action.letter))
                is Action.ScrollChanged -> state.copy(scrollIndex = action.index, scrollOffset = action.offset).only()
                is Action.PageLoaded -> if (action.generation != state.generation) state.only() else state.copy(
                    entities = ((if (action.offset == 0) emptyList() else state.entities) + action.page.entities).distinctBy { it.id },
                    isLoading = false, hasLoaded = true, failed = false, letters = action.page.letters,
                    nextOffset = action.page.nextOffset?.takeIf { it > action.offset },
                ).only()
                is Action.Failed -> if (action.generation != state.generation) state.only() else state.copy(isLoading = false, failed = true).only()
                is Action.EntitiesLoaded -> state.copy(entities = action.entities, isLoading = false, hasLoaded = true).only()
                is Action.EntityTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenEntity(action.entity))))
                is Action.Delegate -> state.only()
            }
        }
    }
}
