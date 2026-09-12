package com.nexussoft.verbum.common.arch

/**
 * Exhaustive test harness (TCA `TestStore`). Every [send] must describe the
 * resulting state; every action an effect sends back must be [receive]d; and
 * nothing may be left over at [finish]. A test that forgets a transition fails.
 *
 * Effects run to completion inside [send]/[receive] on the calling coroutine,
 * which is what makes assertions deterministic. Cancellation ids are honoured
 * only in the sense that a superseded effect's actions are dropped.
 */
class TestStore<S, A>(initialState: S, private val reducer: Reducer<S, A>) {
    var state: S = initialState
        private set

    private val received = ArrayDeque<A>()
    private val supersededIds = HashSet<Any>()
    private var generation = 0L
    private val liveGeneration = HashMap<Any, Long>()

    suspend fun send(action: A, expect: (S) -> S = { it }) {
        check(received.isEmpty()) {
            "Unreceived actions before send($action): $received. Call receive() for each, or finish()."
        }
        dispatch(action, expect)
    }

    suspend fun receive(expected: A, expect: (S) -> S = { it }) {
        val next = received.removeFirstOrNull() ?: throw AssertionError("Expected to receive $expected but no action was sent by any effect.")
        if (next != expected) throw AssertionError("Expected to receive $expected but got $next.")
        dispatch(next, expect)
    }

    /** Receive by shape when the payload is awkward to construct (e.g. errors). */
    suspend fun receive(describe: String, matches: (A) -> Boolean, expect: (S) -> S = { it }) {
        val next = received.removeFirstOrNull() ?: throw AssertionError("Expected to receive $describe but no action was sent by any effect.")
        if (!matches(next)) throw AssertionError("Expected to receive $describe but got $next.")
        dispatch(next, expect)
    }

    fun finish() {
        check(received.isEmpty()) { "Unreceived actions at end of test: $received" }
    }

    private suspend fun dispatch(action: A, expect: (S) -> S) {
        val reduced = reducer.reduce(state, action)
        val expectedState = expect(state)
        if (reduced.state != expectedState) {
            throw AssertionError(
                "State after $action did not match.\n  expected: $expectedState\n  actual:   ${reduced.state}",
            )
        }
        state = reduced.state
        execute(reduced.effect)
    }

    private suspend fun execute(effect: Effect<A>) {
        when (effect) {
            Effect.None -> Unit
            is Effect.Send -> received.addLast(effect.action)
            is Effect.Merge -> effect.effects.forEach { execute(it) }
            is Effect.Run -> {
                val myGeneration = ++generation
                if (effect.id != null) {
                    if (effect.cancelInFlight) supersededIds.add(effect.id)
                    liveGeneration[effect.id] = myGeneration
                }
                effect.block { action ->
                    val stale = effect.id != null && liveGeneration[effect.id] != myGeneration
                    if (!stale) received.addLast(action)
                }
            }
        }
    }
}
