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
            if store.isListening {
                tabs.tabViewBottomAccessory {
                    MiniPlayerView(store: store.scope(state: \.audio, action: \.audio))
                }
            } else {
                tabs
            }
        }
        .task { await store.send(.task).finish() }
    }

    private var tabs: some View {
        TabView(selection: $store.tab.sending(\.tabChanged)) {
            Tab(L10n.t("Home"), systemImage: "house", value: AppFeature.Tab.home) {
                NavigationStack(path: $store.scope(state: \.homePath, action: \.homePath)) {
                    HomeView(store: store.scope(state: \.home, action: \.home))
                } destination: { destination in
                    PathView(store: destination)
                }
            }
            Tab(L10n.t("Explore"), systemImage: "point.3.connected.trianglepath.dotted", value: AppFeature.Tab.explore) {
                NavigationStack(path: $store.scope(state: \.explorePath, action: \.explorePath)) {
                    ExploreView(store: store.scope(state: \.explore, action: \.explore))
                } destination: { destination in
                    PathView(store: destination)
                }
            }
            Tab(L10n.t("Journey"), systemImage: "point.topleft.down.to.point.bottomright.curvepath", value: AppFeature.Tab.journey) {
                NavigationStack { JourneyView() }
            }
            Tab(L10n.t("Library"), systemImage: "books.vertical", value: AppFeature.Tab.library) {
                NavigationStack { LibraryView() }
            }
            Tab(value: AppFeature.Tab.search, role: .search) {
                NavigationStack {
                    SearchView(store: store.scope(state: \.search, action: \.search))
                }
            }
        }
        .tabViewStyle(.sidebarAdaptable)
        .tabBarMinimizeBehavior(.onScrollDown)
        .tint(Palette.accent)
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
        }
    }
}
