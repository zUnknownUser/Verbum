package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.clients.VoiceException
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.feature.scripture.VoiceFeature
import com.nexussoft.verbum.feature.scripture.VoiceFeature.Action
import com.nexussoft.verbum.feature.scripture.VoiceFeature.Phase

/**
 * The conversation sheet: what it is about, what is being said, the passages it rests on, and
 * two controls — mute and end. Calm on purpose: no waveform theatre, the state is a line of
 * text. Twin of iOS `VoiceView`. Hosted in a `ModalBottomSheet` by the app shell.
 */
@Composable
internal fun VoicePane(state: VoiceFeature.State, onOpenSettings: () -> Unit, send: (Action) -> Unit) {
    LaunchedEffect(state.context) { send(Action.Started) }
    Column(Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 640.dp)) {
        Column(Modifier.padding(horizontal = Spacing.readingMargin, vertical = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.voice_talking_about), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.primary)
            Text(state.context.title, style = VerbumTypography.editorialHeadline, maxLines = 2)
            Text(status(state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val phase = state.phase
        if (phase is Phase.Failed) {
            Failure(phase.error, onOpenSettings, send)
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(state.lines.size, state.partial) {
                val count = state.lines.size + if (state.partial.isNotEmpty()) 1 else 0
                if (count > 0) listState.animateScrollToItem(count - 1)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = true),
                contentPadding = PaddingValues(horizontal = Spacing.readingMargin, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                if (state.lines.isEmpty() && state.partial.isEmpty()) {
                    item { Text(stringResource(R.string.voice_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(state.lines, key = { it.id }) { LineView(it) }
                if (state.partial.isNotEmpty()) {
                    item(key = "partial") { LineView(VoiceFeature.Line(-1, VoiceFeature.Line.Role.COMPANION, state.partial)) }
                }
            }
            if (state.passages.isNotEmpty()) {
                Column(Modifier.padding(horizontal = Spacing.readingMargin).padding(bottom = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.voice_passages), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        for (reference in state.passages) {
                            Text(
                                reference.formatted,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraLarge)
                                    .clickable { send(Action.PassageTapped(reference)) }
                                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            val live = phase == Phase.Listening || phase == Phase.Speaking || phase == Phase.Thinking
            TextButton(onClick = { send(Action.MuteToggled) }, enabled = live) {
                Text(stringResource(if (state.isMuted) R.string.voice_unmute else R.string.voice_mute))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { send(Action.EndTapped) }) {
                Text(stringResource(if (phase == Phase.Ended || phase is Phase.Failed) R.string.voice_close else R.string.voice_end))
            }
        }
    }
}

@Composable
private fun status(state: VoiceFeature.State): String = stringResource(
    when (state.phase) {
        Phase.Idle, Phase.Connecting -> R.string.voice_connecting
        Phase.Listening -> if (state.isMuted) R.string.voice_muted else if (state.isUserSpeaking) R.string.voice_hearing else R.string.voice_listening
        Phase.Speaking -> R.string.voice_speaking
        Phase.Thinking -> R.string.voice_thinking
        Phase.Ended -> R.string.voice_ended
        is Phase.Failed -> R.string.voice_not_connected
    },
)

@Composable
private fun LineView(line: VoiceFeature.Line) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            stringResource(if (line.role == VoiceFeature.Line.Role.USER) R.string.voice_you else R.string.voice_companion),
            style = VerbumTypography.overline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            line.text,
            style = if (line.role == VoiceFeature.Line.Role.COMPANION) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif) else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Failure(error: VoiceException, onOpenSettings: () -> Unit, send: (Action) -> Unit) {
    Column(Modifier.weight(1f, fill = true).padding(horizontal = Spacing.readingMargin, vertical = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when (error) {
            VoiceException.Unavailable -> {
                Text(stringResource(R.string.voice_unavailable), style = VerbumTypography.editorialHeadline)
                Text(stringResource(R.string.voice_unavailable_body), style = MaterialTheme.typography.bodyMedium)
            }
            VoiceException.MicrophoneDenied -> {
                Text(stringResource(R.string.voice_mic_denied), style = VerbumTypography.editorialHeadline)
                Text(stringResource(R.string.voice_mic_denied_body), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.voice_open_settings)) }
            }
            VoiceException.NetworkUnavailable -> {
                Text(stringResource(R.string.ask_offline), style = VerbumTypography.editorialHeadline)
                Text(stringResource(R.string.voice_offline_body), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { send(Action.RetryTapped) }) { Text(stringResource(R.string.try_again)) }
            }
            VoiceException.Failed -> {
                Text(stringResource(R.string.voice_dropped), style = VerbumTypography.editorialHeadline)
                TextButton(onClick = { send(Action.RetryTapped) }) { Text(stringResource(R.string.try_again)) }
            }
        }
    }
}
