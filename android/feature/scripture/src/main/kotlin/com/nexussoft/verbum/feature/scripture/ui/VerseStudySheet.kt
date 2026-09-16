package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.clients.helloao.HelloAOTranslation
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.feature.scripture.VerseStudyFeature
import com.nexussoft.verbum.feature.scripture.VerseStudyFeature.Action
import com.nexussoft.verbum.feature.scripture.VerseStudyFeature.Tab
import com.nexussoft.verbum.models.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VerseStudySheet(state: VerseStudyFeature.State, send: (Action)->Unit,onDismiss:()->Unit) {
    val canDismiss=!state.saving && state.annotation.note==state.savedNote
    val currentDismiss by rememberUpdatedState(canDismiss)
    val sheet=rememberModalBottomSheetState(confirmValueChange={it!=SheetValue.Hidden || currentDismiss})
    LaunchedEffect(state.reference) {send(Action.Started)}
    ModalBottomSheet(onDismissRequest={if(canDismiss) onDismiss()},sheetState=sheet,
        containerColor=MaterialTheme.colorScheme.surface.copy(alpha=0.97f), tonalElevation=0.dp) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=Spacing.lg),verticalAlignment=Alignment.CenterVertically) {
                if(state.ask!=null) TextButton(onClick={send(Action.CloseAnswer)}) {Text(stringResource(R.string.reader_study))}
                Text(state.reference.formatted,style=VerbumTypography.navigationSerif,modifier=Modifier.weight(1f))
                IconButton(onClick={send(Action.BookmarkToggled)},enabled=!state.saving) {
                    Icon(if(state.annotation.bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,contentDescription=accountText(if(state.annotation.bookmarked) "removeBookmark" else "savePassage"))
                }
                TextButton(onClick={send(Action.Done)},enabled=!state.saving) {Text(stringResource(R.string.reader_done))}
            }
            if(state.ask!=null) {
                AskPane(state.ask,onBack={send(Action.CloseAnswer)},embedded=true) {send(Action.Ask(it))}
            } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=Spacing.lg).padding(bottom=Spacing.xxl),verticalArrangement=Arrangement.spacedBy(Spacing.lg)) {
                Text(state.text,style=VerbumTypography.scripture,maxLines=4)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(Spacing.sm)) {
                    Tab.entries.forEach {tab->
                        TextButton(onClick={send(Action.TabChanged(tab))},modifier=Modifier.semantics {selected=tab==state.tab}) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                Icon(when(tab) {Tab.HIGHLIGHT->Icons.Outlined.BorderColor;Tab.NOTE->Icons.Outlined.EditNote;Tab.COMPARE->Icons.Outlined.CompareArrows;Tab.CONTEXT->Icons.Outlined.MenuBook;Tab.REFERENCES->Icons.Outlined.AccountTree;Tab.ASK->Icons.Outlined.QuestionAnswer},contentDescription=null)
                                Text(stringResource(tabTitle(tab)),style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                HorizontalDivider()
                when(state.tab) {
                    Tab.HIGHLIGHT -> {
                        Text(stringResource(R.string.reader_mark),style=VerbumTypography.editorialHeadline)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(Spacing.sm)) {
                            HighlightStyle.entries.forEach {style->
                                FilterChip(selected=(state.annotation.highlightStyle ?: HighlightStyle.BACKGROUND)==style,onClick={send(Action.HighlightStyleChanged(style))},enabled=!state.saving,label={Text(stringResource(when(style) {HighlightStyle.BACKGROUND->R.string.highlight_soft;HighlightStyle.UNDERLINE->R.string.highlight_underline;HighlightStyle.MARGIN->R.string.highlight_margin}))})
                            }
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(Spacing.lg),verticalAlignment=Alignment.CenterVertically) {
                            HighlightColor.entries.forEach {color->
                                val label=stringResource(when(color) {HighlightColor.GOLD->R.string.reader_gold;HighlightColor.SAGE->R.string.reader_sage;HighlightColor.ROSE->R.string.reader_rose})
                                IconButton(onClick={send(Action.HighlightChanged(if(state.annotation.highlight==color) null else color))},enabled=!state.saving,
                                    modifier=Modifier.background(highlightColor(color).copy(alpha=0.35f),CircleShape).semantics {contentDescription=label;selected=state.annotation.highlight==color}) {
                                    if(state.annotation.highlight==color) Icon(Icons.Outlined.Check,contentDescription=null)
                                }
                            }
                            if(state.annotation.highlight!=null) TextButton(onClick={send(Action.HighlightChanged(null))}) {Text(stringResource(R.string.reader_remove))}
                        }
                        SaveStatus(state)
                        Text(stringResource(R.string.reader_local),style=MaterialTheme.typography.bodySmall)
                    }
                    Tab.NOTE -> {
                        Text(stringResource(R.string.reader_your_note),style=VerbumTypography.editorialHeadline)
                        OutlinedTextField(value=state.annotation.note,onValueChange={send(Action.NoteChanged(it))},modifier=Modifier.fillMaxWidth().heightIn(min=140.dp),label={Text(stringResource(R.string.reader_your_note))})
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            Text(stringResource(R.string.reader_only_you),style=MaterialTheme.typography.bodySmall)
                            TextButton(onClick={send(Action.Save)},enabled=!state.saving) {Text(stringResource(R.string.reader_save_note))}
                        }
                        SaveStatus(state)
                    }
                    Tab.COMPARE -> {
                        Text(HelloAOTranslation.name(state.translationId),style=VerbumTypography.editorialHeadline)
                        Text(state.text,style=VerbumTypography.scripture)
                        HorizontalDivider()
                        if(state.comparisonLoading) CircularProgressIndicator()
                        else if(state.comparison!=null) {
                            val compared=state.comparison
                            Text(if(compared.translationId=="por_bsl") "Bíblia Portuguesa Mundial" else "World English Bible",style=VerbumTypography.editorialHeadline)
                            Text(compared.text,style=VerbumTypography.scripture)
                            Text(stringResource(if(compared.translationId=="por_bsl") R.string.reader_translation_draft else R.string.reader_public_domain),style=MaterialTheme.typography.bodySmall)
                            SourceLink(if(compared.translationId=="por_bsl") "https://ebible.org/Scriptures/details.php?id=porbrbsl" else "https://worldenglish.bible")
                        } else LayerRetry {send(Action.TabChanged(Tab.COMPARE))}
                    }
                    Tab.CONTEXT -> {
                        if(state.entityLoading) CircularProgressIndicator()
                        else if(state.entity!=null) {
                            val detail=state.entity
                            TextButton(onClick={send(Action.CloseEntity)}) {Text(stringResource(R.string.context_title))}
                            Text(detail.entity.name,style=VerbumTypography.editorialTitle)
                            detail.entity.summary?.let {Text(it,style=VerbumTypography.scripture)}
                            detail.role?.let {Text(it)}
                            detail.approximateDates?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
                            detail.keyPassages.forEach { PassageLink(it,send) }
                            StudySources(detail.sources)
                        } else if(state.candidates.isNotEmpty()) {
                            Text(stringResource(R.string.reader_people_places),style=VerbumTypography.editorialHeadline)
                            state.candidates.forEach {StudyEntity(it,send)}
                        } else {
                            Text(stringResource(R.string.context_title),style=VerbumTypography.editorialHeadline)
                            Text(stringResource(R.string.reader_chapter_scope),style=MaterialTheme.typography.bodySmall)
                            if(state.contextLoading) CircularProgressIndicator()
                            else if(state.context!=null) {
                                state.context.entities.take(24).forEach {StudyEntity(it,send)}
                                StudySources(state.context.sources)
                            } else if(state.contextFailed || state.entityFailed) LayerRetry {send(Action.TabChanged(Tab.CONTEXT))}
                            else Text(stringResource(R.string.reader_no_context))
                        }
                    }
                    Tab.REFERENCES -> {
                        Text(stringResource(R.string.reader_follow_refs),style=VerbumTypography.editorialHeadline)
                        if(state.contextLoading) CircularProgressIndicator()
                        else if(state.context?.relatedPassages?.isNotEmpty()==true) {
                            Text(stringResource(R.string.reader_reference_scope),style=MaterialTheme.typography.bodySmall)
                            state.context.relatedPassages.forEach {PassageLink(it,send)}
                            StudySources(state.context.sources)
                        } else if(state.contextFailed) LayerRetry {send(Action.TabChanged(Tab.REFERENCES))}
                        else Text(stringResource(R.string.reader_no_references))
                    }
                    Tab.ASK -> {
                        Text(stringResource(R.string.reader_investigate),style=VerbumTypography.editorialHeadline)
                        OutlinedTextField(value=state.question,onValueChange={send(Action.QuestionChanged(it))},label={Text(stringResource(R.string.reader_question))},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=5)
                        Text(stringResource(R.string.reader_ask_explainer),style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={send(Action.Submit)},enabled=state.question.isNotBlank()) {Text(stringResource(R.string.reader_ask_passage))}
                    }
                }
            }
        }
    }
}
@Composable private fun SaveStatus(state:VerseStudyFeature.State) {
    when {state.saving->CircularProgressIndicator();state.saveFailed->Text(stringResource(R.string.reader_save_failed),color=MaterialTheme.colorScheme.error);state.saved->Text(stringResource(R.string.reader_saved),style=MaterialTheme.typography.bodySmall)}
}
@Composable private fun LayerRetry(retry:()->Unit) {
    Text(stringResource(R.string.reader_layer_failed));TextButton(onClick=retry) {Text(stringResource(R.string.try_again))}
}
@Composable private fun StudyEntity(entity:BibleEntity,send:(Action)->Unit) {
    TextButton(onClick={send(Action.EntityTapped(entity))}) {
        Column(Modifier.fillMaxWidth()) {
            Text(entity.name,style=VerbumTypography.editorialHeadline)
            entity.summary?.let {Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}
@Composable private fun PassageLink(reference:PassageReference,send:(Action)->Unit) {
    TextButton(onClick={send(Action.PassageTapped(reference))}) {Text(reference.formatted,style=VerbumTypography.navigationSerif)}
}
@Composable private fun StudySources(sources:List<SourceReference>) {
    var expanded by remember {mutableStateOf(false)}
    TextButton(onClick={expanded=!expanded}) {Text(stringResource(R.string.sources))}
    if(expanded) sources.forEach {source->
        Text(source.citation,style=MaterialTheme.typography.bodySmall)
        source.url?.takeIf {it.startsWith("https://")}?.let {SourceLink(it)}
    }
}
@Composable private fun SourceLink(url:String) {
    val handler=LocalUriHandler.current
    var failed by remember {mutableStateOf(false)}
    TextButton(onClick={failed=runCatching {handler.openUri(url)}.isFailure}) {Text(stringResource(R.string.context_source))}
    if(failed) Text(stringResource(R.string.reader_layer_failed))
}
private fun tabTitle(tab:Tab):Int=when(tab) {
    Tab.HIGHLIGHT->R.string.reader_highlight;Tab.NOTE->R.string.reader_note;Tab.COMPARE->R.string.reader_compare;Tab.CONTEXT->R.string.reader_context;Tab.REFERENCES->R.string.reader_references;Tab.ASK->R.string.reader_ask
}
internal fun highlightColor(color:HighlightColor):Color=when(color) {HighlightColor.GOLD->Color(0xFFA88247);HighlightColor.SAGE->Color(0xFF668770);HighlightColor.ROSE->Color(0xFFA66E75)}
