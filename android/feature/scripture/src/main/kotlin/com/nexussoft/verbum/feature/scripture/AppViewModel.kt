package com.nexussoft.verbum.feature.scripture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexussoft.verbum.common.arch.Store

/** Owns the app store across configuration changes (fold/unfold, rotation). */
class AppViewModel(private val deps: AppFeature.Dependencies) : ViewModel() {
    val store: Store<AppFeature.State, AppFeature.Action> = Store(
        initialState = AppFeature.State(profile = ProfileFeature.initial(deps.preferences)),
        reducer = AppFeature.reducer(deps),
        scope = viewModelScope,
    )

    override fun onCleared() {
        // The old session scope is cancelled by ViewModel before cleanup; close live media too.
        CoroutineScope(Dispatchers.Main.immediate).launch {
            withTimeoutOrNull(5000) { deps.voiceClient.stop() }
            withTimeoutOrNull(5000) { deps.player.stop() }
        }
        super.onCleared()
    }

    init {
        store.send(AppFeature.Action.Started)
    }
}
