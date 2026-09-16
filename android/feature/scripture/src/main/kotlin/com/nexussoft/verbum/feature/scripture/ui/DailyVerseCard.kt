package com.nexussoft.verbum.feature.scripture.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.clients.NotificationAuthorization
import com.nexussoft.verbum.designsystem.tokens.Radius
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.DailyVerseFeature
import com.nexussoft.verbum.feature.scripture.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The verse of the day as a card on Home: the text in the reading face, its reference,
 * open + share, and the morning toggle underneath. Twin of iOS `DailyVerseView`.
 */
@Composable
internal fun DailyVerseCard(state: DailyVerseFeature.State, send: (DailyVerseFeature.Action) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { send(DailyVerseFeature.Action.Started) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(Radius.lg))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.lg))
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        when (val text = state.text) {
            DailyVerseFeature.TextState.Loading -> Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            is DailyVerseFeature.TextState.Loaded -> Text(
                text.passage.text,
                style = VerbumTypography.scripture,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { contentDescription = "${context.getString(R.string.verse_of_the_day)}. ${text.passage.text}" },
            )
            DailyVerseFeature.TextState.Failed -> Text(
                stringResource(R.string.daily_verse_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clickable(onClickLabel = stringResource(R.string.open_reference, state.reference.formatted)) { send(DailyVerseFeature.Action.OpenTapped) }
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    state.reference.formatted,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            state.shareText?.let { shareText ->
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, state.reference.formatted)
                        .putExtra(Intent.EXTRA_TEXT, shareText)
                    context.startActivity(Intent.createChooser(send, null))
                }) {
                    Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.share_verse), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ReminderControls(state, send)
            if (state.morningsEnabled && state.authorization == NotificationAuthorization.DENIED) {
                Text(
                    stringResource(R.string.notifications_off_open_settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }
        }
    }
}

/** Shared by Home and Profile so both surfaces edit the same local reminder. */
@Composable
internal fun ReminderControls(state: DailyVerseFeature.State, send: (DailyVerseFeature.Action) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Icon(Icons.Outlined.WbTwilight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.daily_verse_reminder), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = state.morningsEnabled, onCheckedChange = { send(DailyVerseFeature.Action.MorningsToggled(it)) })
    }
    if (state.morningsEnabled) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reminder_time), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            val time = LocalTime.of(state.reminderMinute / 60, state.reminderMinute % 60)
            TextButton(onClick = {
                TimePickerDialog(context, { _, hour, minute ->
                    send(DailyVerseFeature.Action.ReminderTimeChanged(hour * 60 + minute))
                }, time.hour, time.minute, android.text.format.DateFormat.is24HourFormat(context)).show()
            }) {
                Text(time.format(DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)))
            }
        }
    }
}
