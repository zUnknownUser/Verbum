package com.nexussoft.verbum.feature.scripture.ui

import com.nexussoft.verbum.models.localizedName
import androidx.compose.ui.res.stringResource
import com.nexussoft.verbum.feature.scripture.R
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.designsystem.tokens.Elevation
import com.nexussoft.verbum.designsystem.tokens.Motion
import com.nexussoft.verbum.designsystem.tokens.Radius
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.ChapterNavigation
import com.nexussoft.verbum.feature.scripture.ChapterReaderFeature.Action
import com.nexussoft.verbum.feature.scripture.ChapterReaderFeature.Content
import com.nexussoft.verbum.feature.scripture.ChapterReaderFeature.State
import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.BiblePassage
import com.nexussoft.verbum.models.PassageReference

/**
 * The reading page. Paper, warm ink, serif; verse numerals hang in the margin
 * like a printed Bible. Chrome hides while you read and returns on a tap or an
 * upward scroll. Swipe sideways to turn the chapter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChapterReaderPane(
    state: State,
    send: (Action) -> Unit,
    onTitleTapped: () -> Unit,
    onSettingsTapped: () -> Unit,
    showTitleChevron: Boolean,
) {
    var chromeHidden by remember { mutableStateOf(false) }
    val view = LocalView.current
    LaunchedEffect(state.selectedVerses) {
        if (state.selectedVerses.isNotEmpty()) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    LaunchedEffect(state.reference) { chromeHidden = false }
    // Twin of iOS's `.task { store.send(.task) }`: loads on first appearance, reloads when the
    // reference changes (Go/next/previous), and is a no-op if the reducer already has it loaded.
    LaunchedEffect(state.reference) { send(Action.Started) }

    val chooseLabel = stringResource(R.string.choose_book_and_chapter, state.title)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = state.reference to state.content,
            transitionSpec = {
                val forward = isForward(initialState.first, targetState.first)
                (slideInHorizontally(tween(Motion.Duration.SPATIAL_MS)) { if (forward) it / 6 else -it / 6 } + fadeIn(tween(Motion.Duration.SPATIAL_MS)))
                    .togetherWith(fadeOut(tween(Motion.Duration.STANDARD_MS)))
            },
            contentKey = { it.first },
            label = "chapter",
        ) { (_, content) ->
            when (content) {
                Content.Idle, Content.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.outline)
                }

                is Content.Failed -> Unavailable(
                    title = content.error.argument?.let { stringResource(content.error.titleRes, it) } ?: stringResource(content.error.titleRes),
                    message = content.error.argument?.let { stringResource(content.error.messageRes, it) } ?: stringResource(content.error.messageRes),
                ) { send(Action.RetryTapped) }

                is Content.Loaded -> Page(
                    state = state,
                    verses = content.verses,
                    send = send,
                    onChromeToggle = { chromeHidden = !chromeHidden },
                    onScrollDirection = { down -> chromeHidden = down },
                )
            }
        }

        // Paper behind the status bar so text never runs under the clock while chrome is hidden.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
                .align(Alignment.TopCenter),
        )

        AnimatedVisibility(
            visible = !chromeHidden,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f)),
                title = {
                    Row(
                        Modifier
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTitleTapped)
                            .semantics { contentDescription = chooseLabel },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(state.title, style = VerbumTypography.navigationSerif)
                        if (showTitleChevron) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { send(Action.ListenTapped) }) {
                        Icon(Icons.Filled.Headphones, contentDescription = stringResource(R.string.listen), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { send(Action.TalkTapped) }) {
                        Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.voice_talk_chapter), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton(onClick = onSettingsTapped) {
                        Text("Aa", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                },
            )
        }

        val citation = state.selectionCitation
        AnimatedVisibility(
            visible = citation != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SelectionBar(
                citation = citation ?: "",
                onCopy = { send(Action.CopySelectionTapped) },
                onClear = { send(Action.ClearSelectionTapped) },
            )
        }
    }
}

@Composable
private fun Page(
    state: State,
    verses: List<BiblePassage>,
    send: (Action) -> Unit,
    onChromeToggle: () -> Unit,
    onScrollDirection: (down: Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        var last = 0
        snapshotFlow { listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset }
            .collect { offset ->
                val delta = offset - last
                if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 200 || delta < -40) onScrollDirection(false)
                else if (delta > 40) onScrollDirection(true)
                last = offset
            }
    }
    val fontSize = VerbumTypography.scripture.fontSize * state.textScale.factor

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onChromeToggle() } }
            .pointerInput(state.canGoToNextChapter, state.canGoToPreviousChapter) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (drag < -120 && state.canGoToNextChapter) send(Action.NextChapterTapped)
                        else if (drag > 120 && state.canGoToPreviousChapter) send(Action.PreviousChapterTapped)
                    },
                ) { _, dragAmount -> drag += dragAmount }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = READING_MAX_WIDTH).fillMaxWidth(),
            contentPadding = PaddingValues(start = Spacing.readingMargin, end = Spacing.readingMargin, top = 0.dp, bottom = Spacing.xxxl * 2),
        ) {
            item {
                ChapterOpener(state.book?.localizedName ?: state.reference.bookId, state.reference.chapter)
                TextButton(onClick = { send(Action.ContextTapped) }) { Text(stringResource(R.string.context_title)) }
            }
            items(verses, key = { it.id }) { verse ->
                VerseRow(
                    number = verse.verseStart,
                    text = verse.text,
                    fontSize = fontSize,
                    isSelected = verse.verseStart in state.selectedVerses,
                    onTap = { send(Action.VerseTapped(verse.verseStart)) },
                )
            }
            item {
                TextButton(onClick = { send(Action.ContextTapped) }) { Text(stringResource(R.string.context_title)) }
                ChapterFoot(
                    next = ChapterNavigation.next(state.reference),
                    previous = ChapterNavigation.previous(state.reference),
                    onNext = { send(Action.NextChapterTapped) },
                    onPrevious = { send(Action.PreviousChapterTapped) },
                )
            }
        }
    }
}

/** `1 SAMUEL` / `17` / a short bronze rule — centred like a book's chapter page. */
@Composable
private fun ChapterOpener(bookName: String, chapter: Int) {
    val openerLabel = stringResource(R.string.book_comma_chapter, bookName, chapter)
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 88.dp, bottom = Spacing.xxl)
            .semantics(mergeDescendants = true) { heading(); contentDescription = openerLabel },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(bookName.uppercase(), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.primary)
        Text(chapter.toString(), style = VerbumTypography.chapterNumeral, color = MaterialTheme.colorScheme.onSurface)
        Box(Modifier.padding(top = Spacing.xs).width(28.dp).height(1.dp).background(MaterialTheme.colorScheme.primary))
    }
}

