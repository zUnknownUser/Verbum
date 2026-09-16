package com.nexussoft.verbum.feature.scripture.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexussoft.verbum.common.arch.Store
import com.nexussoft.verbum.feature.scripture.AppFeature
import com.nexussoft.verbum.feature.scripture.AppFeature.Action
import com.nexussoft.verbum.feature.scripture.AppFeature.Destination
import com.nexussoft.verbum.feature.scripture.AppFeature.DestinationAction
import com.nexussoft.verbum.feature.scripture.AppFeature.Tab
import com.nexussoft.verbum.feature.scripture.R

/**
 * Bottom bar on a phone, navigation rail on a tablet or an unfolded phone — same state,
 * same hierarchy (`NavigationSuiteScaffold`). Twin of iOS `AppView`.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(store: Store<AppFeature.State, Action>) {
    // Playback time belongs to the accessory, not navigation or reading.
    val shell = remember(store) {
        store.state.map { it.copy(audio = com.nexussoft.verbum.feature.scripture.AudioPlayerFeature.State()) }.distinctUntilChanged()
    }
    val state by shell.collectAsStateWithLifecycle(initialValue = store.state.value.copy(audio = com.nexussoft.verbum.feature.scripture.AudioPlayerFeature.State()))

    state.voice?.let { voice ->
        ModalBottomSheet(onDismissRequest = { store.send(Action.VoiceDismissed) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            val context = LocalContext.current
            VoicePane(
                voice,
                onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                },
            ) { store.send(Action.Voice(it)) }
        }
    }
    val reading = (when(state.tab) { Tab.HOME -> state.homePath.lastOrNull(); Tab.EXPLORE -> state.explorePath.lastOrNull(); else -> null }) as? Destination.Reader
    val quiet = reading?.state?.reader?.focusMode == true
    NavigationSuiteScaffold(
        layoutType = if(quiet) NavigationSuiteType.None else NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo()),
        navigationSuiteItems = {
            item(selected = state.tab == Tab.HOME, onClick = { store.send(Action.TabChanged(Tab.HOME)) }, icon = { Icon(Icons.Filled.Home, null) }, label = { Text(stringResource(R.string.tab_home)) })
            item(selected = state.tab == Tab.EXPLORE, onClick = { store.send(Action.TabChanged(Tab.EXPLORE)) }, icon = { Icon(Icons.Outlined.Explore, null) }, label = { Text(stringResource(R.string.tab_explore)) })
            item(selected = state.tab == Tab.JOURNEY, onClick = { store.send(Action.TabChanged(Tab.JOURNEY)) }, icon = { Icon(Icons.Outlined.Timeline, null) }, label = { Text(stringResource(R.string.tab_journey)) })
            item(selected = state.tab == Tab.LIBRARY, onClick = { store.send(Action.TabChanged(Tab.LIBRARY)) }, icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) }, label = { Text(stringResource(R.string.tab_library)) })
            item(selected = state.tab == Tab.SEARCH, onClick = { store.send(Action.TabChanged(Tab.SEARCH)) }, icon = { Icon(Icons.Filled.Search, null) }, label = { Text(stringResource(R.string.search)) })
        },
    ) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
          Box(Modifier.weight(1f)) {
            when (state.tab) {
                Tab.HOME -> TabStack(
                    path = state.homePath,
                    root = { HomeScreen(state.home) { store.send(Action.Home(it)) } },
                    onPop = { store.send(Action.Pop(Tab.HOME)) },
                    send = { index, action -> store.send(Action.HomePath(index, action)) },
                )
                Tab.EXPLORE -> TabStack(
                    path = state.explorePath,
                    root = { ExploreScreen { store.send(Action.Explore(it)) } },
                    onPop = { store.send(Action.Pop(Tab.EXPLORE)) },
                    send = { index, action -> store.send(Action.ExplorePath(index, action)) },
                )
                Tab.JOURNEY -> EmptyPage(stringResource(R.string.tab_journey), stringResource(R.string.journey_empty))
                Tab.LIBRARY -> EmptyPage(stringResource(R.string.tab_library), stringResource(R.string.library_empty))
                Tab.SEARCH -> SearchPane(state.search) { store.send(Action.Search(it)) }
            }
          }
          if (!quiet) AudioAccessory(store)
        }
    }
}

@Composable
private fun AudioAccessory(store: Store<AppFeature.State, Action>) {
    val audioFlow = remember(store) { store.state.map { it.audio }.distinctUntilChanged() }
    val audio by audioFlow.collectAsStateWithLifecycle(initialValue = store.state.value.audio)
    if (audio.isActive) MiniPlayer(audio) { store.send(Action.Audio(it)) }
}

/** A tab's stack: root, or the top destination with back popping state. */
@Composable
private fun TabStack(path: List<Destination>, root: @Composable () -> Unit, onPop: () -> Unit, send: (Int, DestinationAction) -> Unit) {
    if (path.isNotEmpty()) BackHandler(onBack = onPop)
    AnimatedContent(
        targetState = path.size,
        transitionSpec = {
            val forward = targetState > initialState
            (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()).togetherWith(slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
        },
        label = "stack",
    ) { depth ->
        if (depth == 0) {
            root()
        } else {
            val index = depth - 1
            // AnimatedContent can still render the outgoing depth after a pop.
            path.getOrNull(index)?.let { destination ->
                DestinationScreen(destination, onBack = onPop) { send(index, it) }
            }
        }
    }
}

@Composable
private fun DestinationScreen(destination: Destination, onBack: () -> Unit, send: (DestinationAction) -> Unit) {
    when (destination) {
        is Destination.Arrival -> GuidedExplorationPane(destination.state, onBack) { send(DestinationAction.Arrival(it)) }
        is Destination.Context -> ContextPane(destination.state, onBack) { send(DestinationAction.Context(it)) }
        is Destination.Graph -> GraphPane(destination.state, onBack) { send(DestinationAction.Graph(it)) }
        is Destination.Timeline -> TimelinePane(destination.state, onBack) { send(DestinationAction.Timeline(it)) }
        is Destination.Ask -> AskPane(destination.state, onBack) { send(DestinationAction.Ask(it)) }
        is Destination.Reader -> ScriptureScreen(destination.state) { send(DestinationAction.Reader(it)) }
        is Destination.Entity -> Box(Modifier.statusBarsPadding()) { EntityDetailPane(destination.state) { send(DestinationAction.Entity(it)) } }
        is Destination.Entities -> EntityListScreen(destination.state) { send(DestinationAction.Entities(it)) }
        is Destination.Books -> BookPickerPane(destination.state) { send(DestinationAction.Books(it)) }
    }
}
