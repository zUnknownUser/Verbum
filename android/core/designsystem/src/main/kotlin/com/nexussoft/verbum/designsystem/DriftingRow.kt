package com.nexussoft.verbum.designsystem

import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.nexussoft.verbum.designsystem.tokens.Motion
import com.nexussoft.verbum.designsystem.tokens.Spacing
import kotlin.math.roundToInt

/** Which edge a [DriftingRow] travels towards. */
enum class DriftDirection(internal val sign: Float) {
    START(-1f),
    END(1f),
}

/** Copies of the content laid end to end; the row is a window onto the middle of them. */
private const val COPIES = 6
/** How many of those copies sit before the window's origin. */
private const val COPIES_BEFORE = 2f
/** Seconds of travel a fling is worth, before it is clamped to half a period. */
private const val GLIDE = 0.15f

/**
 * A row of content that drifts sideways on its own and can be pushed sideways
 * with a finger. Mirrors iOS `DriftingRow`.
 *
 * The one place ambience is allowed (docs/DESIGN_SYSTEM.md "Drifting rows"):
 * the arrival chooser, where a handful of words should read like a surface you
 * browse rather than a form you must answer. What keeps it calm and cheap:
 *
 * - Drift is slow ([Motion.Drift], dp per second), so a word stays readable and
 *   a finger always overtakes it.
 * - Nothing re-composes per frame: the phase is read inside [Modifier.offset]'s
 *   lambda, so a moving row costs a placement, not a recomposition.
 * - The content is laid out [COPIES] times so drift and drag always have
 *   material either side of the window; because it repeats every period, the
 *   drag offset can be wrapped by a whole period without a visible seam, which
 *   is what keeps it bounded however far a finger travels.
 * - [drifts] turns the motion off (the row stays draggable) — see
 *   [rememberReduceMotion]. Callers that also need a still layout, for
 *   TalkBack or large text, should reach for `FlowRow` instead of this row.
 */
@Composable
fun DriftingRow(
    modifier: Modifier = Modifier,
    speed: Dp = Motion.Drift.medium,
    direction: DriftDirection = DriftDirection.START,
    spacing: Dp = Spacing.sm,
    drifts: Boolean = !rememberReduceMotion(),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val spacingPx = with(density) { spacing.roundToPx() }
    val speedPx = with(density) { speed.toPx() }
    /** Width of one copy plus the gap that follows it: the distance after which the row repeats. */
    var copyWidth by remember { mutableIntStateOf(0) }
    val period = (copyWidth + spacingPx).toFloat()
    val phase = remember { Animatable(0f) }
    var drag by remember { mutableFloatStateOf(0f) }

    // Keyed on the measurement, so a new one replaces the animation instead of
    // layering a second one over it.
    LaunchedEffect(period, speedPx, drifts) {
        phase.snapTo(0f)
        if (!drifts || period <= 0f || speedPx <= 0f) return@LaunchedEffect
        phase.animateTo(
            targetValue = period,
            animationSpec = infiniteRepeatable(
                animation = tween(((period / speedPx) * 1000f).roundToInt(), easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        )
    }

    Box(
        modifier
            .fillMaxWidth()
            .clipToBounds()
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> drag = wrapped(drag + delta, period) },
                onDragStopped = { velocity ->
                    if (period > 0f) {
                        val glide = (velocity * GLIDE).coerceIn(-period / 2f, period / 2f)
                        animate(
                            initialValue = drag,
                            targetValue = drag + glide,
                            animationSpec = tween(Motion.Drift.SETTLE_MS, easing = LinearOutSlowInEasing),
                        ) { value, _ -> drag = value }
                        drag = wrapped(drag, period)
                    }
                },
            ),
    ) {
        Row(
            // Unbounded so the strip keeps its own width, pinned to the start so
            // it can never resize or move the row it hangs out of.
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .offset { IntOffset((-COPIES_BEFORE * period + direction.sign * phase.value + drag).roundToInt(), 0) },
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            repeat(COPIES) { copy ->
                Row(
                    Modifier
                        .onSizeChanged { if (copy == 0) copyWidth = it.width }
                        // One copy is the row; the others are only its wrap-around.
                        .then(if (copy == 0) Modifier else Modifier.clearAndSetSemantics {}),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) { content() }
            }
        }
    }
}

/**
 * The nearest equivalent offset to zero. Shifting by a whole period is
 * invisible, so this keeps the offset small without moving the content.
 */
private fun wrapped(value: Float, period: Float): Float {
    if (period <= 0f) return 0f
    val remainder = value % period
    return when {
        remainder > period / 2f -> remainder - period
        remainder < -period / 2f -> remainder + period
        else -> remainder
    }
}

/** True when the user has turned animations off for the whole system. */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** True when a screen reader is exploring by touch, so nothing should move under the finger. */
@Composable
fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    }
}

/**
 * Softens both ends of a row that bleeds past the reading column, so words
 * arrive and leave instead of being cut off at the edge of the screen.
 */
fun Modifier.edgeFade(width: Dp = Spacing.xxl): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fade = minOf(width.toPx(), size.width / 4f)
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Black, Color.Transparent),
                startX = size.width - fade,
                endX = size.width,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * Lets content reach [horizontal] past the padding it was given on each side,
 * while still reporting the width it was offered — Compose has no negative
 * padding.
 */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = with(this) { horizontal.roundToPx() }
    val placeable = measurable.measure(
        constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth + extra * 2),
    )
    layout(placeable.width - extra * 2, placeable.height) { placeable.place(-extra, 0) }
}
