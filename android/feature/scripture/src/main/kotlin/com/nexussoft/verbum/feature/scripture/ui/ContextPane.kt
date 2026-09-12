package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.*
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.models.*

@Composable
internal fun ContextPane(state: ContextFeature.State, onBack: () -> Unit, send: (ContextFeature.Action) -> Unit) {
    LaunchedEffect(state.reference) { send(ContextFeature.Action.Started) }
    val uriHandler = LocalUriHandler.current
    var sourceFailed by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(Spacing.readingMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                ContextHeading(stringResource(R.string.context_title))
                Text(state.reference.formatted, style = VerbumTypography.editorialTitle)
                TextButton(onClick = { send(ContextFeature.Action.PassageTapped(state.reference)) }) {
                    Text(stringResource(R.string.context_read))
                }
            }
            when (val content = state.content) {
                ContextFeature.Content.Idle, ContextFeature.Content.Loading -> item {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.context_loading))
                }
                ContextFeature.Content.Unavailable -> item { Text(stringResource(R.string.context_unavailable)) }
                ContextFeature.Content.Failed -> item {
                    Text(stringResource(R.string.err_page))
                    TextButton(onClick = { send(ContextFeature.Action.RetryTapped) }) { Text(stringResource(R.string.try_again)) }
                }
                is ContextFeature.Content.Loaded -> {
                    if (content.page.isFixture) item {
                        Text(stringResource(R.string.context_fixture), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item { Text(stringResource(R.string.context_intro)) }
                    for ((type, title) in listOf(BibleEntityType.PERSON to R.string.people, BibleEntityType.PLACE to R.string.places, BibleEntityType.EVENT to R.string.events, BibleEntityType.THEME to R.string.themes)) {
                        val entities = content.page.entities.filter { it.type == type }
                        if (entities.isNotEmpty()) {
                            item { ContextHeading(stringResource(title)) }
                            items(entities, key = { it.id }) { entity ->
                                TextButton(onClick = { send(ContextFeature.Action.EntityTapped(entity)) }, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(entity.name, style = VerbumTypography.editorialHeadline)
                                        entity.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                            }
                        }
                    }
                    if (content.page.relatedPassages.isNotEmpty()) {
                        item { ContextHeading(stringResource(R.string.context_related)) }
                        items(content.page.relatedPassages) { reference -> ContextPassage(reference, send) }
                    }
                    item { ContextHeading(stringResource(R.string.sources)) }
                    items(content.page.sources, key = { it.id }) { source ->
                        Column {
                            Text(source.citation, style = MaterialTheme.typography.bodySmall)
                            val address = source.url
                            if (address?.startsWith("https://") == true) {
                                TextButton(onClick = {
                                    sourceFailed = runCatching { uriHandler.openUri(address) }.isFailure
                                }) { Text(stringResource(R.string.context_source)) }
                            }
                        }
                    }
                    if (sourceFailed) item { Text(stringResource(R.string.context_source_failed)) }
                }
            }
            item { ContextHeading(stringResource(R.string.context_surrounding)) }
            ChapterNavigation.previous(state.reference)?.let { reference -> item { ContextPassage(reference, send) } }
            ChapterNavigation.next(state.reference)?.let { reference -> item { ContextPassage(reference, send) } }
        }
    }
}

@Composable
private fun ContextHeading(title: String) {
    Text(title, style = VerbumTypography.overline, modifier = Modifier.semantics { heading() })
}

@Composable
private fun ContextPassage(reference: PassageReference, send: (ContextFeature.Action) -> Unit) {
    TextButton(onClick = { send(ContextFeature.Action.PassageTapped(reference)) }) { Text(reference.formatted) }
}
