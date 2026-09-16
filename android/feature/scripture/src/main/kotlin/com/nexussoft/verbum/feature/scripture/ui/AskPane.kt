package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import com.nexussoft.verbum.clients.AskScriptureException
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.AskFeature
import com.nexussoft.verbum.feature.scripture.AskFeature.Action
import com.nexussoft.verbum.feature.scripture.AskFeature.Content
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.ScriptureAnswer

/**
 * The §13.2 shape on paper: short answer, the answer, key passages, explore further,
 * perspectives (only when they differ), sources. Nothing is a revelation and nothing is certain
 * beyond what the server says it is. Twin of iOS `AskView`.
 */
@Composable
internal fun AskPane(state: AskFeature.State, onBack: () -> Unit, embedded: Boolean = false, send: (Action) -> Unit) {
    LaunchedEffect(state.question) { send(Action.Started) }
    val uriHandler = LocalUriHandler.current
    var sourceFailed by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(Spacing.readingMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                AskHeading(stringResource(R.string.ask_scripture))
                Text(state.question, style = VerbumTypography.editorialTitle)
            }
            when (val content = state.content) {
                Content.Idle, Content.Asking -> item {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.ask_loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is Content.Failed -> item { Failure(content.error, send, embedded) }
                is Content.Answered -> if (content.page.answer.isEmpty) noAnswer(content.page.answer, send, embedded) else answered(content.page, send, { address ->
                    sourceFailed = runCatching { uriHandler.openUri(address) }.isFailure
                }, sourceFailed, embedded)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.answered(page: AskFeature.Page, send: (Action) -> Unit, openSource: (String) -> Unit, sourceFailed: Boolean, embedded: Boolean) {
    val answer = page.answer
    item {
        AskHeading(stringResource(R.string.ask_short_answer))
        Text(answer.summary, style = VerbumTypography.editorialHeadline)
    }
    item {
        Text(answer.answer, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif))
        // §31: confidence is shown, never implied.
        Text(
            stringResource(
                when (answer.confidence) {
                    ScriptureAnswer.Confidence.HIGH -> R.string.ask_confidence_high
                    ScriptureAnswer.Confidence.MEDIUM -> R.string.ask_confidence_medium
                    ScriptureAnswer.Confidence.LOW -> R.string.ask_confidence_low
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!embedded) TextButton(onClick = { send(Action.TalkTapped) }) { Text(stringResource(R.string.voice_go_on)) }
    }
    if (answer.passageReferences.isNotEmpty()) {
        item { AskHeading(stringResource(R.string.key_passages)) }
        items(answer.passageReferences) { AskPassage(it, send) }
    }
    if (page.entities.isNotEmpty()) {
        item { AskHeading(stringResource(R.string.ask_explore_further)) }
        items(page.entities, key = { it.id }) { entity ->
            TextButton(onClick = { send(Action.EntityTapped(entity)) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(entity.name, style = VerbumTypography.editorialHeadline)
                    entity.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
    if (answer.interpretiveVariance) {
        item {
            AskHeading(stringResource(R.string.ask_perspectives))
            Text(stringResource(R.string.ask_perspectives_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
    item { AskHeading(stringResource(R.string.sources)) }
    items(answer.sourceReferences, key = { it.id }) { source ->
        Column {
            Text(source.citation, style = MaterialTheme.typography.bodySmall)
            val address = source.url
            if (address?.startsWith("https://") == true) {
                TextButton(onClick = { openSource(address) }) { Text(stringResource(R.string.context_source)) }
            }
        }
    }
    if (sourceFailed) item { Text(stringResource(R.string.context_source_failed)) }
    item { Text(stringResource(R.string.ask_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** Nothing to stand behind (§51). */
private fun androidx.compose.foundation.lazy.LazyListScope.noAnswer(answer: ScriptureAnswer, send: (Action) -> Unit, embedded: Boolean) {
    item {
        val fallback = answer.fallback
        if (fallback != null) { UsageNotice(fallback) } else {
        Text(stringResource(R.string.ask_no_answer), style = VerbumTypography.editorialHeadline)
        Text(
            stringResource(if (answer.passageReferences.isEmpty()) R.string.ask_no_answer_body else R.string.ask_no_answer_body_closest),
            style = MaterialTheme.typography.bodyMedium,
        )
        }
        if (!embedded) TextButton(onClick = { send(Action.SearchInsteadTapped) }) { Text(stringResource(R.string.ask_see_search)) }
    }
    if (answer.passageReferences.isNotEmpty()) {
        item { AskHeading(stringResource(R.string.ask_closest_passages)) }
        items(answer.passageReferences) { AskPassage(it, send) }
    }
}

/** Failed (§52): the state is named; unavailable is not retryable, the rest is. */
@Composable
private fun Failure(error: AskScriptureException, send: (Action) -> Unit, embedded: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when (error) {
            is AskScriptureException.Limited -> UsageNotice(error.restriction)
            AskScriptureException.Unavailable -> {
                Text(stringResource(R.string.ask_unavailable), style = VerbumTypography.editorialHeadline)
                Text(stringResource(R.string.ask_unavailable_body), style = MaterialTheme.typography.bodyMedium)
            }
            AskScriptureException.NetworkUnavailable -> {
                Text(stringResource(R.string.ask_offline), style = VerbumTypography.editorialHeadline)
                Text(stringResource(R.string.ask_offline_body), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { send(Action.RetryTapped) }) { Text(stringResource(R.string.try_again)) }
            }
            AskScriptureException.Failed -> {
                Text(stringResource(R.string.ask_failed), style = VerbumTypography.editorialHeadline)
                TextButton(onClick = { send(Action.RetryTapped) }) { Text(stringResource(R.string.try_again)) }
            }
        }
        if (!embedded) TextButton(onClick = { send(Action.SearchInsteadTapped) }) { Text(stringResource(R.string.ask_see_search)) }
    }
}

@Composable
private fun AskHeading(title: String) {
    Text(title, style = VerbumTypography.overline, modifier = Modifier.semantics { heading() })
}

@Composable
private fun AskPassage(reference: PassageReference, send: (Action) -> Unit) {
    TextButton(onClick = { send(Action.PassageTapped(reference)) }) { Text(reference.formatted) }
}
