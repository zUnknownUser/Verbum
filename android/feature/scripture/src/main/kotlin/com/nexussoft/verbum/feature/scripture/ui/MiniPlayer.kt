package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.designsystem.tokens.Elevation
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.AudioPlayerFeature
import com.nexussoft.verbum.feature.scripture.AudioPlayerFeature.Action
import com.nexussoft.verbum.feature.scripture.R

/** Sits above the navigation bar so it follows the listener everywhere. Tap the title to go back to the chapter. */
@Composable
internal fun MiniPlayer(state: AudioPlayerFeature.State, send: (Action) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = Elevation.Card.dp, shadowElevation = Elevation.Card.dp) {
        Column {
            Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outlineVariant)) {
                Box(Modifier.fillMaxWidth(state.progress).height(2.dp).background(MaterialTheme.colorScheme.primary))
            }
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Column(Modifier.weight(1f).clickable { send(Action.ChapterTapped) }) {
                    Text(state.reference?.formatted ?: "", style = VerbumTypography.navigationSerif, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    Text(subtitle(state), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton(enabled = !state.isLoading && !state.failed, onClick = { send(Action.SkipBackward) }) { Icon(Icons.Filled.Replay10, contentDescription = stringResource(R.string.audio_back)) }
                IconButton(enabled = !state.isLoading, onClick = {
                    if (state.failed) state.reference?.let { send(Action.Play(it)) } else send(Action.TogglePlayPause)
                }) {
                    if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                    else Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = stringResource(if (state.isPlaying) R.string.audio_pause else R.string.audio_play))
                }
                IconButton(enabled = !state.isLoading && !state.failed, onClick = { send(Action.SkipForward) }) { Icon(Icons.Filled.Forward10, contentDescription = stringResource(R.string.audio_forward)) }
                IconButton(onClick = { send(Action.StopTapped) }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.audio_stop)) }
            }
        }
    }
}

@Composable
private fun subtitle(state: AudioPlayerFeature.State): String {
    if (state.failed) return stringResource(R.string.audio_none)
    val time = "${format(state.currentTime)} / ${format(state.duration)}"
    val audio = state.audio
    val narrator = state.narrator
    val language = stringResource(R.string.audio_in_english)
    return if (audio != null && narrator != null) "$language · ${audio.translationId} · ${narrator.name} · $time" else language
}

private fun format(seconds: Double): String {
    val total = seconds.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}
