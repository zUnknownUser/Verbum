package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.designsystem.tokens.Radius
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.EntityListFeature
import com.nexussoft.verbum.feature.scripture.ExploreFeature
import com.nexussoft.verbum.feature.scripture.HomeFeature
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.division
import com.nexussoft.verbum.models.localizedTitle

private val READING_MAX_WIDTH = 680.dp

/** Spec §5: a greeting, one question, a way back in, the verse of the day. */
@Composable
internal fun HomeScreen(state: HomeFeature.State, send: (HomeFeature.Action) -> Unit) {
    val openAccount = LocalOpenAccount.current
    LaunchedEffect(Unit) { send(HomeFeature.Action.Started) }
    val greeting = when (state.greeting) {
        HomeFeature.Greeting.MORNING -> R.string.good_morning
        HomeFeature.Greeting.AFTERNOON -> R.string.good_afternoon
        HomeFeature.Greeting.EVENING -> R.string.good_evening
    }
    Page {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(greeting).uppercase(), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                IconButton(onClick = openAccount) {
                    Icon(Icons.Outlined.AccountCircle, contentDescription = accountText("account"), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(stringResource(R.string.home_question), style = VerbumTypography.editorialTitle, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(Spacing.xxl))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(Radius.lg))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.lg))
                .clickable { send(HomeFeature.Action.SearchTapped) }
                .padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search), tint = MaterialTheme.colorScheme.outline)
            Text(stringResource(R.string.home_ask), style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.lastRead?.let { lastRead ->
            Spacer(Modifier.height(Spacing.xxl))
            Text(stringResource(R.string.continue_reading).uppercase(), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.md))
            PassageCard(lastRead.formatted, BibleBook.book(lastRead.bookId)?.division?.localizedTitle ?: "", Icons.AutoMirrored.Outlined.MenuBook) { send(HomeFeature.Action.ContinueReadingTapped) }
        }
        Spacer(Modifier.height(Spacing.xxl))
        Text(stringResource(R.string.today).uppercase(), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.md))
        DailyVerseCard(state.dailyVerse) { send(HomeFeature.Action.DailyVerse(it)) }
        Spacer(Modifier.height(Spacing.xxl))
        PassageCard(stringResource(R.string.arrival_title), stringResource(R.string.arrival_subtitle), Icons.Outlined.WbSunny) { send(HomeFeature.Action.ArrivalTapped) }
    }
}


/** A passage as a quiet card: reference in serif, a line of context, an arrow. */
@Composable
private fun PassageCard(title: String, subtitle: String, icon: ImageVector, onTap: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(Radius.lg))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.lg))
            .clickable(onClick = onTap)
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Spec §7: the paths into the graph, as an index page. */
@Composable
internal fun ExploreScreen(send: (ExploreFeature.Action) -> Unit) {
    Page {
        Text(stringResource(R.string.tab_explore), style = VerbumTypography.editorialTitle, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.sm))
        Text(stringResource(R.string.explore_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.lg))
        for (entry in ExploreFeature.Entry.entries) {
            val (title, subtitle) = when (entry) {
                ExploreFeature.Entry.PEOPLE -> R.string.people to R.string.explore_people
                ExploreFeature.Entry.PLACES -> R.string.places to R.string.explore_places
                ExploreFeature.Entry.THEMES -> R.string.themes to R.string.explore_themes
                ExploreFeature.Entry.EVENTS -> R.string.events to R.string.explore_events
                ExploreFeature.Entry.TIMELINE -> R.string.timeline to R.string.explore_timeline
                ExploreFeature.Entry.BOOKS -> R.string.books to R.string.explore_books
            }
            Column {
                Row(Modifier.fillMaxWidth().clickable { send(ExploreFeature.Action.EntryTapped(entry)) }.padding(vertical = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** People / Places / Themes / Events. */
@Composable
internal fun EntityListScreen(state: EntityListFeature.State, send: (EntityListFeature.Action) -> Unit) {
    LaunchedEffect(state.type) { if (state.entities.isEmpty() && !state.isLoading) send(EntityListFeature.Action.Started) }
    val title = when (state.type) {
        BibleEntityType.PERSON -> R.string.people
        BibleEntityType.PLACE -> R.string.places
        BibleEntityType.THEME -> R.string.themes
        BibleEntityType.EVENT -> R.string.events
        else -> R.string.tab_explore
    }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.readingMargin, end = Spacing.readingMargin, top = Spacing.lg, bottom = Spacing.xxxl * 2),
    ) {
        item { Text(stringResource(title), style = VerbumTypography.editorialTitle, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = Spacing.lg)) }
        if (state.isLoading) item { CircularProgressIndicator(color = MaterialTheme.colorScheme.outline) }
        items(state.entities, key = { it.id }) { entity -> EntityRow(entity.name, entity.summary) { send(EntityListFeature.Action.EntityTapped(entity)) } }
    }
}

/** Journey (§15) and Library (§16) arrive with the personal layer; until then, say so plainly. */
@Composable
internal fun EmptyPage(title: String, message: String) {
    Page {
        Text(title, style = VerbumTypography.editorialTitle, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.md))
        Spacer(Modifier.width(28.dp).height(1.dp).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.height(Spacing.md))
        Text(message, style = VerbumTypography.scripture, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = READING_MAX_WIDTH)
                .fillMaxWidth()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.readingMargin)
                .padding(top = Spacing.xl, bottom = Spacing.xxxl * 2),
            content = content,
        )
    }
}
