package com.nexussoft.verbum.feature.scripture.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
    val pager=rememberPagerState(initialPage=ReaderCanon.index(state.reference),pageCount={ReaderCanon.chapters.size})
    val currentReference by rememberUpdatedState(state.reference)
    var menu by remember {mutableStateOf(false)}
    LaunchedEffect(Unit) {send(Action.Started)}
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
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if(state.readingMode==ReadingMode.PAGES) HorizontalPager(state=pager,beyondViewportPageCount=1,modifier=Modifier.fillMaxSize(),key={ReaderCanon.key(ReaderCanon.chapters[it])}) {index->
            ReaderPage(state,ReaderCanon.chapters[index],false,send)
        } else ReaderPage(state,state.flow.firstOrNull() ?: state.reference,true,send)
        if(!state.focusMode) TopAppBar(
            title={Row(Modifier.clickable(onClick=onTitleTapped),verticalAlignment=Alignment.CenterVertically) {
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
        ) else Surface(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Spacing.sm),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=0.9f)) {
            IconButton(onClick={send(Action.FocusToggled)}) {Icon(Icons.Outlined.Visibility,contentDescription=stringResource(R.string.reader_show_controls))}
        }
        state.history.lastOrNull()?.let {previous->
            Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Spacing.sm),shape=RoundedCornerShape(28.dp),tonalElevation=2.dp) {
                TextButton(onClick={send(Action.BackToReading)}) {Icon(Icons.Outlined.Undo,contentDescription=null);Text(stringResource(R.string.reader_return,previous.reference.formatted))}
            }
        }
    }
    state.study?.let {study->VerseStudySheet(study,{send(Action.Study(it))},{send(Action.StudyDismissed)})}
}

private data class ReaderItem(val key:String,val reference:PassageReference,val verse:BiblePassage?=null,val header:Boolean=false,val footer:Boolean=false)

@Composable private fun ReaderPage(state:State,reference:PassageReference,continuous:Boolean,send:(Action)->Unit) {
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
    var restored by remember {mutableIntStateOf(-1)}
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
                item.header -> Column(Modifier.fillMaxWidth().padding(top=Spacing.xxl,bottom=Spacing.xxl).semantics {heading()},horizontalAlignment=Alignment.CenterHorizontally) {
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
                            Text(next.formatted,style=VerbumTypography.editorialHeadline,modifier=Modifier.fillMaxWidth().padding(vertical=Spacing.xxl))
                        }
                    } else if(next!=null) TextButton(onClick={send(Action.NextChapterTapped)},modifier=Modifier.fillMaxWidth().padding(vertical=Spacing.xxl)) {
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
    val reference=PassageReference(verse.bookId,verse.chapter)
    val color=MaterialTheme.colorScheme.onSurface
    val accent=MaterialTheme.colorScheme.primary
    val currentSend by rememberUpdatedState(send)
    val segments=state.mentions[id].orEmpty()
    val text=remember(verse.text,segments,color,accent) {buildAnnotatedString {
        fun plain(value:String) {
            withLink(LinkAnnotation.Clickable("verse",TextLinkStyles(style=SpanStyle(color=color))) {
                currentSend(Action.StudyVerse(reference,verse.verseStart))
            }) {append(value)}
        }
        if(segments.isEmpty()) plain(verse.text)
        else segments.forEachIndexed {index,segment->
            if(segment.entityIds.isEmpty()) plain(segment.text)
            else withLink(LinkAnnotation.Clickable("entity-$index",TextLinkStyles(style=SpanStyle(color=color,textDecoration=TextDecoration.Underline))) {
                currentSend(Action.StudyVerse(reference,verse.verseStart,segment.entityIds))
            }) {append(segment.text)}
        }
    }}
    val size=VerbumTypography.scripture.fontSize*state.textScale.factor
    val wash=annotation?.highlight?.let {highlightColor(it).copy(alpha=0.2f)} ?: Color.Transparent
    Row(Modifier.fillMaxWidth().background(wash,RoundedCornerShape(4.dp)).padding(vertical=Spacing.sm),verticalAlignment=Alignment.Top) {
        Column(Modifier.width(32.dp).clickable {send(Action.StudyVerse(reference,verse.verseStart))}.semantics {contentDescription="${verse.verseStart}"},horizontalAlignment=Alignment.End) {
            Text(verse.verseStart.toString(),style=VerbumTypography.verseNumeral(size),color=MaterialTheme.colorScheme.outline)
            if(!annotation?.note.isNullOrEmpty()) Icon(Icons.Outlined.EditNote,contentDescription=stringResource(R.string.reader_your_note),modifier=Modifier.size(12.dp))
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(text,style=VerbumTypography.scripture.copy(fontSize=size,lineHeight=size*1.65f),color=color,
            modifier=Modifier.weight(1f))
    }
}
