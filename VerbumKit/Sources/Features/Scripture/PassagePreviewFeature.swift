import Clients
import ComposableArchitecture
import Models

/// A reference preview that leaves the presenting study and its reading position
/// intact. Scripture continues to come through the existing BibleClient.
@Reducer
public struct PassagePreviewFeature {
    @ObservableState
    public struct State: Equatable {
        public let reference: PassageReference
        public var passage: BiblePassage?
        public var loading = false
        public var error: ReaderError?

        public init(reference: PassageReference) { self.reference = reference }
    }

    public enum Action: Equatable {
        case task, retry, close, openReader
        case response(PassageReference, Result<BiblePassage, ReaderError>)
        case delegate(Delegate)
        @CasePathable public enum Delegate: Equatable {
            case openReader(PassageReference)
        }
    }

    @Dependency(\.bibleClient) var bibleClient
    @Dependency(\.dismiss) var dismiss
    public init() {}

    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .task, .retry:
                if case .task = action, state.loading || state.passage != nil || state.error != nil { return .none }
                state.loading = true
                state.error = nil
                return .run { [bibleClient, reference = state.reference] send in
                    do { await send(.response(reference, .success(try await bibleClient.passage(reference: reference)))) }
                    catch is CancellationError {} catch { await send(.response(reference, .failure(ReaderError(error)))) }
                }
                .cancellable(id: CancelID.load, cancelInFlight: true)
            case .response(let reference, let result):
                guard state.reference == reference else { return .none }
                state.loading = false
                switch result {
                case .success(let passage): state.passage = passage; state.error = nil
                case .failure(let error): state.error = error
                }
                return .none
            case .close:
                return .run { [dismiss] _ in await dismiss() }
            case .openReader:
                return .send(.delegate(.openReader(state.reference)))
            case .delegate:
                return .none
            }
        }
    }

    private enum CancelID { case load }
}
