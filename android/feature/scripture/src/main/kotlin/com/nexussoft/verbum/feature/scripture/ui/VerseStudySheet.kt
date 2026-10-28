package com.nexussoft.verbum.feature.scripture.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
    val studyStates=rememberSaveableStateHolder()
    val canDismiss=!state.saving && state.annotation.note==state.savedNote
    val currentDismiss by rememberUpdatedState(canDismiss)
    val sheet=rememberModalBottomSheetState(skipPartiallyExpanded=state.chapterGuide,confirmValueChange={it!=SheetValue.Hidden || currentDismiss})
    LaunchedEffect(state.reference) {send(Action.Started)}
    ModalBottomSheet(onDismissRequest={if(canDismiss) onDismiss()},sheetState=sheet,
        containerColor=MaterialTheme.colorScheme.surface.copy(alpha=0.97f), tonalElevation=0.dp) {
        BackHandler(enabled=state.previewReference!=null || (state.tab==Tab.CONTEXT && state.entityHistory.isNotEmpty())) {
            send(if(state.previewReference!=null) Action.ClosePreview else Action.CloseEntity)
        }
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=Spacing.lg),verticalAlignment=Alignment.CenterVertically) {
                if(state.ask!=null) TextButton(onClick={send(Action.CloseAnswer)}) {Text(stringResource(R.string.reader_study))}
                Text(state.reference.formatted,style=VerbumTypography.navigationSerif,modifier=Modifier.weight(1f))
                if(!state.chapterGuide && state.previewReference==null) IconButton(onClick={send(Action.BookmarkToggled)},enabled=!state.saving) {
                    Icon(if(state.annotation.bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,contentDescription=accountText(if(state.annotation.bookmarked) "removeBookmark" else "savePassage"))
                }
                TextButton(onClick={send(Action.Done)},enabled=!state.saving) {Text(stringResource(R.string.reader_done))}
            }
            if(state.previewReference!=null) {
                PassagePreviewContent(state.previewReference,state.preview,state.previewLoading,state.previewFailed,
                    onClose={send(Action.ClosePreview)},onRetry={send(Action.RetryPreview)},onOpenInReader={send(Action.OpenPreviewInReader)},
                    modifier=Modifier.weight(1f),canOpen=!state.saving) {
                    if(state.saveFailed) SaveStatus(state)
                }
            } else if(state.ask!=null) {
                AskPane(state.ask,onBack={send(Action.CloseAnswer)},embedded=true) {send(Action.Ask(it))}
            } else studyStates.SaveableStateProvider(state.selectedEntity?.id ?: "chapter-study") {
              Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=Spacing.lg).padding(bottom=Spacing.xxl),verticalArrangement=Arrangement.spacedBy(Spacing.lg)) {
                if(state.text.isNotEmpty()) Text(state.text,style=VerbumTypography.scripture,maxLines=4)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(Spacing.sm)) {
                    state.availableTabs.forEach {tab->
                        TextButton(onClick={send(Action.TabChanged(tab))},modifier=Modifier.semantics {selected=tab==state.tab}) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                Icon(when(tab) {Tab.HIGHLIGHT->Icons.Outlined.BorderColor;Tab.NOTE->Icons.Outlined.EditNote;Tab.COMPARE->Icons.AutoMirrored.Outlined.CompareArrows;Tab.CONTEXT->Icons.AutoMirrored.Outlined.MenuBook;Tab.REFERENCES->Icons.Outlined.AccountTree;Tab.ASK->Icons.Outlined.QuestionAnswer},contentDescription=null)
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
                        if(state.selectedEntity!=null) {
                            TextButton(onClick={send(Action.CloseEntity)}) {
                                Text(state.entityHistory.lastOrNull()?.detail?.entity?.name ?: stringResource(R.string.context_title))
                            }
                            if(state.entityLoading) CircularProgressIndicator()
                            else if(state.entityFailed) LayerRetry {send(Action.RetryEntity)}
                            else state.entity?.let {detail->
                                Text(detail.entity.name,style=VerbumTypography.editorialTitle)
                                detail.entity.summary?.let {Text(it,style=VerbumTypography.scripture)}
                                detail.role?.let {Text(it)}
                                detail.approximateDates?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
                                detail.themeStudy?.let {study->
                                    ThemeStudyContent(detail.entity.id,study,detail.sources,
                                        openPassage={send(Action.PreviewPassage(it))},openEntity={send(Action.ConnectedEntityTapped(it))})
                                    if(state.keyPassages.isNotEmpty()) Text(stringResource(R.string.theme_study_more_readings),style=VerbumTypography.editorialHeadline)
                                }
                                state.keyPassages.take(state.entityPassageLimit).forEach {PassageLink(it,send)}
                                if(state.keyPassages.size>state.entityPassageLimit) TextButton(onClick={send(Action.MoreEntityPassages)}) {Text(stringResource(R.string.history_more))}
                                val otherSources=detail.sources.filter {it.id !in detail.themeStudy?.sourceReferenceIds.orEmpty()}
                                if(otherSources.isNotEmpty()) StudySources(otherSources)
                            }
                        } else {
                            Text(stringResource(R.string.context_title),style=VerbumTypography.editorialHeadline)
                            Text(stringResource(if(state.chapterGuide) R.string.reader_guide_scope else R.string.reader_chapter_scope),style=MaterialTheme.typography.bodySmall)
                            if(state.contextLoading) CircularProgressIndicator()
                            else if(state.context != null || state.contextEntities.isNotEmpty()) {
                                state.context?.let {context->
                                    context.bookOrientation?.let { orientation ->
                                        BookOrientationContent(orientation, context.reference.bookId, context.sources) { send(Action.PreviewPassage(it)) }
                                    }
                                    context.study?.let {study->
                                        ChapterStudyContent(study,context.sources) {send(Action.PreviewPassage(it))}
                                    }
                                }
                                if(state.contextEntities.isNotEmpty()) {
                                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(Spacing.sm)) {
                                        state.entityTypes.forEach {type->
                                            FilterChip(selected=state.activeType==type,onClick={send(Action.EntityTypeChanged(type))},label={Text("${stringResource(studyTypeTitle(type))} (${state.contextEntities.count {it.type==type}})")})
                                        }
                                    }
                                    state.visibleEntities.forEach {StudyEntity(it,send)}
                                    if(state.matchingEntities.size>state.entityLimit) TextButton(onClick={send(Action.MoreEntities)}) {Text(stringResource(R.string.history_more))}
                                } else if(state.context?.study==null && state.context?.bookOrientation==null) Text(stringResource(R.string.reader_no_context))
                                state.context?.let {StudySources(it.sources)}
                            } else if(state.contextFailed) LayerRetry {send(Action.TabChanged(Tab.CONTEXT))}
                            else Text(stringResource(R.string.reader_no_context))
                        }
                    }
                    Tab.REFERENCES -> {
                        Text(stringResource(R.string.reader_follow_refs),style=VerbumTypography.editorialHeadline)
                        if(state.contextLoading) CircularProgressIndicator()
                        else if(state.relatedPassages.isNotEmpty()) {
                            Text(stringResource(R.string.reader_reference_scope),style=MaterialTheme.typography.bodySmall)
                            state.relatedPassages.take(state.referenceLimit).forEach {PassageLink(it,send)}
                            if(state.relatedPassages.size>state.referenceLimit) TextButton(onClick={send(Action.MoreReferences)}) {Text(stringResource(R.string.history_more))}
                            state.context?.let {StudySources(it.sources)}
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
    TextButton(onClick={send(Action.PreviewPassage(reference))}) {Text(reference.formatted,style=VerbumTypography.navigationSerif)}
}
@Composable private fun StudySources(sources:List<SourceReference>) {
    var expanded by remember {mutableStateOf(false)}
    TextButton(onClick={expanded=!expanded}) {Text(stringResource(R.string.sources))}
    if(expanded) sources.forEach {source->
        Text(source.citation,style=MaterialTheme.typography.bodySmall)
        source.webUrl?.let {SourceLink(it)}
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

private fun studyTypeTitle(type:BibleEntityType):Int=when(type) {
    BibleEntityType.PERSON->R.string.people
    BibleEntityType.PLACE->R.string.places
    BibleEntityType.EVENT->R.string.events
    BibleEntityType.THEME->R.string.themes
    BibleEntityType.PASSAGE->R.string.passages
    BibleEntityType.BOOK->R.string.books
    BibleEntityType.PROPHECY->R.string.study_prophecies
    BibleEntityType.ORIGINAL_TERM->R.string.original_terms
    BibleEntityType.HISTORICAL_PERIOD->R.string.study_periods
}
