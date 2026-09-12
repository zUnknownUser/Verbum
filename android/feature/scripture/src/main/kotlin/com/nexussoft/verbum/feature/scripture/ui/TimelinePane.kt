package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.designsystem.tokens.Radius
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.feature.scripture.TimelineDates
import com.nexussoft.verbum.feature.scripture.TimelineFeature
import com.nexussoft.verbum.models.TimelineDatePrecision
import com.nexussoft.verbum.models.TimelineEvent

/**
 * A spine down the left, one row per period or event: the dating (with its uncertainty) above
 * the title, a bar for a span and a dot for a moment. Tap opens the row in place; people and
 * places inside go to their pages. Twin of iOS `TimelineView`.
 */
@Composable
internal fun TimelinePane(state: TimelineFeature.State, onBack: () -> Unit, send: (TimelineFeature.Action) -> Unit) {
    LaunchedEffect(Unit) { send(TimelineFeature.Action.Started) }
    val words = dateWords()
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.timeline), style = VerbumTypography.navigationSerif, modifier = Modifier.weight(1f).semantics { heading() }, textAlign = TextAlign.Center)
                Spacer(Modifier.width(64.dp))
            }
            when (val content = state.content) {
                TimelineFeature.Content.Idle, TimelineFeature.Content.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                TimelineFeature.Content.Failed -> Column(Modifier.fillMaxSize().padding(Spacing.readingMargin), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.timeline_failed), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { send(TimelineFeature.Action.RetryTapped) }) { Text(stringResource(R.string.try_again)) }
                }
                is TimelineFeature.Content.Loaded -> {
                    val listState = rememberLazyListState()
                    val target = state.highlightedEventId
                    LaunchedEffect(target) {
                        val index = content.events.indexOfFirst { it.id == target }
                        if (index >= 0) listState.scrollToItem(index)
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = Spacing.readingMargin, vertical = Spacing.lg),
                        ) {
                            itemsIndexed(content.events, key = { _, e -> e.id }) { _, event ->
                                TimelineRow(
                                    event = event,
                                    dates = TimelineDates.text(event, words),
                                    isSelected = state.selectedId == event.id,
                                    isHighlighted = state.highlight?.let { it in event.entityIds } ?: false,
                                    names = event.entityIds.mapNotNull { id -> state.entityNames[id]?.let { id to it } },
                                    onTap = { send(TimelineFeature.Action.EventTapped(event.id)) },
                                    onEntity = { send(TimelineFeature.Action.EntityTapped(it)) },
                                )
                            }
                            item {
                                Text(stringResource(R.string.timeline_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = Spacing.xl))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun dateWords(): TimelineDates.Words {
    val bc = stringResource(R.string.year_bc); val ad = stringResource(R.string.year_ad)
    val bcs = stringResource(R.string.years_bc); val ads = stringResource(R.string.years_ad)
    return TimelineDates.Words(
        circa = stringResource(R.string.date_circa),
        debated = stringResource(R.string.date_debated),
        unknown = stringResource(R.string.date_unknown),
        bc = { String.format(bc, it) }, ad = { String.format(ad, it) },
        bcRange = { a, b -> String.format(bcs, a, b) }, adRange = { a, b -> String.format(ads, a, b) },
    )
}

@Composable
private fun TimelineRow(
    event: TimelineEvent,
    dates: String,
    isSelected: Boolean,
    isHighlighted: Boolean,
    names: List<Pair<String, String>>,
    onTap: () -> Unit,
    onEntity: (String) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val hollow = event.datePrecision == TimelineDatePrecision.DEBATED || event.datePrecision == TimelineDatePrecision.UNKNOWN
    val hint = stringResource(if (isSelected) R.string.timeline_event_close else R.string.timeline_event_open)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        // Spine: a dot for a moment, a bar for a span; hollow when the dating is debated or unknown.
        Box(Modifier.width(12.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            val shape = if (event.isPeriod) RoundedCornerShape(50) else CircleShape
            Box(
                Modifier.padding(top = Spacing.md + 20.dp)
                    .size(if (event.isPeriod) 8.dp else 10.dp, if (event.isPeriod) 36.dp else 10.dp)
                    .background(if (hollow) MaterialTheme.colorScheme.background else accent, shape)
                    .border(1.5.dp, accent, shape),
            )
        }
        Column(
            Modifier.weight(1f)
                .background(if (isHighlighted) accent.copy(alpha = 0.06f) else MaterialTheme.colorScheme.background, RoundedCornerShape(Radius.md))
                .padding(vertical = Spacing.md, horizontal = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .semantics { contentDescription = "${event.title}. $dates. $hint"; selected = isSelected }
                    .clickable(onClick = onTap),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(dates, style = MaterialTheme.typography.labelMedium, color = if (event.datePrecision == TimelineDatePrecision.DEBATED) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    event.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            AnimatedVisibility(visible = isSelected) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.xs)) {
                    event.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (names.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            names.forEach { (id, name) ->
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accent,
                                    modifier = Modifier
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(50))
                                        .clickable { onEntity(id) }
                                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                                )
                            }
                        }
                    }
                    Text(stringResource(R.string.timeline_source), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
