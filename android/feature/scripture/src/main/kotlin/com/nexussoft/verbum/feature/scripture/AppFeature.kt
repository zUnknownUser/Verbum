package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.AskScriptureClient
import com.nexussoft.verbum.clients.AskScriptureException
import com.nexussoft.verbum.clients.BibleClient
import com.nexussoft.verbum.clients.RealtimeSessionClient
import com.nexussoft.verbum.clients.VoiceClient
import com.nexussoft.verbum.clients.VoiceException
import com.nexussoft.verbum.clients.UnavailableVoiceClient
import com.nexussoft.verbum.common.arch.pullbackOptional
import com.nexussoft.verbum.models.VoiceContext
import com.nexussoft.verbum.clients.ClipboardClient
import com.nexussoft.verbum.clients.GraphClient
import com.nexussoft.verbum.clients.NoopNotificationClient
import com.nexussoft.verbum.clients.NotificationClient
import com.nexussoft.verbum.clients.PreferencesClient
import com.nexussoft.verbum.clients.AudioPlayerClient
import com.nexussoft.verbum.clients.SearchClient
import com.nexussoft.verbum.clients.helloao.ScriptureAudioClient
import com.nexussoft.verbum.common.arch.Effect
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.combine
import com.nexussoft.verbum.common.arch.map
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.pullback
import com.nexussoft.verbum.common.arch.runEffect
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.PassageReference
import java.time.Clock

/**
 * The app shell (§6): Home · Explore · Journey · Library, plus Search. Each content tab owns a
 * navigation stack of destinations, so a screen is fully described by state (§63, §76).
 * Twin of iOS `AppFeature`.
 */
object AppFeature {
    enum class Tab { HOME, EXPLORE, JOURNEY, LIBRARY, SEARCH }

    /** A destination on a tab's stack. */
    sealed interface Destination {
        data class Reader(val state: ScriptureFeature.State) : Destination
        data class Entity(val state: EntityDetailFeature.State) : Destination
        data class Entities(val state: EntityListFeature.State) : Destination
        data class Books(val state: BookPickerFeature.State) : Destination
        data class Context(val state: ContextFeature.State) : Destination
        data class Arrival(val state: GuidedExplorationFeature.State) : Destination
        data class Graph(val state: GraphFeature.State) : Destination
        data class Timeline(val state: TimelineFeature.State) : Destination
        data class Ask(val state: AskFeature.State) : Destination
    }

    /** An action for the destination at [index] of a stack. */
    sealed interface DestinationAction {
        data class Reader(val action: ScriptureFeature.Action) : DestinationAction
        data class Entity(val action: EntityDetailFeature.Action) : DestinationAction
        data class Entities(val action: EntityListFeature.Action) : DestinationAction
        data class Books(val action: BookPickerFeature.Action) : DestinationAction
        data class Context(val action: ContextFeature.Action) : DestinationAction
        data class Arrival(val action: GuidedExplorationFeature.Action) : DestinationAction
        data class Graph(val action: GraphFeature.Action) : DestinationAction
        data class Timeline(val action: TimelineFeature.Action) : DestinationAction
        data class Ask(val action: AskFeature.Action) : DestinationAction
    }

    data class State(
        val tab: Tab = Tab.HOME,
        /** Home or Explore — whichever the user was on last. Search results land here. */
        val contentTab: Tab = Tab.HOME,
        val home: HomeFeature.State = HomeFeature.State(),
        val search: SearchFeature.State = SearchFeature.State(),
        val audio: AudioPlayerFeature.State = AudioPlayerFeature.State(),
        val homePath: List<Destination> = emptyList(),
        val explorePath: List<Destination> = emptyList(),
        /** The spoken conversation sheet, over whatever page started it. */
        val voice: VoiceFeature.State? = null,
    )

    sealed interface Action {
        /** The shell appeared: listen for notification taps for as long as it lives. */
        data object Started : Action
        data class TabChanged(val tab: Tab) : Action
        /** The user tapped a verse-of-the-day notification. */
        data class OpenedVerse(val reference: PassageReference) : Action
        data class Home(val action: HomeFeature.Action) : Action
        data class Explore(val action: ExploreFeature.Action) : Action
        data class Search(val action: SearchFeature.Action) : Action
        data class Audio(val action: AudioPlayerFeature.Action) : Action
        data class HomePath(val index: Int, val action: DestinationAction) : Action
        data class ExplorePath(val index: Int, val action: DestinationAction) : Action
        data class Pop(val tab: Tab) : Action
        data class Voice(val action: VoiceFeature.Action) : Action
        /** The sheet was swiped away. */
        data object VoiceDismissed : Action
    }

