package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.nexussoft.verbum.feature.scripture.ScriptureFeature
import com.nexussoft.verbum.feature.scripture.ScriptureFeature.Action

/**
 * Reader, with the shelf as a bottom sheet (compact) or a leading column (expanded).
 * Size class, never orientation. Twin of iOS `ScriptureView`.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScriptureScreen(state: ScriptureFeature.State, send: (Action) -> Unit) {
    val expanded = currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    val reader: @Composable () -> Unit = {
        ChapterReaderPane(
            state = state.reader,
            send = { send(Action.Reader(it)) },
            onTitleTapped = { send(Action.TitleTapped) },
            onSettingsTapped = { send(Action.SettingsButtonTapped) },
            showTitleChevron = !expanded,
        )
    }

    if (expanded && !state.reader.focusMode) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(340.dp)) { BookPickerPane(state.books, send = { send(Action.Books(it)) }) }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.fillMaxSize()) { reader() }
        }
    } else {
        reader()
        if (state.isShelfPresented) {
            ModalBottomSheet(onDismissRequest = { send(Action.ShelfDismissed) }, containerColor = MaterialTheme.colorScheme.background) {
                BookPickerPane(state.books, send = { send(Action.Books(it)) })
            }
        }
    }

    state.settings?.let { settings ->
        ReaderSettingsSheet(state = settings, send = { send(Action.Settings(it)) }, onDismiss = { send(Action.SettingsDismissed) })
    }
}
