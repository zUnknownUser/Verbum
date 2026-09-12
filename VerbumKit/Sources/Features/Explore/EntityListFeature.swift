import Clients
import ComposableArchitecture
import Models

/// Every entity of one kind — People, Places, Themes, Events (spec §7).
@Reducer
public struct EntityListFeature {
    @ObservableState
    public struct State: Equatable {
        public let type: BibleEntityType
        public var entities: [BibleEntity] = []
        public var isLoading = false

        public init(type: BibleEntityType) {
            self.type = type
        }
    }

    public enum Action: Equatable {
        case task
        case entitiesResponse([BibleEntity])
        case entityTapped(BibleEntity)
        case delegate(Delegate)

        @CasePathable
        public enum Delegate: Equatable {
            case openEntity(BibleEntity)
        }
    }

    @Dependency(\.graphClient) var graphClient

    public init() {}

    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .task:
                state.isLoading = true
                let type = state.type
                return .run { [graphClient] send in
                    await send(.entitiesResponse((try? await graphClient.entities(type: type)) ?? []))
                }

            case .entitiesResponse(let entities):
                state.isLoading = false
                state.entities = entities
                return .none

            case .entityTapped(let entity):
                return .send(.delegate(.openEntity(entity)))

            case .delegate:
                return .none
            }
        }
    }
}