    class Dependencies(
        val bibleClient: BibleClient,
        val clipboard: ClipboardClient,
        val preferences: PreferencesClient,
        val searchClient: SearchClient,
        val graphClient: GraphClient,
        val audioClient: ScriptureAudioClient,
        val player: AudioPlayerClient,
        val clock: Clock = Clock.systemDefaultZone(),
        val language: () -> BookLanguage = { BookLanguage.current },
        val searchDebounceMs: Long = SearchFeature.DEBOUNCE_MS,
        val initialTextScale: () -> ReaderTextScale = { ReaderTextScale.STANDARD },
        val contextClient: com.nexussoft.verbum.clients.ContextClient = com.nexussoft.verbum.clients.fixtures.FixtureContextClient,
        val explorationClient: com.nexussoft.verbum.clients.GuidedExplorationClient = com.nexussoft.verbum.clients.EditorialExplorationClient,
        val notifications: NotificationClient = NoopNotificationClient,
        val timelineClient: com.nexussoft.verbum.clients.TimelineClient = com.nexussoft.verbum.clients.fixtures.FixtureTimelineClient,
        val askClient: AskScriptureClient = AskScriptureClient { throw AskScriptureException.Unavailable },
        val realtimeSessionClient: RealtimeSessionClient = RealtimeSessionClient { throw VoiceException.Unavailable },
        val voiceClient: VoiceClient = UnavailableVoiceClient,
        /** Localised title for the morning notification; resolved when the plan is built. */
        val dailyVerseTitle: () -> String = { "Verse of the day" },
    )

