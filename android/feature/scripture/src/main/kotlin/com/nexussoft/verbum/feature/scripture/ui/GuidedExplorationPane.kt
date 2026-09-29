package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.designsystem.DriftDirection
import com.nexussoft.verbum.designsystem.DriftingRow
import com.nexussoft.verbum.designsystem.bleed
import com.nexussoft.verbum.designsystem.edgeFade
import com.nexussoft.verbum.designsystem.rememberReduceMotion
import com.nexussoft.verbum.designsystem.rememberTouchExploration
import com.nexussoft.verbum.designsystem.tokens.Motion
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.GuidedExplorationFeature
import com.nexussoft.verbum.feature.scripture.GuidedExplorationFeature.Action
import com.nexussoft.verbum.feature.scripture.GuidedExplorationFeature.DelegateAction
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.models.ArrivalFeeling

@Composable
internal fun GuidedExplorationPane(state: GuidedExplorationFeature.State, onBack: () -> Unit, send: (Action) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(Spacing.readingMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            item {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(state.feeling?.let { feelingTitle(it) } ?: stringResource(R.string.arrival_title),
                    style = VerbumTypography.editorialTitle, modifier = Modifier.semantics { heading() })
            }
            if (state.feeling == null) {
                item { FeelingChooser { send(Action.Select(it)) } }
            } else {
                item { Text(stringResource(R.string.arrival_intent), style = VerbumTypography.editorialHeadline) }
                if (state.isLoading) item { CircularProgressIndicator() }
                state.plan?.let { plan ->
                    if (plan.isEditorialPreview) item { Text(stringResource(R.string.arrival_preview), style = MaterialTheme.typography.bodySmall) }
                    item { Text(plan.guidingQuestion, style = VerbumTypography.scripture) }
                    items(plan.passages) { reference ->
                        Column {
                            Text(reference.formatted, style = VerbumTypography.editorialHeadline)
                            TextButton(onClick = { send(Action.Delegate(DelegateAction.OpenPassage(reference))) }) { Text(stringResource(R.string.arrival_read)) }
                            TextButton(onClick = { send(Action.Delegate(DelegateAction.OpenContext(reference))) }) { Text(stringResource(R.string.context_title)) }
                        }
                    }
                    item { Text(stringResource(R.string.arrival_sources), style = MaterialTheme.typography.bodySmall) }
                }
                if (state.failed) item {
                    Text(stringResource(R.string.err_page))
                    TextButton(onClick = { send(Action.Select(state.feeling)) }) { Text(stringResource(R.string.try_again)) }
                }
                item { TextButton(onClick = { send(Action.ChangeFeeling) }) { Text(stringResource(R.string.arrival_change)) } }
            }
        }
    }
}

/**
 * The feelings as words adrift rather than a list to work through: three rows
 * that bleed past the reading column and move at walking pace in alternating
 * directions, each one also pushable with a finger. The point is that nothing
 * here asks to be completed — you reach in and take one. Mirrors iOS
 * `FeelingChooser`.
 *
 * Reduce Motion, TalkBack and large text get the same words as a still,
 * wrapping field instead, where every word is in reach at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeelingChooser(select: (ArrivalFeeling) -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val still = reduceMotion || rememberTouchExploration() || LocalDensity.current.fontScale > 1.3f
    // Points per second, alternating with the drift direction below so the rows
    // never lock into a pattern.
    val speeds = remember { listOf(Motion.Drift.medium, Motion.Drift.slow, Motion.Drift.fast) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(
            stringResource(R.string.arrival_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (still) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ArrivalFeeling.entries.forEach { feeling -> FeelingWord(feeling) { select(feeling) } }
            }
        } else {
            Column(
                // A row that ends before the edge of the screen reads as a list again.
                Modifier.bleed(Spacing.readingMargin).edgeFade(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                feelingRows().forEachIndexed { index, row ->
                    DriftingRow(
                        speed = speeds[index % speeds.size],
                        direction = if (index % 2 == 0) DriftDirection.START else DriftDirection.END,
                        drifts = !reduceMotion,
                    ) {
                        row.forEach { feeling -> FeelingWord(feeling) { select(feeling) } }
                    }
                }
            }
        }
    }
}

/**
 * Dealt round-robin so each row mixes long and short words, and so adding a
 * feeling later simply lands in the next row.
 */
private fun feelingRows(count: Int = 3): List<List<ArrivalFeeling>> =
    (0 until count).map { row -> ArrivalFeeling.entries.filterIndexed { index, _ -> index % count == row } }

/**
 * One feeling, set as a word on paper: serif, a hairline capsule, bronze only
 * while a finger is on it.
 */
@Composable
private fun FeelingWord(feeling: ArrivalFeeling, onTap: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val outline = if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val fill = if (pressed) {
        MaterialTheme.colorScheme.primary.copy(alpha = SELECTION_WASH)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    Text(
        feelingTitle(feeling),
        style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
        color = if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(fill, CircleShape)
            .border(HAIRLINE, outline, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onTap)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
    )
}

/** Bronze wash behind a word under a finger, matching iOS `Palette.selectionWash`. */
private const val SELECTION_WASH = 0.16f
private val HAIRLINE: Dp = 1.dp

@Composable
private fun feelingTitle(feeling: ArrivalFeeling): String = stringResource(when (feeling) {
    ArrivalFeeling.ANXIOUS -> R.string.arrival_anxious
    ArrivalFeeling.LOST -> R.string.arrival_lost
    ArrivalFeeling.GRATEFUL -> R.string.arrival_grateful
    ArrivalFeeling.TIRED -> R.string.arrival_tired
    ArrivalFeeling.AFRAID -> R.string.arrival_afraid
    ArrivalFeeling.ALONE -> R.string.arrival_alone
    ArrivalFeeling.ANGRY -> R.string.arrival_angry
    ArrivalFeeling.HOPELESS -> R.string.arrival_hopeless
    ArrivalFeeling.PEACEFUL -> R.string.arrival_peaceful
})
