import Clients
import ComposableArchitecture
import Foundation
import Models

/// Type a reference, a book, a person, a place or a theme (spec §27).
/// Debounced; a newer query cancels the search in flight. Results are
/// delivered to whoever hosts the feature through `delegate`.
@Reducer
public struct SearchFeature {
    @ObservableState
    public struct State: Equatable {
        public var query = ""
        public var phase: Phase = .idle
        public var results: SearchResponse?

        public init() {}

        /// `true` while the user has typed something that produced nothing.
        public var showsNoResults: Bool {
            if case .idle = phase, let results, results.isEmpty, !query.trimmingCharacters(in: .whitespaces).isEmpty {
                return true
            }
            return false
        }
    }

    public enum Phase: Equatable, Sendable {
        case idle
        case searching
    }

    public enum Action: Equatable, BindableAction {
        case binding(BindingAction<State>)
        case searchResponse(SearchResponse)
        case submitted
        case passageTapped(PassageReference)
        case bookTapped(BibleBook)
        case entityTapped(BibleEntity)
        case delegate(Delegate)

        @CasePathable
        public enum Delegate: Equatable {
            case openPassage(PassageReference)
            case openEntity(BibleEntity)
        }
    }

    @Dependency(\.searchClient) var searchClient
    @Dependency(\.continuousClock) var clock
    @Dependency(\.locale) var locale

    public init() {}

    /// How long typing may pause before we search.
    static let debounce: Duration = .milliseconds(250)

    public var body: some ReducerOf<Self> {
        BindingReducer()
        Reduce { state, action in
            switch action {
            case .binding(\.query):
                let query = state.query.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !query.isEmpty else {
                    state.phase = .idle
                    state.results = nil
                    return .cancel(id: CancelID.search)
                }
                state.phase = .searching
                return .run { [searchClient, clock] send in
                    try await clock.sleep(for: Self.debounce)
                    let response = (try? await searchClient.search(query: query)) ?? .empty(query)
                    await send(.searchResponse(response))
                }
                .cancellable(id: CancelID.search, cancelInFlight: true)

            case .binding:
                return .none

            case .searchResponse(let response):
                // Ignore answers to a query the user has since moved past.
                guard response.query == state.query.trimmingCharacters(in: .whitespacesAndNewlines) else { return .none }
                state.results = response
                state.phase = .idle
                return .none

            case .submitted:
                // Return key: a reference opens immediately, without waiting for results.
                if let reference = try? PassageReferenceParser.parse(state.query, language: BookLanguage(locale: locale)) {
                    return .send(.delegate(.openPassage(reference)))
                }
                if let book = state.results?.books.first {
                    return .send(.delegate(.openPassage(PassageReference(bookId: book.id, chapter: 1))))
                }
                return .none

            case .passageTapped(let reference):
                return .send(.delegate(.openPassage(reference)))

            case .bookTapped(let book):
                return .send(.delegate(.openPassage(PassageReference(bookId: book.id, chapter: 1))))

            case .entityTapped(let entity):
                return .send(.delegate(.openEntity(entity)))

            case .delegate:
                return .none
            }
        }
    }

    private enum CancelID { case search }
}
