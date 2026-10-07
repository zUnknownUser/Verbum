package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.feature.scripture.ReadingCollectionFeature
import com.nexussoft.verbum.feature.scripture.ReadingCollectionFeature.Action
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.ReaderCanon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingHistoryScreen(state: ReadingCollectionFeature.State, onBack: () -> Unit, send: (Action) -> Unit) {
    val sync = LocalPersonalSync.current
    LaunchedEffect(sync.revision) { send(Action.JourneyStarted) }
    val scroll = rememberLazyListState(state.historyScrollIndex, state.historyScrollOffset)
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.firstVisibleItemIndex to scroll.firstVisibleItemScrollOffset }
            .distinctUntilChanged().collect { (index, offset) -> send(Action.HistoryScrollChanged(index, offset)) }
    }
    val locale = if (BookLanguage.current == BookLanguage.PORTUGUESE) Locale.forLanguageTag("pt-BR") else Locale.US
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val dateFormat = remember(locale, zone) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone) }
    val groups = remember(state.activity, state.historyQuery, state.historyVisibleCount, zone) {
        state.visibleVisits.groupBy { Instant.ofEpochMilli(it.lastOpened).atZone(zone).toLocalDate() }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.history_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.historyQuery,
                onValueChange = { send(Action.HistoryQueryChanged(it)) },
                label = { Text(stringResource(R.string.history_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (state.historyQuery.isNotEmpty()) IconButton(onClick = { send(Action.HistoryQueryChanged("")) }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.clear))
                    }
                },
                singleLine = true, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.readingMargin).testTag("journey.history.search"),
            )
            LaunchedEffect(state.historyQuery) { if (state.historyScrollIndex == 0 && state.historyScrollOffset == 0) scroll.scrollToItem(0) }
            LazyColumn(state = scroll, contentPadding = PaddingValues(Spacing.readingMargin), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (state.failed) item {
                    Text(stringResource(R.string.collection_load_failed))
                    TextButton(onClick = { send(Action.JourneyStarted) }) { Text(stringResource(R.string.try_again)) }
                }
                if (state.loading && state.activity.visits.isEmpty()) item { CircularProgressIndicator() }
                if (groups.isEmpty() && !state.loading && !state.failed) item {
                    Text(stringResource(R.string.history_empty), style = VerbumTypography.editorialHeadline)
                    Text(stringResource(R.string.history_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                groups.forEach { (day, visits) ->
                    item(key = "day:$day") {
                        val title = when (day) {
                            today -> stringResource(R.string.today)
                            today.minusDays(1) -> stringResource(R.string.history_yesterday)
                            else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
                        }
                        Text(title, style = VerbumTypography.overline, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Spacing.md))
                    }
                    items(visits, key = { ReaderCanon.key(it.reference) }) { visit ->
                        Surface(onClick = { send(Action.Open(visit.reference)) }, color = MaterialTheme.colorScheme.background) {
                            Column(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(visit.reference.formatted, style = VerbumTypography.navigationSerif, modifier = Modifier.weight(1f))
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(dateFormat.format(Instant.ofEpochMilli(visit.lastOpened)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                HorizontalDivider()
                            }
                        }
                    }
                }
                if (state.hasMore) item {
                    OutlinedButton(onClick = { send(Action.HistoryShowMore) }, modifier = Modifier.fillMaxWidth().testTag("journey.history.showMore")) {
                        Text(stringResource(R.string.history_more))
                    }
                }
            }
        }
    }
}
