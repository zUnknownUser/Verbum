package com.nexussoft.verbum.common.arch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Runtime for a [Reducer]. [send] reduces synchronously on the caller's
 * thread — call it from the main thread — and launches effects in [scope]
 * (a `viewModelScope` in production). Effects send back through the same path.
 */
class Store<S, A>(
    initialState: S,
    private val reducer: Reducer<S, A>,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state

    private val inFlight = HashMap<Any, Job>()

    fun send(action: A) {
        val reduced = reducer.reduce(_state.value, action)
        _state.update { reduced.state }
        execute(reduced.effect)
    }

    private fun execute(effect: Effect<A>) {
        when (effect) {
            Effect.None -> Unit
            is Effect.Send -> send(effect.action)
            is Effect.Merge -> effect.effects.forEach(::execute)
            is Effect.Run -> {
                if (effect.cancelInFlight && effect.id != null) inFlight.remove(effect.id)?.cancel()
                val job = scope.launch { effect.block { send(it) } }
                if (effect.id != null) {
                    inFlight[effect.id] = job
                    job.invokeOnCompletion { if (inFlight[effect.id] === job) inFlight.remove(effect.id) }
                }
            }
        }
    }
}
