package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.GuidedExplorationFeature
import com.nexussoft.verbum.feature.scripture.GuidedExplorationFeature.Action
import com.nexussoft.verbum.feature.scripture.GuidedExplorationFeature.DelegateAction
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.models.ArrivalFeeling

@Composable
internal fun GuidedExplorationPane(state: GuidedExplorationFeature.State, onBack: () -> Unit, send: (Action) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(Spacing.readingMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            item {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(state.feeling?.let { feelingTitle(it) } ?: stringResource(R.string.arrival_title),
                    style = VerbumTypography.editorialTitle, modifier = Modifier.semantics { heading() })
            }
            if (state.feeling == null) {
                item { Text(stringResource(R.string.arrival_privacy)) }
                items(ArrivalFeeling.entries, key = { it.name }) { feeling ->
                    TextButton(onClick = { send(Action.Select(feeling)) }, modifier = Modifier.fillMaxWidth()) {
                        Text(feelingTitle(feeling), modifier = Modifier.fillMaxWidth())
                    }
                }
            } else {
                item { Text(stringResource(R.string.arrival_intent), style = VerbumTypography.editorialHeadline) }
                if (state.isLoading) item { CircularProgressIndicator() }
                state.plan?.let { plan ->
                    if (plan.isEditorialPreview) item { Text(stringResource(R.string.arrival_preview), style = MaterialTheme.typography.bodySmall) }
                    item { Text(plan.guidingQuestion, style = VerbumTypography.scripture) }
                    items(plan.passages) { reference ->
                        Column {
                            Text(reference.formatted, style = VerbumTypography.editorialHeadline)
                            TextButton(onClick = { send(Action.Delegate(DelegateAction.OpenPassage(reference))) }) { Text(stringResource(R.string.arrival_read)) }
                            TextButton(onClick = { send(Action.Delegate(DelegateAction.OpenContext(reference))) }) { Text(stringResource(R.string.context_title)) }
                        }
                    }
                    item { Text(stringResource(R.string.arrival_sources), style = MaterialTheme.typography.bodySmall) }
                }
                if (state.failed) item {
                    Text(stringResource(R.string.err_page))
                    TextButton(onClick = { send(Action.Select(state.feeling)) }) { Text(stringResource(R.string.try_again)) }
                }
                item { TextButton(onClick = { send(Action.ChangeFeeling) }) { Text(stringResource(R.string.arrival_change)) } }
            }
        }
    }
}

@Composable
private fun feelingTitle(feeling: ArrivalFeeling): String = stringResource(when (feeling) {
    ArrivalFeeling.ANXIOUS -> R.string.arrival_anxious
    ArrivalFeeling.LOST -> R.string.arrival_lost
    ArrivalFeeling.GRATEFUL -> R.string.arrival_grateful
    ArrivalFeeling.TIRED -> R.string.arrival_tired
    ArrivalFeeling.AFRAID -> R.string.arrival_afraid
    ArrivalFeeling.ALONE -> R.string.arrival_alone
    ArrivalFeeling.ANGRY -> R.string.arrival_angry
    ArrivalFeeling.HOPELESS -> R.string.arrival_hopeless
    ArrivalFeeling.PEACEFUL -> R.string.arrival_peaceful
})
