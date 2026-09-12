import Clients
import ComposableArchitecture
import Models

@Reducer
public struct ContextFeature {
    @ObservableState
    public struct State: Equatable {
        public let reference: PassageReference
        public var content: Content = .idle

        public init(reference: PassageReference) {
            self.reference = PassageReference(bookId: reference.bookId, chapter: reference.chapter)
        }
    }

    public enum Content: Equatable, Sendable {
        case idle, loading, unavailable, failed
        case loaded(PassageContext)
    }

    public enum Action: Equatable {
        case task, retryTapped
        case response(Result<PassageContext?, Failure>)
        case passageTapped(PassageReference)
        case entityTapped(BibleEntity)
        case delegate(Delegate)

        @CasePathable
        public enum Delegate: Equatable {
            case openPassage(PassageReference)
            case openEntity(BibleEntity)
        }
    }

    public struct Failure: Error, Equatable, Sendable {}
    @Dependency(\.contextClient) var contextClient
    public init() {}

    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .task:
                guard state.content == .idle else { return .none }
                return load(&state)
            case .retryTapped:
                return load(&state)
            case .response(.success(let context)):
                state.content = context.map(Content.loaded) ?? .unavailable
                return .none
            case .response(.failure):
                state.content = .failed
                return .none
            case .passageTapped(let reference):
                return .send(.delegate(.openPassage(reference)))
            case .entityTapped(let entity):
                return .send(.delegate(.openEntity(entity)))
            case .delegate:
                return .none
            }
        }
    }

    private func load(_ state: inout State) -> Effect<Action> {
        state.content = .loading
        return .run { [contextClient, reference = state.reference] send in
            do {
                let context = try await contextClient.chapter(reference)
                try Task.checkCancellation()
                await send(.response(.success(context)))
            } catch is CancellationError {
                return
            } catch {
                await send(.response(.failure(Failure())))
            }
        }
        .cancellable(id: CancelID.load, cancelInFlight: true)
    }

    private enum CancelID { case load }
}
