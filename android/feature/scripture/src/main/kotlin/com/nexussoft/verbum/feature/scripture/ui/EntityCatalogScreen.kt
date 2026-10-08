package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.EntityListFeature
import com.nexussoft.verbum.feature.scripture.EntityListFeature.Action
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.models.EntityCatalog
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntityCatalogScreen(state: EntityListFeature.State, onBack: () -> Unit, send: (Action) -> Unit) {
    val places = state.type == com.nexussoft.verbum.models.BibleEntityType.PLACE
    val identifier = if (places) "places" else "people"
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { send(Action.Started) }
    DisposableEffect(Unit) { onDispose { send(Action.Stopped) } }
    val scroll = rememberLazyListState(state.scrollIndex, state.scrollOffset)
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.firstVisibleItemIndex to scroll.firstVisibleItemScrollOffset }
            .distinctUntilChanged().collect { (index, offset) -> send(Action.ScrollChanged(index, offset)) }
    }
    var previousFilter by remember { mutableStateOf(state.query to state.letter) }
    LaunchedEffect(state.query, state.letter) {
        if (previousFilter != state.query to state.letter) {
            scroll.scrollToItem(0)
            previousFilter = state.query to state.letter
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(if (places) R.string.places else R.string.people)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(value = state.query, onValueChange = { send(Action.QueryChanged(it)) },
                label = { Text(stringResource(if (places) R.string.places_search else R.string.people_search)) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { send(Action.QueryChanged("")) }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) } },
                singleLine = true, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.readingMargin).testTag("$identifier.search"))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.readingMargin, vertical = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                (listOf("") + ('A'..'Z').map { it.toString() } + "#").forEach { letter ->
                    FilterChip(selected = state.letter == letter, onClick = { focus.clearFocus(); send(Action.LetterChanged(letter)) },
                        label = { Text(if (letter.isEmpty()) stringResource(if (places) R.string.places_all else R.string.people_all) else letter) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("$identifier.letter.${letter.ifEmpty { "all" }}"))
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(state = scroll, modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.readingMargin, vertical = Spacing.md)) {
                    state.entities.forEachIndexed { index, entity ->
                        val letter = EntityCatalog.letter(entity.name)
                        if (index == 0 || EntityCatalog.letter(state.entities[index - 1].name) != letter) item(key = "letter:$letter") {
                            Text(letter, style = VerbumTypography.editorialHeadline, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm).semantics { heading() })
                        }
                        item(key = entity.id) {
                            Surface(onClick = { send(Action.EntityTapped(entity)) }, color = MaterialTheme.colorScheme.background) {
                                Column {
                                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = Spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                            Text(entity.name, style = VerbumTypography.navigationSerif)
                                            entity.summary?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                                        }
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                    when {
                        state.isLoading -> item { Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                        state.failed -> item {
                            Text(stringResource(if (places) R.string.places_load_failed else R.string.people_load_failed), modifier = Modifier.padding(top = Spacing.xl))
                            OutlinedButton(onClick = { send(Action.Retry) }) { Text(stringResource(R.string.try_again)) }
                        }
                        state.hasLoaded && state.entities.isEmpty() -> item {
                            Text(stringResource(if (places) R.string.places_empty else R.string.people_empty), style = VerbumTypography.editorialHeadline, modifier = Modifier.padding(top = Spacing.xl))
                            Text(stringResource(R.string.people_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
                        }
                        state.nextOffset != null -> item {
                            OutlinedButton(onClick = { send(Action.LoadMore) }, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl).testTag("$identifier.showMore")) { Text(stringResource(R.string.history_more)) }
                        }
                    }
                }
            }
        }
    }
}
