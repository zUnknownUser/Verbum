import Clients
import ComposableArchitecture
import Models

@Reducer
public struct ReadingCollectionFeature {
    @ObservableState
    public struct State: Equatable {
        @Shared(.readingActivity) var activity
        @Shared(.lastRead) var lastRead
        public var annotations: [ReaderAnnotation] = []
        public var query = ""
        public var filter = ReadingCollectionFilter.all
        public var loading = false
        public var failed = false
        public init() {}
        var entries: [ReaderAnnotation] { ReadingCollection.entries(annotations, filter: filter, query: query) }
    }
    public enum Action: Equatable {
        case task, retry
        case loaded([ReaderAnnotation]), failed
        case queryChanged(String), filterChanged(ReadingCollectionFilter)
        case open(PassageReference), browse
        case delegate(Delegate)
        @CasePathable public enum Delegate: Equatable {
            case open(PassageReference), browse
        }
    }
    @Dependency(\.readerAnnotations) var annotations
    public init() {}
    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .task, .retry:
                state.loading = true; state.failed = false
                return .run { [annotations] send in
                    do { await send(.loaded(try await annotations.load())) }
                    catch is CancellationError {} catch { await send(.failed) }
                }.cancellable(id: CancelID.load, cancelInFlight: true)
            case .loaded(let values):
                state.annotations = values; state.loading = false; state.failed = false
                return .none
            case .failed: state.loading = false; state.failed = true; return .none
            case .queryChanged(let query): state.query = query; return .none
            case .filterChanged(let filter): state.filter = filter; return .none
            case .open(let reference): return .send(.delegate(.open(reference)))
            case .browse: return .send(.delegate(.browse))
            case .delegate: return .none
            }
        }
    }
    private enum CancelID { case load }
}
