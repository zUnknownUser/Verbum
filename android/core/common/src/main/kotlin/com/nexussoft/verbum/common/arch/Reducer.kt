package com.nexussoft.verbum.common.arch

/**
 * Unidirectional data flow, shaped like The Composable Architecture so each
 * Android feature is a 1:1 twin of its iOS reducer (docs/PRODUCT.md §35, §62):
 *
 * - [Reducer.reduce] is a pure function `(state, action) -> (state, effect)`.
 * - [Effect] is the only place side effects happen; it can send actions back.
 * - [Store] runs it for the UI; [TestStore] runs it in tests and refuses to
 *   let a state change or a received action go unasserted.
 *
 * Big features are composed from small reducers with [pullback] and [combine].
 */
fun interface Reducer<S, A> {
    fun reduce(state: S, action: A): Reduced<S, A>
}

data class Reduced<S, A>(val state: S, val effect: Effect<A> = Effect.None)

/** Reduce without side effects. */
fun <S, A> S.only(): Reduced<S, A> = Reduced(this)

/** Reduce and schedule an effect. */
fun <S, A> S.with(effect: Effect<A>): Reduced<S, A> = Reduced(this, effect)

sealed interface Effect<out A> {
    data object None : Effect<Nothing>

    /** Re-dispatch an action synchronously after this reduce completes. */
    data class Send<A>(val action: A) : Effect<A>

    /**
     * Async work. [id] + [cancelInFlight] cancel a previous run with the same id,
     * so a newer chapter load replaces an older one.
     */
    class Run<A>(
        val id: Any? = null,
        val cancelInFlight: Boolean = false,
        val block: suspend (send: suspend (A) -> Unit) -> Unit,
    ) : Effect<A>

    data class Merge<A>(val effects: List<Effect<A>>) : Effect<A>
}

fun <A> merge(vararg effects: Effect<A>): Effect<A> = Effect.Merge(effects.toList())

fun <A> runEffect(
    id: Any? = null,
    cancelInFlight: Boolean = false,
    block: suspend (send: suspend (A) -> Unit) -> Unit,
): Effect<A> = Effect.Run(id, cancelInFlight, block)

fun <A, B> Effect<A>.map(transform: (A) -> B): Effect<B> = when (this) {
    Effect.None -> Effect.None
    is Effect.Send -> Effect.Send(transform(action))
    is Effect.Run -> Effect.Run(id, cancelInFlight) { send -> block { send(transform(it)) } }
    is Effect.Merge -> Effect.Merge(effects.map { it.map(transform) })
}

/**
 * Embed a child reducer in a parent (TCA `Scope`). The child runs on the slice
 * of parent state at [get]/[set]; its actions are wrapped with [embedAction].
 * Parent actions that are not child actions ([extractAction] returns null) pass through.
 */
fun <S, A, CS, CA> Reducer<CS, CA>.pullback(
    get: (S) -> CS,
    set: (S, CS) -> S,
    extractAction: (A) -> CA?,
    embedAction: (CA) -> A,
): Reducer<S, A> = Reducer { state, action ->
    val childAction = extractAction(action) ?: return@Reducer state.only()
    val reduced = reduce(get(state), childAction)
    Reduced(set(state, reduced.state), reduced.effect.map(embedAction))
}

/**
 * Optional child (TCA `ifLet`): runs only while the slice is non-null.
 */
fun <S, A, CS : Any, CA> Reducer<CS, CA>.pullbackOptional(
    get: (S) -> CS?,
    set: (S, CS?) -> S,
    extractAction: (A) -> CA?,
    embedAction: (CA) -> A,
): Reducer<S, A> = Reducer { state, action ->
    val childAction = extractAction(action) ?: return@Reducer state.only()
    val childState = get(state) ?: return@Reducer state.only()
    val reduced = reduce(childState, childAction)
    Reduced(set(state, reduced.state), reduced.effect.map(embedAction))
}

/** Run reducers in order, threading state through and merging effects (TCA `body`). */
fun <S, A> combine(vararg reducers: Reducer<S, A>): Reducer<S, A> = Reducer { state, action ->
    var current = state
    val effects = ArrayList<Effect<A>>(reducers.size)
    for (reducer in reducers) {
        val reduced = reducer.reduce(current, action)
        current = reduced.state
        if (reduced.effect != Effect.None) effects += reduced.effect
    }
    Reduced(current, if (effects.isEmpty()) Effect.None else Effect.Merge(effects))
}
