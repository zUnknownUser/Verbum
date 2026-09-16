import ComposableArchitecture
import DesignSystem
import SwiftUI

/// Tabs on a phone, a sidebar on iPad or an unfolded phone — same state,
/// same hierarchy (`.sidebarAdaptable`). Search is the system search tab.
public struct AppView: View {
    @Bindable var store: StoreOf<AppFeature>

    public init(store: StoreOf<AppFeature>) {
        self.store = store
    }

    public var body: some View {
        Group {
            if #available(iOS 26.0, *), store.isListening && !isReaderFocused {
                tabs.tabViewBottomAccessory {
                    MiniPlayerView(store: store.scope(state: \.audio, action: \.audio))
                }
            } else {
                tabs
            }
        }
        .environment(\.audioReading, store.audio.readingPosition)
        .sheet(item: $store.scope(state: \.voice, action: \.voice)) { voice in
            VoiceView(store: voice)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .presentationBackground(Palette.paper)
        }
        .task { await store.send(.task).finish() }
    }

    private var isReaderFocused: Bool {
        guard store.tab == .home || store.tab == .explore else { return false }
        let path = store.tab == .home ? store.homePath : store.explorePath
        guard let destination = path.last, case .reader(let reader) = destination else { return false }
        return reader.reader.focusMode
    }

    @ViewBuilder
    private var tabs: some View {
        if #available(iOS 26.0, *) {
            modernTabs.tabBarMinimizeBehavior(.onScrollDown)
        } else if #available(iOS 18.0, *) {
            modernTabs
        } else {
            TabView(selection: $store.tab.sending(\.tabChanged)) {
                home.tabItem { Label(L10n.t("Home"), systemImage: "house") }
                    .tag(AppFeature.Tab.home)
                explore.tabItem { Label(L10n.t("Explore"), systemImage: "point.3.connected.trianglepath.dotted") }
                    .tag(AppFeature.Tab.explore)
                journey.tabItem { Label(L10n.t("Journey"), systemImage: "point.topleft.down.to.point.bottomright.curvepath") }
                    .tag(AppFeature.Tab.journey)
                library.tabItem { Label(L10n.t("Library"), systemImage: "books.vertical") }
                    .tag(AppFeature.Tab.library)
                search.tabItem { Label(L10n.t("Search"), systemImage: "magnifyingglass") }
                    .tag(AppFeature.Tab.search)
            }
            .tint(Palette.accent)
        }
    }

    @available(iOS 18.0, *)
    private var modernTabs: some View {
        TabView(selection: $store.tab.sending(\.tabChanged)) {
            Tab(L10n.t("Home"), systemImage: "house", value: AppFeature.Tab.home) { home }
            Tab(L10n.t("Explore"), systemImage: "point.3.connected.trianglepath.dotted", value: AppFeature.Tab.explore) { explore }
            Tab(L10n.t("Journey"), systemImage: "point.topleft.down.to.point.bottomright.curvepath", value: AppFeature.Tab.journey) { journey }
            Tab(L10n.t("Library"), systemImage: "books.vertical", value: AppFeature.Tab.library) { library }
            if #available(iOS 26.0, *) {
                Tab(value: AppFeature.Tab.search, role: .search) { search }
            } else {
                Tab(L10n.t("Search"), systemImage: "magnifyingglass", value: AppFeature.Tab.search) { search }
            }
        }
        .tabViewStyle(.sidebarAdaptable)
        .tint(Palette.accent)
    }

    private var home: some View {
        withLegacyPlayer {
            NavigationStack(path: $store.scope(state: \.homePath, action: \.homePath)) {
                HomeView(store: store.scope(state: \.home, action: \.home))
            } destination: { PathView(store: $0) }
        }
    }

    private var explore: some View {
        withLegacyPlayer {
            NavigationStack(path: $store.scope(state: \.explorePath, action: \.explorePath)) {
                ExploreView(store: store.scope(state: \.explore, action: \.explore))
            } destination: { PathView(store: $0) }
        }
    }

    private var journey: some View {
        withLegacyPlayer { NavigationStack { JourneyView() } }
    }

    private var library: some View {
        withLegacyPlayer { NavigationStack { LibraryView() } }
    }

    private var search: some View {
        withLegacyPlayer {
            NavigationStack { SearchView(store: store.scope(state: \.search, action: \.search)) }
        }
    }

    @ViewBuilder
    private func withLegacyPlayer<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        if #available(iOS 26.0, *) {
            content()
        } else {
            // Inset each tab's content so the player sits above the system tab bar.
            content().safeAreaInset(edge: .bottom, spacing: 0) {
                if store.isListening && !isReaderFocused {
                    MiniPlayerView(store: store.scope(state: \.audio, action: \.audio))
                        .padding(.vertical, Spacing.md)
                        .background(.regularMaterial)
                }
            }
        }
    }

}

/// One view per destination kind.
struct PathView: View {
    let store: StoreOf<AppFeature.Path>

    var body: some View {
        switch store.case {
        case .reader(let store):
            ScriptureView(store: store)
        case .entity(let store):
            EntityDetailView(store: store)
        case .entities(let store):
            EntityListView(store: store)
        case .books(let store):
            BookPickerView(store: store)
        case .context(let store):
            ContextView(store: store)
        case .arrival(let store):
            GuidedExplorationView(store: store)
        case .graph(let store):
            GraphView(store: store)
        case .timeline(let store):
            TimelineView(store: store)
        case .ask(let store):
            AskView(store: store)
        }
    }
}
