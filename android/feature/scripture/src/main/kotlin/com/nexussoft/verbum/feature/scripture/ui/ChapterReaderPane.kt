package com.nexussoft.verbum.feature.scripture.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.ChapterNavigation
import com.nexussoft.verbum.feature.scripture.ChapterReaderFeature
import com.nexussoft.verbum.feature.scripture.ChapterReaderFeature.Action
import com.nexussoft.verbum.feature.scripture.ChapterReaderFeature.State
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Text stays on its page; native pager physics never replace it with a fade. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChapterReaderPane(state:State,send:(Action)->Unit,onTitleTapped:()->Unit,onSettingsTapped:()->Unit,showTitleChevron:Boolean) {
    val audioReading=LocalAudioReading.current
    var followsAudio by remember {mutableStateOf(true)}
    var previousAudioReference by remember {mutableStateOf<PassageReference?>(null)}
    LaunchedEffect(audioReading) {
        audioReading?.let {value->
            if(followsAudio && value.isPlaying && state.chapters[ReaderCanon.key(state.reference)]?.firstOrNull()?.translationId==value.translationId && previousAudioReference==state.reference && value.reference!=state.reference && ChapterNavigation.next(state.reference)==value.reference) {
                if(state.readingMode==ReadingMode.CONTINUOUS && (value.reference in state.flow || state.flow.lastOrNull()==state.reference)) {
                    if(value.reference !in state.flow) send(Action.AppendChapter)
                    send(Action.ChapterVisible(value.reference))
                } else send(Action.Go(value.reference))
            }
            if(value.isPlaying) previousAudioReference=value.reference
        }
    }
    LaunchedEffect(state.study!=null) {if(state.study!=null) followsAudio=false}
    val pager=rememberPagerState(initialPage=ReaderCanon.index(state.reference),pageCount={ReaderCanon.chapters.size})
    val currentReference by rememberUpdatedState(state.reference)
    val pagerDragged by pager.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(pagerDragged) {if(pagerDragged) followsAudio=false}
    var menu by remember {mutableStateOf(false)}
    LaunchedEffect(Unit) {send(Action.Started)}
    LaunchedEffect(state.reference, state.chapters[ReaderCanon.key(state.reference)]?.firstOrNull()?.id) {
        if(!state.chapters[ReaderCanon.key(state.reference)].isNullOrEmpty()) send(Action.RecordReading)
    }
    LaunchedEffect(state.reference,state.readingMode) {
        val target=ReaderCanon.index(state.reference)
        if(state.readingMode==ReadingMode.PAGES && pager.currentPage!=target && !pager.isScrollInProgress) pager.animateScrollToPage(target)
    }
    LaunchedEffect(pager,state.readingMode) {
        if(state.readingMode==ReadingMode.PAGES) snapshotFlow {pager.settledPage}.distinctUntilChanged().collect {index->
            if(index!=ReaderCanon.index(currentReference)) send(Action.Go(ReaderCanon.chapters[index]))
        }
    }
    BackHandler(enabled=state.history.isNotEmpty() && state.study==null) {send(Action.BackToReading)}
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(if(state.focusMode) Modifier.clickable {send(Action.FocusToggled)} else Modifier)) {
        if(state.readingMode==ReadingMode.PAGES) HorizontalPager(state=pager,beyondViewportPageCount=1,modifier=Modifier.fillMaxSize(),key={ReaderCanon.key(ReaderCanon.chapters[it])}) {index->
            ReaderPage(state,ReaderCanon.chapters[index],false,send,followsAudio) {followsAudio=false}
        } else ReaderPage(state,state.flow.firstOrNull() ?: state.reference,true,send,followsAudio) {followsAudio=false}
        if(!state.focusMode) TopAppBar(
            title={Row(Modifier.clickable {followsAudio=false;onTitleTapped()},verticalAlignment=Alignment.CenterVertically) {
                Text(state.title,style=VerbumTypography.navigationSerif)
                if(showTitleChevron) Icon(Icons.Outlined.KeyboardArrowDown,contentDescription=stringResource(R.string.choose_book_and_chapter,state.title))
            }},
            actions={
                IconButton(onClick={send(Action.FocusToggled)}) {Icon(Icons.Outlined.VisibilityOff,contentDescription=stringResource(R.string.reader_quiet))}
                Box {
                    IconButton(onClick={menu=true}) {Icon(Icons.Outlined.MoreHoriz,contentDescription=stringResource(R.string.reader_settings))}
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                        DropdownMenuItem(text={Text(stringResource(R.string.listen))},onClick={menu=false;send(Action.ListenTapped)})
                        DropdownMenuItem(text={Text(stringResource(R.string.voice_talk_chapter))},onClick={menu=false;send(Action.TalkTapped)})
                        DropdownMenuItem(text={Text(stringResource(R.string.reader_settings))},onClick={menu=false;onSettingsTapped()})
                    }
                }
            },colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background.copy(alpha=0.96f)),modifier=Modifier.align(Alignment.TopCenter),
        )
        if(!state.focusMode && !followsAudio && audioReading?.isPlaying==true && state.chapters[ReaderCanon.key(state.reference)]?.firstOrNull()?.translationId==audioReading.translationId) Surface(Modifier.align(Alignment.BottomCenter).padding(Spacing.md),shape=RoundedCornerShape(28.dp),tonalElevation=2.dp) {
            TextButton(onClick={followsAudio=true;if(audioReading.reference!=state.reference) send(Action.Go(audioReading.reference))}) {Text(stringResource(R.string.audio_follow_reading))}
        } else if(!state.focusMode) state.history.lastOrNull()?.let {previous->
            Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Spacing.sm),shape=RoundedCornerShape(28.dp),tonalElevation=2.dp) {
                TextButton(onClick={send(Action.BackToReading)}) {Icon(Icons.Outlined.Undo,contentDescription=null);Text(stringResource(R.string.reader_return,previous.reference.formatted))}
            }
        }
    }
    state.study?.let {study->VerseStudySheet(study,{send(Action.Study(it))},{send(Action.StudyDismissed)})}
}

