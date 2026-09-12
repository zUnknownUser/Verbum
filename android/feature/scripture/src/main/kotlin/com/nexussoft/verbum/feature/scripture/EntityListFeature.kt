package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.GraphClient
import com.nexussoft.verbum.common.arch.Effect
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.runEffect
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType

/** Every entity of one kind — People, Places, Themes, Events (§7). Twin of iOS `EntityListFeature`. */
object EntityListFeature {
    data class State(val type: BibleEntityType, val entities: List<BibleEntity> = emptyList(), val isLoading: Boolean = false)

    sealed interface Action {
        data object Started : Action
        data class EntitiesLoaded(val entities: List<BibleEntity>) : Action
        data class EntityTapped(val entity: BibleEntity) : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }

    sealed interface DelegateAction {
        data class OpenEntity(val entity: BibleEntity) : DelegateAction
    }

    fun reducer(graphClient: GraphClient): Reducer<State, Action> = Reducer { state, action ->
        when (action) {
            Action.Started -> state.copy(isLoading = true).with(runEffect { send -> send(Action.EntitiesLoaded(runCatching { graphClient.entities(state.type) }.getOrDefault(emptyList()))) })
            is Action.EntitiesLoaded -> state.copy(isLoading = false, entities = action.entities).only()
            is Action.EntityTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenEntity(action.entity))))
            is Action.Delegate -> state.only()
        }
    }
}
