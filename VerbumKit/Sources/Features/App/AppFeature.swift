import Clients
import ComposableArchitecture
import Models

/// The app shell (spec §6): Home · Explore · Journey · Library, plus Search
/// reachable from anywhere. Each content tab owns a navigation stack whose
/// elements are the app's destinations — a chapter, an entity page, a list —
/// so a screen is fully described by state (§63, §76 deep links).
@Reducer
public struct AppFeature {
    @ObservableState
    public struct State: Equatable {
        public var tab: Tab = .home
        /// Home or Explore — whichever the user was on last. Search results land here.
        public var contentTab: Tab = .home
        public var home = HomeFeature.State()
        public var explore = ExploreFeature.State()
        public var search = SearchFeature.State()
        public var audio = AudioPlayerFeature.State()
        /// Mirrors `audio.isActive` so the shell can show the mini player without
        /// observing the player's time ticks.
        public var isListening = false
        public var homePath = StackState<Path.State>()
        public var explorePath = StackState<Path.State>()

        public init() {}
    }

    public enum Tab: Equatable, Sendable, CaseIterable {
        case home, explore, journey, library, search
    }

    @Reducer(state: .equatable, action: .equatable)
    public enum Path {
        case reader(ScriptureFeature)
        case entity(EntityDetailFeature)
        case entities(EntityListFeature)
        case books(BookPickerFeature)
        case context(ContextFeature)
        case arrival(GuidedExplorationFeature)
        case graph(GraphFeature)
        case timeline(TimelineFeature)
    }

    public enum Action: Equatable {
        case task
        case tabChanged(Tab)
        /// The user tapped a verse-of-the-day notification.
        case openedVerse(PassageReference)
        case home(HomeFeature.Action)
        case explore(ExploreFeature.Action)
        case search(SearchFeature.Action)
        case audio(AudioPlayerFeature.Action)
        case homePath(StackActionOf<Path>)
        case explorePath(StackActionOf<Path>)
    }

    @Dependency(\.notificationClient) var notificationClient

    public init() {}

    public var body: some ReducerOf<Self> {
        Scope(state: \.home, action: \.home) { HomeFeature() }
        Scope(state: \.explore, action: \.explore) { ExploreFeature() }
        Scope(state: \.search, action: \.search) { SearchFeature() }
        Scope(state: \.audio, action: \.audio) { AudioPlayerFeature() }
        Reduce { state, action in
            switch action {
            case .task:
                return .run { [notificationClient] send in
                    for await reference in notificationClient.openedVerses() {
                        await send(.openedVerse(reference))
                    }
                }

            case .tabChanged(let tab):
                state.tab = tab
                if tab == .home || tab == .explore { state.contentTab = tab }
                return .none

            // A notification lands on Home, on top of whatever was there.
            case .openedVerse(let reference):
                state.tab = .home
                state.contentTab = .home
                state.homePath.append(.reader(ScriptureFeature.State(reference: reference)))
                return .none

            // Home
            case .home(.delegate(.openPassage(let reference))):
                state.homePath.append(.reader(ScriptureFeature.State(reference: reference)))
                return .none

            case .home(.delegate(.openSearch)):
                state.tab = .search
                return .none
            case .home(.delegate(.openArrival)):
                state.homePath.append(.arrival(GuidedExplorationFeature.State()))
                return .none

            // Explore
            case .explore(.delegate(.open(let entry))):
                if let type = entry.entityType {
                    state.explorePath.append(.entities(EntityListFeature.State(type: type)))
                } else if entry == .timeline {
                    state.explorePath.append(.timeline(TimelineFeature.State()))
                } else {
                    state.explorePath.append(.books(BookPickerFeature.State(current: state.home.lastRead ?? PassageReference(bookId: "John", chapter: 3))))
                }
                return .none

            // Search: results open on the tab you came from, so Back returns to your list.
            case .search(.delegate(.openPassage(let reference))):
                push(.reader(ScriptureFeature.State(reference: reference)), in: &state)
                return .none

            case .search(.delegate(.openEntity(let entity))):
                push(.entity(EntityDetailFeature.State(entityID: entity.id)), in: &state)
                return .none

            // Mini player: go back to the chapter being heard, on the content tab.
            case .audio(.delegate(.openChapter(let reference))):
                push(.reader(ScriptureFeature.State(reference: reference)), in: &state)
                return .none

            // Destinations, on either stack
            case .homePath(.element(_, action: let action)):
                return handle(action, path: \.homePath, state: &state)

            case .explorePath(.element(_, action: let action)):
                return handle(action, path: \.explorePath, state: &state)

            case .audio:
                state.isListening = state.audio.isActive
                return .none

            case .home, .explore, .search, .homePath, .explorePath:
                return .none
            }
        }
        .forEach(\.homePath, action: \.homePath)
        .forEach(\.explorePath, action: \.explorePath)
    }

    /// Search lives on its own tab; what it opens lands on the last content
    /// tab, which becomes the visible one.
    private func push(_ destination: Path.State, in state: inout State) {
        state.tab = state.contentTab
        if state.contentTab == .explore {
            state.explorePath.append(destination)
        } else {
            state.homePath.append(destination)
        }
    }

    private func handle(_ action: Path.Action, path: WritableKeyPath<State, StackState<Path.State>>, state: inout State) -> Effect<Action> {
        switch action {
        case .entity(.delegate(.openEntity(let entity))),
             .context(.delegate(.openEntity(let entity))),
             .graph(.delegate(.openEntity(let entity))),
             .entities(.delegate(.openEntity(let entity))):
            state[keyPath: path].append(.entity(EntityDetailFeature.State(entityID: entity.id)))
        case .entity(.delegate(.openPassage(let reference))),
             .arrival(.delegate(.openPassage(let reference))),
             .graph(.delegate(.openPassage(let reference))),
             .context(.delegate(.openPassage(let reference))):
            state[keyPath: path].append(.reader(ScriptureFeature.State(reference: reference)))
        case .entity(.delegate(.openGraph(let id))):
            state[keyPath: path].append(.graph(GraphFeature.State(rootID: id)))
        case .entity(.delegate(.openTimeline(let id))):
            state[keyPath: path].append(.timeline(TimelineFeature.State(highlight: id)))
        case .timeline(.delegate(.openEntity(let id))):
            state[keyPath: path].append(.entity(EntityDetailFeature.State(entityID: id)))
        case .graph(.delegate(.focus(let entity))):
            state[keyPath: path].append(.graph(GraphFeature.State(rootID: entity.id)))
        case .reader(.delegate(.openContext(let reference))), .arrival(.delegate(.openContext(let reference))):
            state[keyPath: path].append(.context(ContextFeature.State(reference: reference)))
        case .books(.delegate(.chapterSelected(let reference))):
            state[keyPath: path].append(.reader(ScriptureFeature.State(reference: reference)))
        case .reader(.delegate(.listen(let reference))):
            // Already playing this chapter: the button pauses/resumes instead.
            if state.audio.reference == PassageReference(bookId: reference.bookId, chapter: reference.chapter) {
                return .send(.audio(.togglePlayPause))
            }
            return .send(.audio(.play(reference)))
        default:
            break
        }
        return .none
    }
}