private data class ReaderItem(val key:String,val reference:PassageReference,val verse:BiblePassage?=null,val header:Boolean=false,val footer:Boolean=false)

@Composable private fun ReaderPage(state:State,reference:PassageReference,continuous:Boolean,send:(Action)->Unit,followsAudio:Boolean,onManualScroll:()->Unit) {
    val cacheKey=if(continuous) "flow" else ReaderCanon.key(reference)
    val refs=if(continuous) state.flow else listOf(reference)
    val rows=remember(refs,state.chapters) {refs.flatMap {chapter->
        val key=ReaderCanon.key(chapter)
        listOf(ReaderItem("$key.header",chapter,header=true)) +
            (state.chapters[key]?.map {ReaderItem("${it.bookId}.${it.chapter}.${it.verseStart}",chapter,it)} ?: listOf(ReaderItem("$key.loading",chapter))) +
            listOf(ReaderItem("$key.footer",chapter,footer=true))
    }}
    val previous=state.positions[cacheKey] ?: ChapterReaderFeature.Position()
    val list=rememberLazyListState(initialFirstVisibleItemIndex=previous.index,initialFirstVisibleItemScrollOffset=previous.offset)
    val audioReading=LocalAudioReading.current
    val dragged by list.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) {if(dragged) onManualScroll()}
    var restored by remember {mutableIntStateOf(-1)}
    LaunchedEffect(audioReading,followsAudio,rows.size,restored) {
        val cue=audioReading
        if(restored==state.navigationRevision && followsAudio && cue?.isPlaying==true && (continuous || reference==cue.reference)) {
            val target=rows.indexOfFirst {it.verse?.let {v->cue.contains(v) && v.verseStart==cue.cue.verseStart}==true}
            if(target>=0 && list.layoutInfo.visibleItemsInfo.none {it.index==target}) list.animateScrollToItem(target)
        }
    }
    val latestRows by rememberUpdatedState(rows)
    val latestReference by rememberUpdatedState(state.reference)
    LaunchedEffect(reference) {send(Action.EnsureChapter(reference))}
    LaunchedEffect(state.navigationRevision,state.chapters[ReaderCanon.key(state.reference)]?.size) {
        if((continuous || reference==state.reference) && state.chapters[ReaderCanon.key(state.reference)]!=null && restored!=state.navigationRevision) {
            val requested=state.requestedVerses?.first
            val target=if(requested!=null) rows.indexOfFirst {it.key=="${state.reference.bookId}.${state.reference.chapter}.$requested"}.coerceAtLeast(0) else state.restorePosition?.index ?: previous.index
            val offset=if(requested!=null) 0 else state.restorePosition?.offset ?: previous.offset
            if(rows.isNotEmpty()) list.scrollToItem(target.coerceAtMost(rows.lastIndex),offset)
            restored=state.navigationRevision
        }
    }
    LaunchedEffect(list,cacheKey) {
        snapshotFlow {list.isScrollInProgress}.filter {!it}.collect {
            send(Action.PositionChanged(cacheKey,list.firstVisibleItemIndex,list.firstVisibleItemScrollOffset))
        }
    }
    LaunchedEffect(list,continuous) {
        if(continuous) snapshotFlow {list.firstVisibleItemIndex}.distinctUntilChanged().collect {index->
            latestRows.getOrNull(index)?.reference?.takeIf {it!=latestReference}?.let {send(Action.ChapterVisible(it))}
        }
    }
    DisposableEffect(cacheKey) {onDispose {send(Action.PositionChanged(cacheKey,list.firstVisibleItemIndex,list.firstVisibleItemScrollOffset))}}
    LazyColumn(state=list,modifier=Modifier.fillMaxSize().statusBarsPadding(),contentPadding=PaddingValues(start=Spacing.readingMargin,end=Spacing.readingMargin,top=if(state.focusMode) Spacing.xl else 76.dp,bottom=100.dp)) {
        itemsIndexed(rows,key={_,item->item.key}) {_,item->
            when {
                item.header -> if(!state.focusMode) Column(Modifier.fillMaxWidth().padding(top=Spacing.xxl,bottom=Spacing.xxl).semantics {heading()},horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(BibleBook.book(item.reference.bookId)?.localizedName ?: item.reference.bookId,style=VerbumTypography.overline,color=MaterialTheme.colorScheme.primary)
                    Text(item.reference.chapter.toString(),style=VerbumTypography.chapterNumeral)
                    Spacer(Modifier.height(Spacing.md));HorizontalDivider(Modifier.width(28.dp),color=MaterialTheme.colorScheme.primary)
                }
                item.verse!=null -> ReaderVerse(item.verse,state,send)
                item.footer -> {
                    val next=ChapterNavigation.next(item.reference)
                    if(continuous) {
                        if(item.reference==state.flow.lastOrNull() && state.chapters[ReaderCanon.key(item.reference)]!=null && next!=null) {
                            LaunchedEffect(item.key) {send(Action.AppendChapter)}
                            if(!state.focusMode) Text(next.formatted,style=VerbumTypography.editorialHeadline,modifier=Modifier.fillMaxWidth().padding(vertical=Spacing.xxl))
                        }
                    } else if(next!=null && !state.focusMode) TextButton(onClick={send(Action.NextChapterTapped)},modifier=Modifier.fillMaxWidth().padding(vertical=Spacing.xxl)) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(next.formatted,style=VerbumTypography.editorialHeadline)
                            Text(stringResource(R.string.reader_swipe_continue),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                else -> {
                    LaunchedEffect(item.key) {send(Action.EnsureChapter(item.reference))}
                    val error=state.chapterErrors[ReaderCanon.key(item.reference)]
                    if(error!=null) Column(Modifier.fillMaxWidth().padding(vertical=Spacing.xxl)) {
                        Text(error.argument?.let {stringResource(error.messageRes,it)} ?: stringResource(error.messageRes))
                        TextButton(onClick={send(Action.EnsureChapter(item.reference))}) {Text(stringResource(R.string.try_again))}
                    } else Box(Modifier.fillMaxWidth().height(220.dp),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
                }
            }
        }
    }
}

@Composable private fun ReaderVerse(verse:BiblePassage,state:State,send:(Action)->Unit) {
    val id="${verse.bookId}.${verse.chapter}.${verse.verseStart}"
    val annotation=state.annotations[id]
    val spoken=LocalAudioReading.current?.contains(verse)==true
    val reference=PassageReference(verse.bookId,verse.chapter)
    val color=MaterialTheme.colorScheme.onSurface
    val accent=MaterialTheme.colorScheme.primary
    val currentSend by rememberUpdatedState(send)
    val segments=state.mentions[id].orEmpty()
    val text=remember(verse.text,segments,color,accent,state.focusMode,annotation?.highlightStyle,annotation?.highlight) {buildAnnotatedString {
        fun plain(value:String) {
            withLink(LinkAnnotation.Clickable("verse",TextLinkStyles(style=SpanStyle(color=color))) {
                currentSend(if(state.focusMode) Action.FocusToggled else Action.StudyVerse(reference,verse.verseStart))
            }) {append(value)}
        }
        if(segments.isEmpty()) plain(verse.text)
        else segments.forEachIndexed {index,segment->
            if(segment.entityIds.isEmpty()) plain(segment.text)
            else withLink(LinkAnnotation.Clickable("entity-$index",TextLinkStyles(style=SpanStyle(color=color,textDecoration=if(state.focusMode) TextDecoration.None else TextDecoration.Underline))) {
                currentSend(if(state.focusMode) Action.FocusToggled else Action.StudyVerse(reference,verse.verseStart,segment.entityIds))
            }) {append(segment.text)}
        }
    }}
    val size=VerbumTypography.scripture.fontSize*state.textScale.factor
    val wash=if((annotation?.highlightStyle ?: HighlightStyle.BACKGROUND)==HighlightStyle.BACKGROUND) annotation?.highlight?.let {highlightColor(it).copy(alpha=0.12f)} ?: Color.Transparent else Color.Transparent
    Row(Modifier.fillMaxWidth().drawBehind {
        if(spoken) {
            drawRect(accent.copy(alpha=0.035f))
            drawLine(accent.copy(alpha=0.45f),androidx.compose.ui.geometry.Offset(0f,4.dp.toPx()),androidx.compose.ui.geometry.Offset(0f,this.size.height-4.dp.toPx()),2.dp.toPx())
        }
        if(annotation?.highlightStyle==HighlightStyle.MARGIN && annotation.highlight!=null) {
            drawLine(highlightColor(annotation.highlight ?: HighlightColor.GOLD).copy(alpha=0.65f),androidx.compose.ui.geometry.Offset(0f,4.dp.toPx()),androidx.compose.ui.geometry.Offset(0f,this.size.height-4.dp.toPx()),2.dp.toPx())
        }
    }.background(wash,RoundedCornerShape(4.dp)).padding(vertical=Spacing.sm),verticalAlignment=Alignment.Top) {
        if(!state.focusMode) { Column(Modifier.width(32.dp).clickable {send(Action.StudyVerse(reference,verse.verseStart))}.semantics {contentDescription="${verse.verseStart}"},horizontalAlignment=Alignment.End) {
            Text(verse.verseStart.toString(),style=VerbumTypography.verseNumeral(size),color=MaterialTheme.colorScheme.outline)
            if(!annotation?.note.isNullOrEmpty()) Icon(Icons.Outlined.EditNote,contentDescription=stringResource(R.string.reader_your_note),modifier=Modifier.size(12.dp))
        }
        Spacer(Modifier.width(Spacing.sm)) }
        Text(text,style=VerbumTypography.scripture.copy(fontSize=size,lineHeight=size*1.65f,textDecoration=if(annotation?.highlightStyle==HighlightStyle.UNDERLINE && annotation.highlight!=null) TextDecoration.Underline else TextDecoration.None),color=color,
            modifier=Modifier.weight(1f))
    }
}
