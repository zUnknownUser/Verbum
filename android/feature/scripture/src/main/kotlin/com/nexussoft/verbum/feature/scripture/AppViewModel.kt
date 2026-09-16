package com.nexussoft.verbum.feature.scripture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexussoft.verbum.common.arch.Store

/** Owns the app store across configuration changes (fold/unfold, rotation). */
class AppViewModel(deps: AppFeature.Dependencies) : ViewModel() {
    val store: Store<AppFeature.State, AppFeature.Action> = Store(
        initialState = AppFeature.State(profile = ProfileFeature.initial(deps.preferences)),
        reducer = AppFeature.reducer(deps),
        scope = viewModelScope,
    )

    init {
        store.send(AppFeature.Action.Started)
    }
}
