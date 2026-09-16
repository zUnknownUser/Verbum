package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.PreferencesClient
import com.nexussoft.verbum.models.ReadingMode
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.runEffect
import com.nexussoft.verbum.common.arch.with

/** Reader preferences. Persists on change; the parent mirrors the value into the reader. */
object ReaderSettingsFeature {
    data class State(val textScale: ReaderTextScale, val readingMode: ReadingMode = ReadingMode.PAGES, val focusMode: Boolean = false)

    sealed interface Action {
        data class ModeChanged(val mode: ReadingMode): Action
        data class FocusChanged(val enabled: Boolean): Action
        data class TextScaleChanged(val scale: ReaderTextScale) : Action
    }

    fun reducer(preferences: PreferencesClient): Reducer<State, Action> = Reducer { state, action ->
        when (action) {
            is Action.ModeChanged -> state.copy(readingMode=action.mode).with(runEffect { preferences.setString(ChapterReaderFeature.MODE_KEY,action.mode.name) })
            is Action.FocusChanged -> state.copy(focusMode=action.enabled).with(runEffect { preferences.setString(ChapterReaderFeature.FOCUS_KEY,action.enabled.toString()) })
            is Action.TextScaleChanged -> state.copy(textScale = action.scale).with(
                runEffect { preferences.setString(ReaderTextScale.PREFERENCE_KEY, action.scale.name) },
            )
        }
    }
}
