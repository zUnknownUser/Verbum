package com.nexussoft.verbum.feature.scripture.ui

import com.nexussoft.verbum.models.localizedTitle
import com.nexussoft.verbum.models.localizedName
import androidx.compose.ui.res.stringResource
import com.nexussoft.verbum.feature.scripture.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.designsystem.tokens.Radius
import com.nexussoft.verbum.designsystem.tokens.Shelf
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.BookPickerFeature.Action
import com.nexussoft.verbum.feature.scripture.BookPickerFeature.State
import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.Division
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.Testament
import com.nexussoft.verbum.models.division

private const val TOP_BREATHING_ROOM_PX = 48

/**
 * The canon as a bookshelf: one shelf per division, one block per book, the
 * name set upright in strong serif with its chapter count beside it. Tap a
 * block and its chapters unfold beneath the shelf; tap again to close.
 * Twin of the iOS `BookPickerView`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookPickerPane(state: State, send: (Action) -> Unit) {
    val listState = rememberLazyListState()
    val divisions = Division.entries
    LaunchedEffect(state.selectedBook) {
        val book = state.selectedBook ?: return@LaunchedEffect
        // items: 0 = title, then per testament: 1 header + its divisions
        val index = 1 + divisions.indexOf(book.division) + (if (book.division.testament == Testament.NEW) 2 else 1)
        listState.animateScrollToItem(index, scrollOffset = -TOP_BREATHING_ROOM_PX)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = WindowInsets.statusBars.asPaddingValues(),
    ) {
        item {
            Text(
                stringResource(R.string.books),
                style = VerbumTypography.editorialTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(start = Spacing.readingMargin, end = Spacing.readingMargin, top = Spacing.xl, bottom = Spacing.lg)
                    .semantics { heading() },
            )
        }
        for (testament in Testament.entries) {
            item {
                Text(
                    stringResource(if (testament == Testament.OLD) R.string.old_testament else R.string.new_testament).uppercase(),
                    style = VerbumTypography.overline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.readingMargin, end = Spacing.readingMargin, top = if (testament == Testament.OLD) 0.dp else Spacing.xxl, bottom = Spacing.sm),
                )
            }
            val shelves = divisions.filter { it.testament == testament }
            shelves.forEachIndexed { index, division ->
                item(key = division.name) {
                    ShelfRow(
                        division = division,
                        tintIndex = index,
                        current = state.current,
                        selectedBook = state.selectedBook,
                        onSpine = { book -> send(if (state.selectedBook == book) Action.BackToBooksTapped else Action.BookTapped(book)) },
                        onChapter = { send(Action.ChapterTapped(it)) },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(Spacing.xxxl * 2)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShelfRow(
    division: Division,
    tintIndex: Int,
    current: PassageReference,
    selectedBook: BibleBook?,
    onSpine: (BibleBook) -> Unit,
    onChapter: (Int) -> Unit,
) {
    val openBook = selectedBook?.takeIf { it.division == division }
    val tint = Shelf.spineTint(tintIndex)
    Column(Modifier.fillMaxWidth().padding(top = Spacing.md).animateContentSize()) {
        Text(
            division.localizedTitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.readingMargin),
        )
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.readingMargin, end = Spacing.readingMargin, top = Spacing.sm, bottom = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            for (book in division.books) {
                BookBlock(
                    book = book,
                    tint = tint,
                    isCurrent = book.id == current.bookId,
                    isOpen = openBook == book,
                    onTap = { onSpine(book) },
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = Spacing.readingMargin - Spacing.sm),
        )
        AnimatedVisibility(
            visible = openBook != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val book = openBook ?: selectedBook ?: return@AnimatedVisibility
            Column(Modifier.padding(horizontal = Spacing.readingMargin).padding(top = Spacing.lg)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(book.localizedName.uppercase(), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.n_chapters, book.chapterCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Spacer(Modifier.height(Spacing.md))
                ChapterGrid(book, if (current.bookId == book.id) current.chapter else null, onChapter)
            }
        }
    }
}

/** A block with the book's name set upright and its chapter count beside it. The book being read is bronze; the open one lifts. */
@Composable
private fun BookBlock(book: BibleBook, tint: androidx.compose.ui.graphics.Color, isCurrent: Boolean, isOpen: Boolean, onTap: () -> Unit) {
    val shape = RoundedCornerShape(Radius.sm)
    val nameColor = if (isCurrent) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
    val countColor = if (isCurrent) MaterialTheme.colorScheme.background.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline
    val blockLabel = if (isCurrent) "${book.localizedName}, ${stringResource(R.string.currently_reading)}" else "${book.localizedName}, ${stringResource(R.string.n_chapters, book.chapterCount)}"
    val border = when {
        isOpen -> MaterialTheme.colorScheme.primary
        isCurrent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Row(
        Modifier
            .offset(y = if (isOpen) -3.dp else 0.dp)
            .height(46.dp)
            .background(if (isCurrent) MaterialTheme.colorScheme.primary else tint, shape)
            .border(if (isOpen) 1.5.dp else 1.dp, border, shape)
            .clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onTap)
            .semantics {
                contentDescription = blockLabel
                selected = isOpen
            }
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(book.localizedName, style = Shelf.spine, color = nameColor, maxLines = 1)
        Text(book.chapterCount.toString(), style = MaterialTheme.typography.labelSmall, color = countColor)
    }
}

@Composable
private fun ChapterGrid(book: BibleBook, currentChapter: Int?, onChapter: (Int) -> Unit) {
    val perRow = 6
    val rows = (1..book.chapterCount).chunked(perRow)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                for (chapter in row) {
                    val isCurrent = chapter == currentChapter
                    val chapterLabel = stringResource(R.string.book_chapter, book.localizedName, chapter)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(Radius.sm))
                            .border(1.dp, if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.sm))
                            .clickable { onChapter(chapter) }
                            .semantics {
                                contentDescription = chapterLabel
                                selected = isCurrent
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            chapter.toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                            color = if (isCurrent) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
