package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.EntityListFeature.Action
import com.nexussoft.verbum.feature.scripture.R

internal data class EventPeriod(val id: String, val title: Int, val subtitle: Int)
internal val eventPeriods = listOf(
    EventPeriod("origins", R.string.events_period_origins, R.string.events_period_origins_sub),
    EventPeriod("patriarchs", R.string.events_period_patriarchs, R.string.events_period_patriarchs_sub),
    EventPeriod("exodus", R.string.events_period_exodus, R.string.events_period_exodus_sub),
    EventPeriod("judges", R.string.events_period_judges, R.string.events_period_judges_sub),
    EventPeriod("monarchy", R.string.events_period_monarchy, R.string.events_period_monarchy_sub),
    EventPeriod("kingdoms", R.string.events_period_kingdoms, R.string.events_period_kingdoms_sub),
    EventPeriod("exile", R.string.events_period_exile, R.string.events_period_exile_sub),
    EventPeriod("return", R.string.events_period_return, R.string.events_period_return_sub),
    EventPeriod("jesus", R.string.events_period_jesus, R.string.events_period_jesus_sub),
    EventPeriod("church", R.string.events_period_church, R.string.events_period_church_sub),
)

@Composable
internal fun EventDiscoveryScreen(send: (Action) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
            contentPadding = PaddingValues(Spacing.readingMargin), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            item {
                Text(stringResource(R.string.events_periods_title), style = VerbumTypography.editorialTitle)
                Text(stringResource(R.string.events_periods_intro), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
            }
            item {
                Button(onClick = { send(Action.CategoryChanged("")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("events.all")) {
                    Text(stringResource(R.string.events_all))
                }
            }
            items(eventPeriods, key = { it.id }) { period ->
                Surface(onClick = { send(Action.CategoryChanged(period.id)) }, color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().testTag("events.period.${period.id}")) {
                    Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(stringResource(period.title), style = VerbumTypography.navigationSerif)
                            Text(stringResource(period.subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { Text(stringResource(R.string.events_periods_order), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
