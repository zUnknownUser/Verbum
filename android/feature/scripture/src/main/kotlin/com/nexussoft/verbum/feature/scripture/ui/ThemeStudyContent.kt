package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.ThemeStudy
import com.nexussoft.verbum.models.SourceReference

/** Rendering only: guide references preview in the owning feature; entities use its existing route. */
@Composable
internal fun ThemeStudyContent(
    themeId: String,
    study: ThemeStudy,
    sources: List<SourceReference>,
    openPassage: (PassageReference) -> Unit,
    openEntity: (BibleEntity) -> Unit,
) {
    key(themeId) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StudyHeading(stringResource(R.string.theme_study_title))
            Text(stringResource(R.string.theme_study_intro), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(study.overview.text, style = MaterialTheme.typography.bodyMedium)
            study.overview.references.distinct().filter { reference -> study.readings.none { it.reference == reference } }
                .forEach { ThemeReadingLink(it, openPassage) }
            StudyHeading(stringResource(R.string.theme_study_readings))
            study.readings.forEachIndexed { index, reading ->
                key("reading-${reading.id}") {
                    ThemeSection(stringResource(R.string.theme_study_reading_title, index + 1, reading.title), reading.reference.formatted) {
                        ThemeReadingLink(reading.reference, openPassage)
                        StudyParagraph(R.string.theme_study_context, reading.context)
                        StudyParagraph(R.string.theme_study_observation, reading.observation)
                        StudyParagraph(R.string.theme_study_significance, reading.significance)
                        StudyParagraph(R.string.theme_study_question, reading.question)
                    }
                }
            }
            StudyHeading(stringResource(R.string.theme_study_comparisons))
            study.comparisons.forEach { comparison ->
                key("comparison-${comparison.id}") {
                    ThemeSection(comparison.title) {
                        comparison.references.forEach { ThemeReadingLink(it, openPassage) }
                        StudyParagraph(R.string.theme_study_connection, comparison.explanation)
                        StudyParagraph(R.string.theme_study_difference, comparison.difference)
                    }
                }
            }
            StudyHeading(stringResource(R.string.theme_study_entities))
            study.connections.forEach { connection ->
                key("connection-${connection.entity.id}") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        EntityRow(connection.entity.name, null) { openEntity(connection.entity) }
                        Text(connection.explanation, style = MaterialTheme.typography.bodyMedium)
                        ThemeReadingLink(connection.reference, openPassage)
                    }
                }
            }
            ThemeSection(stringResource(R.string.theme_study_sources)) {
                val uriHandler = LocalUriHandler.current
                var failed by remember { mutableStateOf(false) }
                sources.filter { it.id in study.sourceReferenceIds }.distinctBy { it.id }.forEach { source ->
                    val url = source.webUrl
                    if (url == null) Text(source.citation, style = MaterialTheme.typography.bodySmall)
                    else TextButton(onClick = { failed = runCatching { uriHandler.openUri(url) }.isFailure }) {
                        Text(source.citation, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (failed) Text(stringResource(R.string.context_source_failed))
            }
        }
    }
}

@Composable
private fun StudyHeading(title: String) {
    Text(title, style = VerbumTypography.editorialHeadline, modifier = Modifier.semantics { heading() })
}

@Composable
private fun StudyParagraph(label: Int, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ThemeReadingLink(reference: PassageReference, openPassage: (PassageReference) -> Unit) {
    TextButton(onClick = { openPassage(reference) }) {
        Text(reference.formatted, style = VerbumTypography.navigationSerif)
    }
}

@Composable
private fun ThemeSection(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(if (expanded) R.string.chapter_study_expanded else R.string.chapter_study_collapsed)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().semantics { stateDescription = description }) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(title)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        }
        if (expanded) content()
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
