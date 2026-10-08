import Clients
import ComposableArchitecture
import Models

@Reducer
public struct EntityListFeature {
    @ObservableState
    public struct State: Equatable {
        public let type: BibleEntityType
        public var entities: [BibleEntity] = []
        public var isLoading = false
        public var query = ""
        public var category = ""
        public var browsingThemes = false
        public var letter = ""
        public var letters: [String] = []
        public var nextOffset: Int?
        public var hasLoaded = false
        public var failed = false
        var generation = 0
        var loadingOffset = 0
        public init(type: BibleEntityType) { self.type = type }
    }

    public enum Action: Equatable {
        case task, cancel, retry, loadMore, discoverThemes
        case categoryChanged(String)
        case queryChanged(String), letterChanged(String)
        case pageResponse(Int, Int, EntityCatalogPage)
        case failed(Int)
        case entitiesResponse([BibleEntity])
        case entityTapped(BibleEntity)
        case delegate(Delegate)
        @CasePathable public enum Delegate: Equatable { case openEntity(BibleEntity) }
    }

    @Dependency(\.graphClient) var graphClient
    @Dependency(\.continuousClock) var clock
    public init() {}

    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .task:
                guard !state.hasLoaded, !state.isLoading else { return .none }
                return load(&state)
            case .retry:
                guard !state.isLoading else { return .none }
                return load(&state, offset: state.loadingOffset)
            case .loadMore:
                guard !state.isLoading, let offset = state.nextOffset else { return .none }
                return load(&state, offset: offset)
            case .discoverThemes:
                state.browsingThemes = false; state.category = ""; state.query = ""; state.letter = ""
                return reset(&state)
            case .categoryChanged(let category):
                state.category = category; state.browsingThemes = true; state.query = ""; state.letter = ""
                return reset(&state)
            case .queryChanged(let query):
                guard query != state.query else { return .none }
                state.query = String(query.prefix(100)); state.letter = ""
                return reset(&state, debounce: true)
            case .letterChanged(let letter):
                guard letter != state.letter else { return .none }
                state.letter = letter
                return reset(&state)
            case .cancel:
                state.isLoading = false; state.generation += 1
                return .cancel(id: CancelID.catalog)
            case let .pageResponse(generation, offset, page):
                guard generation == state.generation else { return .none }
                state.isLoading = false; state.hasLoaded = true; state.failed = false
                if offset == 0 { state.entities = [] }
                var ids = Set(state.entities.map(\.id))
                state.entities += page.entities.filter { ids.insert($0.id).inserted }
                state.letters = page.letters
                state.nextOffset = page.nextOffset.flatMap { $0 > offset ? $0 : nil }
                return .none
            case .failed(let generation):
                guard generation == state.generation else { return .none }
                state.isLoading = false; state.failed = true
                return .none
            case .entitiesResponse(let entities):
                state.isLoading = false; state.hasLoaded = true; state.entities = entities
                return .none
            case .entityTapped(let entity): return .send(.delegate(.openEntity(entity)))
            case .delegate: return .none
            }
        }
    }

    private func reset(_ state: inout State, debounce: Bool = false) -> Effect<Action> {
        state.entities = []; state.nextOffset = nil; state.hasLoaded = false
        return load(&state, debounce: debounce)
    }

    private func load(_ state: inout State, offset: Int = 0, debounce: Bool = false) -> Effect<Action> {
        state.isLoading = true; state.failed = false; state.loadingOffset = offset; state.generation += 1
        let generation = state.generation
        let request = EntityCatalogRequest(type: state.type, query: state.query, letter: state.letter, offset: offset, category: state.category)
        return .run { [graphClient, clock] send in
            do {
                if debounce { try await clock.sleep(for: .milliseconds(300)) }
                if request.type == .person || request.type == .place || request.type == .theme || request.type == .event {
                    let page = try await graphClient.entityPage(request)
                    try Task.checkCancellation()
                    await send(.pageResponse(generation, offset, page))
                } else {
                    let entities = try await graphClient.entities(type: request.type)
                    try Task.checkCancellation()
                    await send(.entitiesResponse(entities))
                }
            } catch is CancellationError { }
            catch { await send(.failed(generation)) }
        }.cancellable(id: CancelID.catalog, cancelInFlight: true)
    }
    private enum CancelID { case catalog }
}
