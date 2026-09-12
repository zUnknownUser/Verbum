package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.ContextClient
import com.nexussoft.verbum.common.arch.*
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.CancellationException

object ContextFeature {
    data class State(val reference: PassageReference, val content: Content = Content.Idle)
    sealed interface Content {
        data object Idle : Content
        data object Loading : Content
        data object Unavailable : Content
        data object Failed : Content
        data class Loaded(val page: PassageContext) : Content
    }
    sealed interface Action {
        data object Started : Action
        data object RetryTapped : Action
        data class Response(val page: PassageContext?) : Action
        data object Failed : Action
        data class PassageTapped(val reference: PassageReference) : Action
        data class EntityTapped(val entity: BibleEntity) : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }
    sealed interface DelegateAction {
        data class OpenPassage(val reference: PassageReference) : DelegateAction
        data class OpenEntity(val entity: BibleEntity) : DelegateAction
    }

    fun reducer(client: ContextClient): Reducer<State, Action> = Reducer { state, action ->
        when (action) {
            Action.Started, Action.RetryTapped -> {
                if (action == Action.Started && state.content != Content.Idle) state.only()
                else state.copy(content = Content.Loading).with(runEffect { send ->
                    try {
                        send(Action.Response(client.chapter(state.reference)))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        send(Action.Failed)
                    }
                })
            }
            is Action.Response -> state.copy(content = action.page?.let { Content.Loaded(it) } ?: Content.Unavailable).only()
            Action.Failed -> state.copy(content = Content.Failed).only()
            is Action.PassageTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenPassage(action.reference))))
            is Action.EntityTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenEntity(action.entity))))
            is Action.Delegate -> state.only()
        }
    }
}
