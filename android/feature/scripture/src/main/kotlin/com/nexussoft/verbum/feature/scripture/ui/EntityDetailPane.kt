package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.EntityDetailFeature
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.SourceReference

/** An entity's page on paper (§9). Twin of iOS `EntityDetailView`. */
@Composable
fun EntityDetailPane(state: EntityDetailFeature.State, send: (EntityDetailFeature.Action) -> Unit) {
    LaunchedEffect(state.entityId) { if (state.content == EntityDetailFeature.Content.Idle) send(EntityDetailFeature.Action.Started) }
    when (val content = state.content) {
        EntityDetailFeature.Content.Idle, EntityDetailFeature.Content.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.outline) }
        EntityDetailFeature.Content.Failed ->
            Column(Modifier.fillMaxSize().padding(Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.err_page), style = VerbumTypography.editorialHeadline, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.padding(Spacing.sm))
                Button(onClick = { send(EntityDetailFeature.Action.RetryTapped) }) { Text(stringResource(R.string.try_again)) }
            }
        is EntityDetailFeature.Content.Loaded -> PageBody(content.page, send)
    }
}

@Composable
private fun PageBody(page: EntityDetailFeature.Page, send: (EntityDetailFeature.Action) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            contentPadding = PaddingValues(start = Spacing.readingMargin, end = Spacing.readingMargin, top = Spacing.sm, bottom = Spacing.xxxl * 2),
        ) {
            item { Header(page) }
            if (page.neighborhood.nodes.isNotEmpty()) {
                item { EntityRow(stringResource(R.string.explore_graph), stringResource(R.string.explore_graph_subtitle, page.entity.name)) { send(EntityDetailFeature.Action.GraphTapped) } }
            }
            if (page.isOnTimeline) {
                item { EntityRow(stringResource(R.string.view_in_timeline), stringResource(R.string.view_in_timeline_subtitle, page.entity.name)) { send(EntityDetailFeature.Action.TimelineTapped) } }
            }
            item { EntityRow(stringResource(R.string.voice_talk_entity, page.entity.name), stringResource(R.string.voice_talk_entity_subtitle)) { send(EntityDetailFeature.Action.TalkTapped) } }
            if (page.passages.isNotEmpty()) {
                sectionHeader(R.string.key_passages)
                items(page.passages) { reference -> EntityRow(reference.formatted, null) { send(EntityDetailFeature.Action.PassageTapped(reference)) } }
            }
            related(R.string.people, page.related(BibleEntityType.PERSON), send)
            related(R.string.places, page.related(BibleEntityType.PLACE), send)
            related(R.string.events, page.related(BibleEntityType.EVENT), send)
            related(R.string.themes, page.related(BibleEntityType.THEME), send)
            item { Sources(page.sources) }
        }
    }
}

@Composable
private fun Header(page: EntityDetailFeature.Page) {
    val kind = when (page.entity.type) {
        BibleEntityType.PERSON -> R.string.kind_person
        BibleEntityType.PLACE -> R.string.kind_place
        BibleEntityType.EVENT -> R.string.kind_event
        BibleEntityType.THEME -> R.string.kind_theme
        BibleEntityType.PASSAGE -> R.string.passage
        BibleEntityType.BOOK -> R.string.kind_book
        BibleEntityType.PROPHECY -> R.string.kind_prophecy
        BibleEntityType.ORIGINAL_TERM -> R.string.kind_original_term
        BibleEntityType.HISTORICAL_PERIOD -> R.string.kind_period
    }
    Column(Modifier.padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(kind).uppercase(), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.primary)
        Text(page.entity.name, style = VerbumTypography.editorialTitle, color = MaterialTheme.colorScheme.onSurface)
        if (page.detail.aliases.isNotEmpty()) {
            Text(page.detail.aliases.joinToString(" · "), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            page.detail.role?.let { Fact(stringResource(R.string.fact_role), it) }
            page.detail.approximateDates?.let { Fact(stringResource(R.string.fact_when), it) }
            page.detail.modernGeography?.let { Fact(stringResource(R.string.fact_today), it) }
        }
        page.entity.summary?.let {
            Text(it, style = VerbumTypography.scripture, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.md))
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(56.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun LazyListScope.related(title: Int, entities: List<BibleEntity>, send: (EntityDetailFeature.Action) -> Unit) {
    if (entities.isEmpty()) return
    sectionHeader(title)
    items(entities, key = { it.id }) { entity -> EntityRow(entity.name, entity.summary) { send(EntityDetailFeature.Action.EntityTapped(entity)) } }
}

private fun LazyListScope.sectionHeader(title: Int) {
    item(key = "h-$title") {
        Text(stringResource(title).uppercase(), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.xs))
    }
}

@Composable
internal fun EntityRow(title: String, subtitle: String?, onTap: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onTap).padding(vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                if (!subtitle.isNullOrEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Sources(sources: List<SourceReference>) {
    Column(Modifier.padding(top = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.sources).uppercase(), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (source in sources) Text(source.citation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
