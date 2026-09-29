package com.nexussoft.verbum.designsystem.tokens

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp

/**
 * Animation durations and curves (docs/PRODUCT.md §41, §64).
 *
 * Motion communicates navigation through knowledge, never ambience.
 * Pass the platform's "remove animations" setting to [resolved] to honour it.
 *
 * [Drift] is the single, deliberate exception (docs/DESIGN_SYSTEM.md "Drifting
 * rows"): the arrival chooser, where words move slowly enough to read against.
 */
object Motion {
    object Duration {
        const val QUICK_MS = 150
        const val STANDARD_MS = 250
        const val SPATIAL_MS = 450
    }

    /** State toggles, selection feedback. */
    fun <T> quick(): AnimationSpec<T> = tween(Duration.QUICK_MS, easing = LinearOutSlowInEasing)
    /** Most transitions. */
    fun <T> standard(): AnimationSpec<T> = tween(Duration.STANDARD_MS, easing = FastOutSlowInEasing)
    /** Graph expansion, focus transitions, timeline movement. */
    fun <T> spatial(): AnimationSpec<T> = tween(Duration.SPATIAL_MS, easing = FastOutSlowInEasing)

    /**
     * Ambient sideways drift, in dp per second. Slow enough that a word stays
     * readable while it moves, and that a finger always overtakes it.
     * Mirrors iOS `Motion.Drift`.
     */
    object Drift {
        val slow = 7.dp
        val medium = 9.dp
        val fast = 12.dp

        /** The short glide after a finger lets go of a drifting row. */
        const val SETTLE_MS = 550
    }

    /** Returns an instant spec when the user has animations disabled. */
    fun <T> resolved(spec: AnimationSpec<T>, reduceMotion: Boolean): AnimationSpec<T> =
        if (reduceMotion) snap() else spec
}
