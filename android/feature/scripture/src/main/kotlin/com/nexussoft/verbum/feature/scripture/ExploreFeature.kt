package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.common.arch.Effect
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.models.BibleEntityType

/** The discovery surface (§7). Twin of iOS `ExploreFeature`. */
object ExploreFeature {
    data object State

    enum class Entry(val entityType: BibleEntityType?) {
        PEOPLE(BibleEntityType.PERSON), PLACES(BibleEntityType.PLACE), THEMES(BibleEntityType.THEME), EVENTS(BibleEntityType.EVENT), TIMELINE(null), BOOKS(null)
    }

    sealed interface Action {
        data class EntryTapped(val entry: Entry) : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }

    sealed interface DelegateAction {
        data class Open(val entry: Entry) : DelegateAction
    }

    val reducer: Reducer<State, Action> = Reducer { state, action ->
        when (action) {
            is Action.EntryTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.Open(action.entry))))
            is Action.Delegate -> state.only()
        }
    }
}
