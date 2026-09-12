import ComposableArchitecture
import Models

/// The discovery surface (spec §7): the paths into the knowledge graph, and
/// the timeline. The graph itself is entered from an entity (§21.4).
@Reducer
public struct ExploreFeature {
    @ObservableState
    public struct State: Equatable {
        public init() {}
    }

    public enum Entry: Equatable, Sendable, CaseIterable {
        case people, places, themes, events, timeline, books

        public var entityType: BibleEntityType? {
            switch self {
            case .people: .person
            case .places: .place
            case .themes: .theme
            case .events: .event
            case .timeline, .books: nil
            }
        }
    }

    public enum Action: Equatable {
        case entryTapped(Entry)
        case delegate(Delegate)

        @CasePathable
        public enum Delegate: Equatable {
            case open(Entry)
        }
    }

    public init() {}

    public var body: some ReducerOf<Self> {
        Reduce { _, action in
            switch action {
            case .entryTapped(let entry):
                return .send(.delegate(.open(entry)))
            case .delegate:
                return .none
            }
        }
    }
}
