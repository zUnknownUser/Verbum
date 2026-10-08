package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.EntityListFeature.Action
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType

internal data class ThemeCategory(val id: String, val title: Int, val subtitle: Int, val icon: ImageVector)
internal val themeCategories = listOf(
    ThemeCategory("with-god", R.string.themes_cat_god, R.string.themes_cat_god_sub, Icons.Default.Star),
    ThemeCategory("emotions", R.string.themes_cat_emotions, R.string.themes_cat_emotions_sub, Icons.Default.Favorite),
    ThemeCategory("relationships", R.string.themes_cat_relationships, R.string.themes_cat_relationships_sub, Icons.Default.People),
    ThemeCategory("character", R.string.themes_cat_character, R.string.themes_cat_character_sub, Icons.Default.Spa),
    ThemeCategory("daily-life", R.string.themes_cat_daily, R.string.themes_cat_daily_sub, Icons.Default.Home),
    ThemeCategory("foundations", R.string.themes_cat_foundations, R.string.themes_cat_foundations_sub, Icons.Default.MenuBook),
    ThemeCategory("community", R.string.themes_cat_community, R.string.themes_cat_community_sub, Icons.Default.Groups),
    ThemeCategory("eternity", R.string.themes_cat_eternity, R.string.themes_cat_eternity_sub, Icons.Default.AutoAwesome),
)

@Composable
internal fun ThemeDiscoveryScreen(send: (Action) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(columns = GridCells.Adaptive(150.dp * LocalDensity.current.fontScale), modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            contentPadding = PaddingValues(Spacing.readingMargin), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.themes_explore_subject), style = VerbumTypography.editorialTitle)
                    Text(stringResource(R.string.themes_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.themes_begin), style = VerbumTypography.overline, modifier = Modifier.padding(top = Spacing.lg))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf("fixture.theme.faith" to R.string.themes_faith, "fixture.theme.prayer" to R.string.themes_prayer, "editorial.theme.hope" to R.string.themes_hope).forEach { (id, label) ->
                            val name = stringResource(label)
                            FilledTonalButton(onClick = { send(Action.EntityTapped(BibleEntity(id, BibleEntityType.THEME, name, null))) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp), contentPadding = PaddingValues(Spacing.sm)) { Text(name) }
                        }
                    }
                    Text(stringResource(R.string.themes_categories), style = VerbumTypography.overline, modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs))
                }
            }
            items(themeCategories, key = { it.id }) { category ->
                Surface(onClick = { send(Action.CategoryChanged(category.id)) }, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().testTag("themes.category.${category.id}")) {
                    Column(Modifier.heightIn(min = 164.dp).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Icon(category.icon, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(category.title), style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif))
                        Text(stringResource(category.subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(onClick = { send(Action.CategoryChanged("")) }, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md).testTag("themes.all")) { Text(stringResource(R.string.themes_all)) }
            }
        }
    }
}
