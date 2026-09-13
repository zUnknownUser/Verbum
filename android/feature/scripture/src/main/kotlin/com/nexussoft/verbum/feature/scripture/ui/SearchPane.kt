package com.nexussoft.verbum.feature.scripture.ui

import com.nexussoft.verbum.models.localizedTitle
import com.nexussoft.verbum.models.localizedName
import androidx.compose.ui.res.stringResource
import com.nexussoft.verbum.feature.scripture.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.SearchFeature.Action
import com.nexussoft.verbum.feature.scripture.SearchFeature.Phase
import com.nexussoft.verbum.feature.scripture.SearchFeature.State
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.SearchResponse
import com.nexussoft.verbum.models.division

/**
 * One field, grouped results on paper. A reference opens the reader on the
 * IME action; books and passages are tappable; entities are listed but wait
 * for their detail screen (Task 7). Twin of the iOS `SearchView`.
 */
@Composable
internal fun SearchPane(state: State, send: (Action) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = state.query,
                onValueChange = { send(Action.QueryChanged(it)) },
                placeholder = { Text(stringResource(R.string.search_prompt)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
                trailingIcon = {
                    if (state.phase == Phase.SEARCHING) {
                        CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.outline)
                    } else if (state.query.isNotEmpty()) {
                        IconButton(onClick = { send(Action.QueryChanged("")) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear), tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { send(Action.Submitted) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }

        val results = state.results
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xxxl)) {
            if (state.isOffline) item { OfflineNotice() }
            state.askSuggestion?.let { question ->
                // §13: the question goes to Ask Scripture; the groups below are still search.
                header(R.string.ask_scripture)
                item { ResultRow(question, stringResource(R.string.ask_row_subtitle), Icons.Filled.Search, tappable = true) { send(Action.AskTapped) } }
            }
            when {
                results != null && !results.isEmpty -> resultSections(results, send)
                state.showsNoResults -> item { NoResults(state.query, state.isOffline) }
                state.query.isEmpty() -> item { Suggestions { send(Action.QueryChanged(it)) } }
            }
        }
    }
}

private fun LazyListScope.resultSections(results: SearchResponse, send: (Action) -> Unit) {
    if (results.passages.isNotEmpty()) {
        header(if (results.passages.size == 1) R.string.passage else R.string.passages)
        items(results.passages) { reference ->
            ResultRow(reference.formatted, stringResource(R.string.open_in_reader), Icons.Filled.Search, tappable = true) { send(Action.PassageTapped(reference)) }
        }
    }
    if (results.books.isNotEmpty()) {
        header(R.string.books)
        items(results.books, key = { "book-${it.id}" }) { book ->
            ResultRow(book.localizedName, stringResource(R.string.chapters_and_division, book.chapterCount, book.division.localizedTitle), Icons.AutoMirrored.Filled.KeyboardArrowRight, tappable = true) { send(Action.BookTapped(book)) }
        }
    }
    entitySection(R.string.people, results.entities(BibleEntityType.PERSON), send)
    entitySection(R.string.places, results.entities(BibleEntityType.PLACE), send)
    entitySection(R.string.themes, results.entities(BibleEntityType.THEME), send)
    entitySection(R.string.events, results.entities(BibleEntityType.EVENT), send)
}

private fun LazyListScope.entitySection(title: Int, entities: List<BibleEntity>, send: (Action) -> Unit) {
    if (entities.isEmpty()) return
    header(title)
    items(entities, key = { it.id }) { entity ->
        ResultRow(entity.name, entity.summary ?: "", null, tappable = true) { send(Action.EntityTapped(entity)) }
    }
}

private fun LazyListScope.header(title: Int) {
    item(key = "header-$title") {
        Text(
            stringResource(title).uppercase(),
            style = VerbumTypography.overline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.readingMargin, end = Spacing.readingMargin, top = Spacing.xl, bottom = Spacing.xs),
        )
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String, icon: ImageVector?, tappable: Boolean, onTap: () -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.readingMargin)) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (tappable) Modifier.clickable(onClick = onTap) else Modifier)
                .padding(vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            if (tappable) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Suggestions(onPick: (String) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.readingMargin, vertical = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(stringResource(R.string.try_label).uppercase(), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (example in listOf("John 3:16", "1 Samuel 17", "David", "Jerusalem", "Forgiveness", "why did Job suffer")) {
            Text(
                example,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable { onPick(example) },
            )
        }
    }
}

/** §52: the network state is named, never hidden behind thinner results. */
@Composable
private fun OfflineNotice() {
    Text(
        stringResource(R.string.search_offline_notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.readingMargin, vertical = Spacing.sm),
    )
}

@Composable
private fun NoResults(query: String, isOffline: Boolean) {
    Column(Modifier.padding(horizontal = Spacing.readingMargin, vertical = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.nothing_for, query), style = VerbumTypography.editorialHeadline, color = MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(if (isOffline) R.string.search_explainer_offline else R.string.search_explainer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