/** One verse: numeral hanging in the margin, text flush. Selection is a bronze wash. */
@Composable
private fun VerseRow(number: Int, text: String, fontSize: TextUnit, isSelected: Boolean, onTap: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val wash = if (isSelected) accent.copy(alpha = 0.16f) else Color.Transparent
    val verseLabel = stringResource(R.string.verse_n, number, text)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .semantics {
                contentDescription = verseLabel
                selected = isSelected
            }
            .background(wash, RoundedCornerShape(Radius.md))
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            number.toString(),
            style = VerbumTypography.verseNumeral(fontSize),
            color = if (isSelected) accent else MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End,
            modifier = Modifier.width(28.dp).padding(top = 3.dp),
        )
        Text(
            text,
            style = VerbumTypography.scripture.copy(fontSize = fontSize, lineHeight = fontSize * 1.65f),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** End-of-chapter mark and the way onward. */
@Composable
private fun ChapterFoot(next: PassageReference?, previous: PassageReference?, onNext: () -> Unit, onPrevious: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Box(Modifier.width(28.dp).height(1.dp).background(MaterialTheme.colorScheme.primary))
        if (next != null) {
            val continueLabel = stringResource(R.string.continue_to, next.formatted)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNext)
                    .semantics { contentDescription = continueLabel }
                    .padding(vertical = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(stringResource(R.string.continue_label).uppercase(), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(next.formatted, style = VerbumTypography.editorialHeadline, color = MaterialTheme.colorScheme.onSurface)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            Text(stringResource(R.string.end_of_the_book).uppercase(), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (previous != null) {
            TextButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.width(14.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(previous.formatted, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun Unavailable(title: String, message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = VerbumTypography.editorialHeadline, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.sm))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.lg))
        Button(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
    }
}

@Composable
private fun SelectionBar(citation: String, onCopy: () -> Unit, onClear: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = Elevation.Floating.dp,
        shadowElevation = Elevation.Floating.dp,
        modifier = Modifier.navigationBarsPadding().padding(horizontal = Spacing.screenMargin, vertical = Spacing.md),
    ) {
        Row(
            Modifier.padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(citation, style = VerbumTypography.navigationSerif, color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onCopy) { Text(stringResource(R.string.copy)) }
            IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_selection), tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

private fun isForward(old: PassageReference, new: PassageReference): Boolean {
    val o = BibleBook.book(old.bookId)?.order ?: 0
    val n = BibleBook.book(new.bookId)?.order ?: 0
    return if (n == o) new.chapter >= old.chapter else n > o
}

private val READING_MAX_WIDTH = 680.dp
