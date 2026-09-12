package com.nexussoft.verbum.common.arch

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StoreTest {
    data class Counter(val count: Int = 0, val label: String = "")

    sealed interface Action {
        data object Increment : Action
        data object IncrementAsync : Action
        data class Loaded(val value: Int) : Action
        data class Child(val action: ChildAction) : Action
    }

    sealed interface ChildAction {
        data class Rename(val to: String) : ChildAction
    }

    private val child = Reducer<String, ChildAction> { state, action ->
        when (action) {
            is ChildAction.Rename -> action.to.only()
        }
    }

    private val reducer: Reducer<Counter, Action> = combine(
        child.pullback(
            get = { it.label },
            set = { s, l -> s.copy(label = l) },
            extractAction = { (it as? Action.Child)?.action },
            embedAction = { Action.Child(it) },
        ),
        Reducer { state, action ->
            when (action) {
                Action.Increment -> state.copy(count = state.count + 1).only()
                Action.IncrementAsync -> state.with(runEffect(id = "load", cancelInFlight = true) { send -> send(Action.Loaded(state.count + 10)) })
                is Action.Loaded -> state.copy(count = action.value).only()
                is Action.Child -> state.only()
            }
        },
    )

    @Test
    fun testStoreAssertsEveryTransition() = runTest {
        val store = TestStore(Counter(), reducer)
        store.send(Action.Increment) { it.copy(count = 1) }
        store.send(Action.IncrementAsync)
        store.receive(Action.Loaded(11)) { it.copy(count = 11) }
        store.send(Action.Child(ChildAction.Rename("x"))) { it.copy(label = "x") }
        store.finish()
    }

    @Test
    fun wrongStateExpectationFails() = runTest {
        val store = TestStore(Counter(), reducer)
        assertFailsWith<AssertionError> { store.send(Action.Increment) { it.copy(count = 2) } }
    }

    @Test
    fun unreceivedActionFailsNextSend() = runTest {
        val store = TestStore(Counter(), reducer)
        store.send(Action.IncrementAsync)
        assertFailsWith<IllegalStateException> { store.send(Action.Increment) { it.copy(count = 1) } }
    }

    @Test
    fun unreceivedActionFailsFinish() = runTest {
        val store = TestStore(Counter(), reducer)
        store.send(Action.IncrementAsync)
        assertFailsWith<IllegalStateException> { store.finish() }
    }

    @Test
    fun liveStoreRunsEffects() = runTest {
        val store = Store(Counter(), reducer, this)
        store.send(Action.IncrementAsync)
        advanceUntilIdle()
        assertEquals(10, store.state.value.count)
    }
}
