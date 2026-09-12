package com.nexussoft.verbum.designsystem.tokens

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

/**
 * Animation durations and curves (docs/PRODUCT.md §41, §64).
 *
 * Motion communicates navigation through knowledge, never ambience.
 * Pass the platform's "remove animations" setting to [resolved] to honour it.
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

    /** Returns an instant spec when the user has animations disabled. */
    fun <T> resolved(spec: AnimationSpec<T>, reduceMotion: Boolean): AnimationSpec<T> =
        if (reduceMotion) snap() else spec
}
