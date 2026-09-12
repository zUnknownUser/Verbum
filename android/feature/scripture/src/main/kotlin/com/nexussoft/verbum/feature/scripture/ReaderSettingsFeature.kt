package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.PreferencesClient
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.runEffect
import com.nexussoft.verbum.common.arch.with

/** Reader preferences. Persists on change; the parent mirrors the value into the reader. */
object ReaderSettingsFeature {
    data class State(val textScale: ReaderTextScale)

    sealed interface Action {
        data class TextScaleChanged(val scale: ReaderTextScale) : Action
    }

    fun reducer(preferences: PreferencesClient): Reducer<State, Action> = Reducer { state, action ->
        when (action) {
            is Action.TextScaleChanged -> state.copy(textScale = action.scale).with(
                runEffect { preferences.setString(ReaderTextScale.PREFERENCE_KEY, action.scale.name) },
            )
        }
    }
}
