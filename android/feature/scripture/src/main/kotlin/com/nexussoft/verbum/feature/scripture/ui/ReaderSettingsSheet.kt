package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.ui.res.stringResource
import com.nexussoft.verbum.feature.scripture.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import com.nexussoft.verbum.models.ReadingMode
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.ReaderSettingsFeature.Action
import com.nexussoft.verbum.feature.scripture.ReaderSettingsFeature.State
import com.nexussoft.verbum.feature.scripture.ReaderTextScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheet(state: State, send: (Action) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(stringResource(R.string.reader_reading), style=VerbumTypography.overline)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ReadingMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(selected=state.readingMode==mode,onClick={send(Action.ModeChanged(mode))},shape=SegmentedButtonDefaults.itemShape(index,2)) {
                        Text(stringResource(if(mode==ReadingMode.PAGES) R.string.reader_pages else R.string.reader_continuous))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(stringResource(R.string.reader_quiet))
                Switch(checked=state.focusMode,onCheckedChange={send(Action.FocusChanged(it))})
            }
            Text(stringResource(R.string.text_size).uppercase(), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ReaderTextScale.entries.forEachIndexed { index, scale ->
                    SegmentedButton(
                        selected = scale == state.textScale,
                        onClick = { send(Action.TextScaleChanged(scale)) },
                        shape = SegmentedButtonDefaults.itemShape(index, ReaderTextScale.entries.size),
                    ) { Text(stringResource(scale.titleRes)) }
                }
            }
            val size = VerbumTypography.scripture.fontSize * state.textScale.factor
            Text(
                stringResource(R.string.preview_sentence),
                style = VerbumTypography.scripture.copy(fontSize = size, lineHeight = size * 1.65f),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.heightIn(min = 96.dp),
            )
        }
    }
}
