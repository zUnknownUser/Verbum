package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.GuidedExplorationClient
import com.nexussoft.verbum.common.arch.*
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object GuidedExplorationFeature {
    data class State(
        val feeling: ArrivalFeeling? = null,
        val plan: ExplorationPlan? = null,
        val isLoading: Boolean = false,
        val failed: Boolean = false,
    )
    sealed interface Action {
        data class Select(val feeling: ArrivalFeeling) : Action
        data class Response(val feeling: ArrivalFeeling, val plan: ExplorationPlan?) : Action
        data object ChangeFeeling : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }
    sealed interface DelegateAction {
        data class OpenPassage(val reference: PassageReference) : DelegateAction
        data class OpenContext(val reference: PassageReference) : DelegateAction
    }
    private object LoadId
    fun reducer(client: GuidedExplorationClient): Reducer<State, Action> = Reducer { state, action ->
        when (action) {
            is Action.Select -> State(feeling = action.feeling, isLoading = true).with(runEffect(id = LoadId, cancelInFlight = true) { send ->
                val plan = try { client.explore(ExplorationRequest(action.feeling, BookLanguage.current)) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
                currentCoroutineContext().ensureActive()
                send(Action.Response(action.feeling, plan))
            })
            is Action.Response -> if (state.feeling != action.feeling) state.only()
                else state.copy(plan = action.plan, isLoading = false, failed = action.plan == null).only()
            Action.ChangeFeeling -> State().with(runEffect(id = LoadId, cancelInFlight = true) {})
            is Action.Delegate -> state.only()
        }
    }
}
