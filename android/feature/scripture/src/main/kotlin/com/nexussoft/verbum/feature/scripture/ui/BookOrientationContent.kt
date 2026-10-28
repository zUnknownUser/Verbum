package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.models.*

@Composable
internal fun BookOrientationContent(orientation: BookOrientation, bookId: String, sources: List<SourceReference>, openPassage: (PassageReference) -> Unit) {
    key(bookId) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.book_orientation_title), style = VerbumTypography.editorialHeadline, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.book_orientation_scope), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(orientation.genre, style = MaterialTheme.typography.bodySmall)
            Text(orientation.context, style = MaterialTheme.typography.bodyMedium)
            StudySection(stringResource(R.string.book_orientation_focus)) { Text(orientation.purpose) }
            StudySection(stringResource(R.string.book_orientation_structure)) {
                orientation.outline.forEach { part ->
                    TextButton(onClick = { openPassage(PassageReference(bookId, part.chapterStart)) }) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(part.title)
                            Text(stringResource(R.string.book_orientation_chapters, part.chapterStart, part.chapterEnd), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            StudySection(stringResource(R.string.book_orientation_begin)) {
                orientation.readings.forEach { StudyReading(it, openPassage) }
            }
            StudySection(stringResource(R.string.book_orientation_sources)) {
                Text(stringResource(R.string.book_orientation_source_scope), style = MaterialTheme.typography.bodySmall)
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                var failed by remember { mutableStateOf(false) }
                sources.filter { it.id in orientation.sourceReferenceIds }.forEach { source ->
                    val url = source.webUrl
                    if (url == null) Text(source.citation, style = MaterialTheme.typography.bodySmall)
                    else TextButton(onClick = { failed = runCatching { uriHandler.openUri(url) }.isFailure }) { Text(source.citation) }
                }
                if (failed) Text(stringResource(R.string.context_source_failed))
            }
        }
    }
}
