package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.models.*

/** Rendering only: the containing feature owns reference previews/navigation. */
@Composable
internal fun ChapterStudyContent(study: ChapterStudy, sources: List<SourceReference>, openPassage: (PassageReference) -> Unit) {
    key(study.title) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(study.title, style = VerbumTypography.editorialHeadline, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.chapter_study_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StudyNoteContent(study.summary, openPassage)
            StudySection(stringResource(R.string.chapter_study_people)) { StudyNoteContent(study.participants, openPassage) }
            StudySection(stringResource(R.string.chapter_study_setting)) { StudyNoteContent(study.setting, openPassage) }
            StudySection(stringResource(R.string.chapter_study_literary)) { StudyNoteContent(study.literaryContext, openPassage) }
            StudySection(stringResource(R.string.chapter_study_outline)) {
                study.outline.forEach { part ->
                    Text(part.title, style = MaterialTheme.typography.bodyMedium)
                    StudyReading(part.reference, openPassage)
                }
            }
            StudySection(stringResource(R.string.chapter_study_connections)) {
                study.connections.forEach { connection ->
                    StudyReading(connection.reference, openPassage)
                    Text(connection.explanation, style = MaterialTheme.typography.bodyMedium)
                }
            }
            StudySection(stringResource(R.string.chapter_study_sources)) {
                val uriHandler = LocalUriHandler.current
                var failed by remember { mutableStateOf(false) }
                sources.filter { it.id in study.sourceReferenceIds }.forEach { source ->
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
private fun StudyNoteContent(note: StudyNote, openPassage: (PassageReference) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(note.text, style = MaterialTheme.typography.bodyMedium)
        note.references.forEach { StudyReading(it, openPassage) }
    }
}

@Composable
internal fun StudyReading(reference: PassageReference, openPassage: (PassageReference) -> Unit) {
    TextButton(onClick = { openPassage(reference) }) {
        Text(reference.formatted, style = VerbumTypography.navigationSerif)
    }
}

@Composable
internal fun StudySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(if (expanded) R.string.chapter_study_expanded else R.string.chapter_study_collapsed)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().semantics { stateDescription = description }) {
            Text(title, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        }
        if (expanded) content()
    }
}
