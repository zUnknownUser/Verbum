package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import com.nexussoft.verbum.designsystem.EditorialCard
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.ReadingCollectionFeature
import com.nexussoft.verbum.feature.scripture.ReadingCollectionFeature.Action
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.models.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun ReadingCollectionScreen(state: ReadingCollectionFeature.State, journey: Boolean, send: (Action) -> Unit) {
    val sync = LocalPersonalSync.current
    LaunchedEffect(journey, sync.revision) { send(if (journey) Action.JourneyStarted else Action.Started) }
    val entries = remember(state.annotations, state.filter, state.query) { ReadingCollection.entries(state.annotations, state.filter, state.query) }
    val locale = if (BookLanguage.current == BookLanguage.PORTUGUESE) Locale.forLanguageTag("pt-BR") else Locale.US
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(ZoneId.systemDefault()) }
    LazyColumn(modifier = Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(Spacing.readingMargin), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        item {
            Text(stringResource(if (journey) R.string.tab_journey else R.string.tab_library), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.lg))
            Text(stringResource(if (journey) R.string.collection_journey_title else R.string.collection_library_title), style = VerbumTypography.editorialTitle)
            Spacer(Modifier.height(Spacing.md))
            Text(stringResource(if (journey) R.string.collection_journey_body else R.string.collection_library_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.loading && state.annotations.isEmpty() && (!journey || state.activity.visits.isEmpty())) item { CircularProgressIndicator() }
        if (state.failed) item {
            Text(stringResource(R.string.collection_load_failed), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { send(if (journey) Action.JourneyStarted else Action.Retry) }) { Text(stringResource(R.string.try_again)) }
        }
        if (journey) {
            item {
                EditorialCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                    Column(Modifier.weight(1f)) { Metric(state.activity.days.size, R.string.collection_days) }
                    Column(Modifier.weight(1f)) { Metric(state.activity.visits.size, R.string.collection_chapters) }
                }
                }
                Spacer(Modifier.height(Spacing.md))
                Text(stringResource(R.string.collection_visits_explained), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            (state.lastRead ?: state.activity.visits.firstOrNull()?.reference)?.let { reference ->
                item {
                    Surface(onClick = { send(Action.Open(reference)) }, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f), shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(stringResource(R.string.continue_reading), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(reference.formatted, style = VerbumTypography.editorialHeadline)
                        }
                    }
                }
            }
            if (state.activity.visits.isEmpty() && !state.loading && !state.failed) item {
                CollectionEmpty(R.string.collection_journey_empty, R.string.collection_journey_empty_body, send)
            }
            if (state.activity.visits.isNotEmpty()) item { Text(stringResource(R.string.collection_recent), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.primary) }
            items(state.activity.visits.take(10), key = { ReaderCanon.key(it.reference) }) { visit ->
                Column(Modifier.fillMaxWidth().clickable { send(Action.Open(visit.reference)) }.padding(vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(visit.reference.formatted, style = VerbumTypography.navigationSerif)
                    Text(dateFormat.format(Instant.ofEpochMilli(visit.lastOpened)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                }
            }
            if (state.activity.visits.isNotEmpty()) item {
                OutlinedButton(onClick = { send(Action.HistoryTapped) }, modifier = Modifier.fillMaxWidth().testTag("journey.history")) {
                    Text(stringResource(R.string.history_view_all))
                }
            }
        } else {
            item {
                OutlinedTextField(value = state.query, onValueChange = { send(Action.QueryChanged(it)) }, label = { Text(stringResource(R.string.collection_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("library.search"))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ReadingCollectionFilter.entries.forEach { filter ->
                        FilterChip(selected = state.filter == filter, onClick = { send(Action.FilterChanged(filter)) }, label = { Text(stringResource(filterTitle(filter))) })
                    }
                }
            }
            if (entries.isEmpty() && !state.loading && !state.failed) item {
                val empty = state.query.isEmpty() && state.filter == ReadingCollectionFilter.ALL
                CollectionEmpty(if (empty) R.string.collection_library_empty else R.string.collection_no_matches, if (empty) R.string.collection_library_empty_body else R.string.collection_no_matches_body, send)
            }
            items(entries, key = { it.id }) { item ->
                EditorialCard {
                Column(Modifier.fillMaxWidth().clickable { send(Action.Open(item.reference)) }, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(item.reference.formatted, style = VerbumTypography.navigationSerif)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        if (item.bookmarked) Icon(Icons.Outlined.BookmarkBorder, stringResource(R.string.collection_saved), tint = MaterialTheme.colorScheme.primary)
                        if (item.highlight != null) Icon(Icons.Outlined.Highlight, stringResource(R.string.collection_highlights), tint = MaterialTheme.colorScheme.primary)
                        if (item.note.isNotEmpty()) Icon(Icons.Outlined.EditNote, stringResource(R.string.collection_notes), tint = MaterialTheme.colorScheme.primary)
                    }
                    if (item.note.isNotEmpty()) Text(item.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                }
                }
            }
        }
        item { Text(sync.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun Metric(value: Int, label: Int) {
    Text(value.toString(), style = VerbumTypography.editorialTitle)
    Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun CollectionEmpty(title: Int, body: Int, send: (Action) -> Unit) {
    EditorialCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(title), style = VerbumTypography.editorialHeadline)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { send(Action.Browse) }) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, null)
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.collection_browse))
        }
    }
}
private fun filterTitle(filter: ReadingCollectionFilter) = when (filter) {
    ReadingCollectionFilter.ALL -> R.string.collection_all
    ReadingCollectionFilter.SAVED -> R.string.collection_saved
    ReadingCollectionFilter.HIGHLIGHTS -> R.string.collection_highlights
    ReadingCollectionFilter.NOTES -> R.string.collection_notes
}
