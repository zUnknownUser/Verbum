import Clients
import ComposableArchitecture
import Models

@Reducer
public struct GuidedExplorationFeature {
    @ObservableState
    public struct State: Equatable {
        public var feeling: ArrivalFeeling?
        public var plan: ExplorationPlan?
        public var isLoading = false
        public var failed = false
        public init() {}
    }
    public enum Action: Equatable {
        case select(ArrivalFeeling)
        case response(ArrivalFeeling, ExplorationPlan?)
        case changeFeeling
        case delegate(Delegate)
        @CasePathable public enum Delegate: Equatable {
            case openPassage(PassageReference)
            case openContext(PassageReference)
        }
    }
    @Dependency(\.guidedExploration) var client
    public init() {}
    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .select(let feeling):
                state.feeling = feeling
                state.plan = nil
                state.isLoading = true
                state.failed = false
                return .run { [client] send in
                    let plan = try? await client.explore(.init(feeling: feeling, language: .current))
                    try Task.checkCancellation()
                    await send(.response(feeling, plan))
                }.cancellable(id: CancelID.load, cancelInFlight: true)
            case .response(let feeling, let plan):
                guard state.feeling == feeling else { return .none }
                state.isLoading = false
                state.plan = plan
                state.failed = plan == nil
                return .none
            case .changeFeeling:
                state = State()
                return .cancel(id: CancelID.load)
            case .delegate:
                return .none
            }
        }
    }
    private enum CancelID { case load }
}