    fun reducer(deps: Dependencies): Reducer<State, Action> {
        val reader = ScriptureFeature.reducer(deps.bibleClient, deps.clipboard, deps.preferences, deps.contextClient, deps.graphClient, deps.askClient)
        val entity = EntityDetailFeature.reducer(deps.graphClient, deps.timelineClient)
        val timeline = TimelineFeature.reducer(deps.timelineClient, deps.graphClient)
        val entities = EntityListFeature.reducer(deps.graphClient)
        val books = BookPickerFeature.reducer
        val context = ContextFeature.reducer(deps.contextClient)
        val arrival = GuidedExplorationFeature.reducer(deps.explorationClient)
        val graph = GraphFeature.reducer(deps.graphClient)
        val ask = AskFeature.reducer(deps.askClient, deps.graphClient)
        val voice = VoiceFeature.reducer(VoiceFeature.Dependencies(deps.realtimeSessionClient, deps.voiceClient, deps.askClient, deps.searchClient, deps.bibleClient, deps.language))

        fun reduceDestination(destination: Destination, action: DestinationAction): Pair<Destination, Effect<DestinationAction>>? = when {
            destination is Destination.Ask && action is DestinationAction.Ask ->
                ask.reduce(destination.state, action.action).let { Destination.Ask(it.state) to it.effect.map { a -> DestinationAction.Ask(a) } }
            destination is Destination.Arrival && action is DestinationAction.Arrival ->
                arrival.reduce(destination.state, action.action).let { Destination.Arrival(it.state) to it.effect.map { a -> DestinationAction.Arrival(a) } }
            destination is Destination.Timeline && action is DestinationAction.Timeline ->
                timeline.reduce(destination.state, action.action).let { Destination.Timeline(it.state) to it.effect.map { a -> DestinationAction.Timeline(a) } }
            destination is Destination.Graph && action is DestinationAction.Graph ->
                graph.reduce(destination.state, action.action).let { Destination.Graph(it.state) to it.effect.map { a -> DestinationAction.Graph(a) } }
            destination is Destination.Context && action is DestinationAction.Context ->
                context.reduce(destination.state, action.action).let { Destination.Context(it.state) to it.effect.map { a -> DestinationAction.Context(a) } }
            destination is Destination.Reader && action is DestinationAction.Reader ->
                reader.reduce(destination.state, action.action).let { Destination.Reader(it.state) to it.effect.map { a -> DestinationAction.Reader(a) } }
            destination is Destination.Entity && action is DestinationAction.Entity ->
                entity.reduce(destination.state, action.action).let { Destination.Entity(it.state) to it.effect.map { a -> DestinationAction.Entity(a) } }
            destination is Destination.Entities && action is DestinationAction.Entities ->
                entities.reduce(destination.state, action.action).let { Destination.Entities(it.state) to it.effect.map { a -> DestinationAction.Entities(a) } }
            destination is Destination.Books && action is DestinationAction.Books ->
                books.reduce(destination.state, action.action).let { Destination.Books(it.state) to it.effect.map { a -> DestinationAction.Books(a) } }
            else -> null
        }

        /** What a destination's delegate pushes next, if anything. */
        fun follow(action: DestinationAction): Destination? = when (action) {
            is DestinationAction.Arrival -> (action.action as? GuidedExplorationFeature.Action.Delegate)?.delegate?.let {
                when (it) {
                    is GuidedExplorationFeature.DelegateAction.OpenPassage -> Destination.Reader(ScriptureFeature.State.initial(it.reference, deps.initialTextScale()))
                    is GuidedExplorationFeature.DelegateAction.OpenContext -> Destination.Context(ContextFeature.State(PassageReference(it.reference.bookId, it.reference.chapter)))
                }
            }
            is DestinationAction.Entity -> (action.action as? EntityDetailFeature.Action.Delegate)?.delegate?.let {
                when (it) {
                    is EntityDetailFeature.DelegateAction.OpenEntity -> Destination.Entity(EntityDetailFeature.State(it.entity.id))
                    is EntityDetailFeature.DelegateAction.OpenPassage -> Destination.Reader(ScriptureFeature.State.initial(it.reference, deps.initialTextScale()))
                    is EntityDetailFeature.DelegateAction.OpenGraph -> Destination.Graph(GraphFeature.State(it.entityId))
                    is EntityDetailFeature.DelegateAction.OpenTimeline -> Destination.Timeline(TimelineFeature.State(highlight = it.entityId))
                    is EntityDetailFeature.DelegateAction.Talk -> null
                }
            }
            is DestinationAction.Timeline -> (action.action as? TimelineFeature.Action.Delegate)?.delegate?.let {
                when (it) { is TimelineFeature.DelegateAction.OpenEntity -> Destination.Entity(EntityDetailFeature.State(it.id)) }
            }
            is DestinationAction.Graph -> (action.action as? GraphFeature.Action.Delegate)?.delegate?.let {
                when (it) {
                    is GraphFeature.DelegateAction.OpenEntity -> Destination.Entity(EntityDetailFeature.State(it.entity.id))
                    is GraphFeature.DelegateAction.OpenPassage -> Destination.Reader(ScriptureFeature.State.initial(it.reference, deps.initialTextScale()))
                    is GraphFeature.DelegateAction.Focus -> Destination.Graph(GraphFeature.State(it.entity.id))
                }
            }
            is DestinationAction.Entities -> (action.action as? EntityListFeature.Action.Delegate)?.delegate?.let {
                when (it) { is EntityListFeature.DelegateAction.OpenEntity -> Destination.Entity(EntityDetailFeature.State(it.entity.id)) }
            }
            is DestinationAction.Books -> (action.action as? BookPickerFeature.Action.Delegate)?.delegate?.let {
                when (it) { is BookPickerFeature.DelegateAction.ChapterSelected -> Destination.Reader(ScriptureFeature.State.initial(it.reference, deps.initialTextScale())) }
            }
            is DestinationAction.Reader -> ((action.action as? ScriptureFeature.Action.Delegate)?.delegate as? ScriptureFeature.DelegateAction.OpenContext)?.let {
                Destination.Context(ContextFeature.State(PassageReference(it.reference.bookId, it.reference.chapter)))
            }
            is DestinationAction.Context -> (action.action as? ContextFeature.Action.Delegate)?.delegate?.let {
                when (it) {
                    is ContextFeature.DelegateAction.OpenEntity -> Destination.Entity(EntityDetailFeature.State(it.entity.id))
                    is ContextFeature.DelegateAction.OpenPassage -> Destination.Reader(ScriptureFeature.State.initial(it.reference, deps.initialTextScale()))
                }
            }
            is DestinationAction.Ask -> (action.action as? AskFeature.Action.Delegate)?.delegate?.let {
                when (it) {
                    is AskFeature.DelegateAction.OpenEntity -> Destination.Entity(EntityDetailFeature.State(it.entity.id))
                    is AskFeature.DelegateAction.OpenPassage -> Destination.Reader(ScriptureFeature.State.initial(it.reference, deps.initialTextScale()))
                    is AskFeature.DelegateAction.SearchInstead -> null
                    is AskFeature.DelegateAction.Talk -> null
                }
            }
        }

        val pathReducer = Reducer<State, Action> { state, action ->
            val (index, destAction, isHome) = when (action) {
                is Action.HomePath -> Triple(action.index, action.action, true)
                is Action.ExplorePath -> Triple(action.index, action.action, false)
                else -> return@Reducer state.only()
            }
            val path = if (isHome) state.homePath else state.explorePath
            if (index !in path.indices) return@Reducer state.only()
            val (newDestination, effect) = reduceDestination(path[index], destAction) ?: return@Reducer state.only()
            var newPath = path.toMutableList().also { it[index] = newDestination }
            follow(destAction)?.let { newPath += it }
            val embedded = effect.map { if (isHome) Action.HomePath(index, it) else Action.ExplorePath(index, it) }
            val listen = ((destAction as? DestinationAction.Reader)?.action as? ScriptureFeature.Action.Delegate)?.delegate as? ScriptureFeature.DelegateAction.Listen
            val audioEffect: Effect<Action> = when {
                listen == null -> Effect.None
                // Already playing this chapter: the button pauses/resumes instead.
                state.audio.reference == PassageReference(listen.reference.bookId, listen.reference.chapter) -> Effect.Send(Action.Audio(AudioPlayerFeature.Action.TogglePlayPause))
                else -> Effect.Send(Action.Audio(AudioPlayerFeature.Action.Play(listen.reference)))
            }
            // §21.3: Ask falls back to search results — the field still holds the question.
            val searchInstead = ((destAction as? DestinationAction.Ask)?.action as? AskFeature.Action.Delegate)?.delegate as? AskFeature.DelegateAction.SearchInstead
            val searchEffect: Effect<Action> = when {
                searchInstead == null || state.search.query.trim() == searchInstead.question -> Effect.None
                else -> Effect.Send(Action.Search(SearchFeature.Action.QueryChanged(searchInstead.question)))
            }
            val tab = if (searchInstead != null) Tab.SEARCH else state.tab
            // A conversation starts over the page; one at a time, and never over the chapter audio.
            val talk: VoiceContext? = when (destAction) {
                is DestinationAction.Reader -> ((destAction.action as? ScriptureFeature.Action.Delegate)?.delegate as? ScriptureFeature.DelegateAction.Talk)?.let { VoiceContext.Chapter(it.reference) }
                is DestinationAction.Entity -> ((destAction.action as? EntityDetailFeature.Action.Delegate)?.delegate as? EntityDetailFeature.DelegateAction.Talk)?.let { VoiceContext.Entity(it.detail) }
                is DestinationAction.Ask -> ((destAction.action as? AskFeature.Action.Delegate)?.delegate as? AskFeature.DelegateAction.Talk)?.let { VoiceContext.Answer(it.question, it.answer) }
                else -> null
            }
            val voiceState = talk?.let { VoiceFeature.State(it) } ?: state.voice
            val pauseEffect: Effect<Action> = if (talk != null && state.audio.isPlaying) Effect.Send(Action.Audio(AudioPlayerFeature.Action.TogglePlayPause)) else Effect.None
            (if (isHome) state.copy(tab = tab, homePath = newPath, voice = voiceState) else state.copy(tab = tab, explorePath = newPath, voice = voiceState))
                .with(Effect.Merge(listOf(embedded, audioEffect, searchEffect, pauseEffect)))
        }

        fun push(state: State, destination: Destination): State =
            if (state.contentTab == Tab.EXPLORE) state.copy(tab = Tab.EXPLORE, explorePath = state.explorePath + destination)
            else state.copy(tab = Tab.HOME, homePath = state.homePath + destination)

        return combine(
            HomeFeature.reducer(deps.preferences, deps.clock, deps.bibleClient, deps.notifications, deps.dailyVerseTitle).pullback(
                get = { it.home }, set = { s, c -> s.copy(home = c) },
                extractAction = { (it as? Action.Home)?.action }, embedAction = { Action.Home(it) },
            ),
            SearchFeature.reducer(deps.searchClient, deps.searchDebounceMs, deps.language).pullback(
                get = { it.search }, set = { s, c -> s.copy(search = c) },
                extractAction = { (it as? Action.Search)?.action }, embedAction = { Action.Search(it) },
            ),
            AudioPlayerFeature.reducer(deps.audioClient, deps.player).pullback(
                get = { it.audio }, set = { s, c -> s.copy(audio = c) },
                extractAction = { (it as? Action.Audio)?.action }, embedAction = { Action.Audio(it) },
            ),
            ExploreFeature.reducer.pullback(
                get = { ExploreFeature.State }, set = { s, _ -> s },
                extractAction = { (it as? Action.Explore)?.action }, embedAction = { Action.Explore(it) },
            ),
            pathReducer,
            voice.pullbackOptional(
                get = { it.voice }, set = { s, c -> s.copy(voice = c) },
                extractAction = { (it as? Action.Voice)?.action }, embedAction = { Action.Voice(it) },
            ),
            Reducer { state, action ->
                when (action) {
                    // The companion opens a passage: the sheet goes, the reader comes.
                    is Action.Voice -> when (val d = (action.action as? VoiceFeature.Action.Delegate)?.delegate) {
                        is VoiceFeature.DelegateAction.OpenPassage -> push(state.copy(voice = null), Destination.Reader(ScriptureFeature.State.initial(d.reference, deps.initialTextScale()))).with(VoiceFeature.cancelSession())
                        null -> state.only()
                    }
                    Action.VoiceDismissed -> state.copy(voice = null).with(VoiceFeature.cancelSession())
                    Action.Started -> state.with(
                        runEffect(id = "openedVerses", cancelInFlight = true) { send ->
                            deps.notifications.openedVerses().collect { send(Action.OpenedVerse(it)) }
                        },
                    )
                    // A notification lands on Home, on top of whatever was there.
                    is Action.OpenedVerse -> state.copy(
                        tab = Tab.HOME,
                        contentTab = Tab.HOME,
                        homePath = state.homePath + Destination.Reader(ScriptureFeature.State.initial(action.reference, deps.initialTextScale())),
                    ).only()
                    is Action.TabChanged -> {
                        // Tapping the already-selected tab pops it to root — SwiftUI's TabView
                        // does this for free on iOS; NavigationSuiteScaffold does not, so it is
                        // explicit here.
                        val reselected = action.tab == state.tab
                        state.copy(
                            tab = action.tab,
                            contentTab = if (action.tab == Tab.HOME || action.tab == Tab.EXPLORE) action.tab else state.contentTab,
                            homePath = if (reselected && action.tab == Tab.HOME) emptyList() else state.homePath,
                            explorePath = if (reselected && action.tab == Tab.EXPLORE) emptyList() else state.explorePath,
                        ).only()
                    }
                    is Action.Pop -> when (action.tab) {
                        Tab.HOME -> state.copy(homePath = state.homePath.dropLast(1)).only()
                        Tab.EXPLORE -> state.copy(explorePath = state.explorePath.dropLast(1)).only()
                        else -> state.only()
                    }
                    is Action.Home -> when (val d = (action.action as? HomeFeature.Action.Delegate)?.delegate) {
                        is HomeFeature.DelegateAction.OpenPassage ->
                            state.copy(homePath = state.homePath + Destination.Reader(ScriptureFeature.State.initial(d.reference, deps.initialTextScale()))).only()
                        HomeFeature.DelegateAction.OpenSearch -> state.copy(tab = Tab.SEARCH).only()
                        HomeFeature.DelegateAction.OpenArrival -> state.copy(homePath = state.homePath + Destination.Arrival(GuidedExplorationFeature.State())).only()
                        null -> state.only()
                    }
                    is Action.Explore -> when (val d = (action.action as? ExploreFeature.Action.Delegate)?.delegate) {
                        is ExploreFeature.DelegateAction.Open -> {
                            val type = d.entry.entityType
                            val destination = when {
                                type != null -> Destination.Entities(EntityListFeature.State(type))
                                d.entry == ExploreFeature.Entry.TIMELINE -> Destination.Timeline(TimelineFeature.State())
                                else -> Destination.Books(BookPickerFeature.State(current = state.home.lastRead ?: PassageReference("John", 3)))
                            }
                            state.copy(explorePath = state.explorePath + destination).only()
                        }
                        null -> state.only()
                    }
                    is Action.Audio -> when (val d = (action.action as? AudioPlayerFeature.Action.Delegate)?.delegate) {
                        is AudioPlayerFeature.DelegateAction.OpenChapter -> push(state, Destination.Reader(ScriptureFeature.State.initial(d.reference, deps.initialTextScale()))).only()
                        null -> state.only()
                    }
                    is Action.Search -> when (val d = (action.action as? SearchFeature.Action.Delegate)?.delegate) {
                        is SearchFeature.DelegateAction.OpenPassage -> push(state, Destination.Reader(ScriptureFeature.State.initial(d.reference, deps.initialTextScale()))).only()
                        is SearchFeature.DelegateAction.OpenEntity -> push(state, Destination.Entity(EntityDetailFeature.State(d.entity.id))).only()
                        is SearchFeature.DelegateAction.Ask -> push(state, Destination.Ask(AskFeature.State(d.question))).only()
                        null -> state.only()
                    }
                    else -> state.only()
                }
            },
        )
    }
}
