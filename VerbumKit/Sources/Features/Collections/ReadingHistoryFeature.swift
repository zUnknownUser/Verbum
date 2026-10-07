import Clients
import ComposableArchitecture
import Foundation
import Models

@Reducer
public struct ReadingHistoryFeature {
    @ObservableState
    public struct State: Equatable {
        @Shared(.readingActivity) var activity
        var query = ""
        var visibleCount = 20
        public init() {}

        var matchingVisits: [ReadingActivity.Visit] {
            let terms = query.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: .current)
                .split(whereSeparator: { $0.isWhitespace })
            return activity.visits.filter { visit in
                let book = BibleBook.book(id: visit.reference.bookId)
                let names = ([visit.reference.bookId] + BookLanguage.allCases.map { book?.localizedName(for: $0) ?? "" })
                    .joined(separator: " ").folding(options: [.caseInsensitive, .diacriticInsensitive], locale: .current)
                return terms.allSatisfy { term in
                    if let number = Int(term) {
                        return number == visit.reference.chapter || names.split(separator: " ").contains(term)
                    }
                    return names.contains(term)
                }
            }
        }
        var visibleVisits: [ReadingActivity.Visit] { Array(matchingVisits.prefix(visibleCount)) }
        var hasMore: Bool { matchingVisits.count > visibleCount }
    }

    public enum Action: Equatable {
        case queryChanged(String), showMore, open(PassageReference)
        case delegate(Delegate)
        @CasePathable public enum Delegate: Equatable { case open(PassageReference) }
    }

    public init() {}
    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .queryChanged(let query):
                state.query = query
                state.visibleCount = 20
                return .none
            case .showMore:
                state.visibleCount += 20
                return .none
            case .open(let reference):
                return .send(.delegate(.open(reference)))
            case .delegate:
                return .none
            }
        }
    }
}
