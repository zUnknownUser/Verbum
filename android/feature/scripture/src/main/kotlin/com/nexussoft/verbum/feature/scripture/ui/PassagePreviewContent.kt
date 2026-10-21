package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nexussoft.verbum.clients.helloao.HelloAOTranslation
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.models.BiblePassage
import com.nexussoft.verbum.models.PassageReference

/** Shared preview rendering. The containing feature owns loading, cancellation and navigation. */
@Composable
internal fun PassagePreviewContent(
    reference: PassageReference,
    passage: BiblePassage?,
    loading: Boolean,
    failed: Boolean,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onOpenInReader: () -> Unit,
    modifier: Modifier = Modifier,
    canOpen: Boolean = true,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg)
        .padding(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        TextButton(onClick = onClose) { Text(stringResource(R.string.reader_back_to_study)) }
        Text(reference.formatted, style = VerbumTypography.editorialTitle)
        when {
            loading -> CircularProgressIndicator()
            failed -> {
                Text(stringResource(R.string.reader_layer_failed))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
            }
            passage != null -> {
                Text(HelloAOTranslation.name(passage.translationId), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(passage.text, style = VerbumTypography.scripture)
            }
        }
        TextButton(onClick = onOpenInReader, enabled = canOpen) { Text(stringResource(R.string.reader_open_in_reader)) }
        footer()
    }
}
