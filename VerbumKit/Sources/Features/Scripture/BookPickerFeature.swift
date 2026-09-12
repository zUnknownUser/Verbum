import ComposableArchitecture
import Models

/// Two-step picker: book, then chapter. Navigation inside the column is state
/// (`selectedBook` drives the push). Emits a delegate action; the parent
/// decides what to do with it. `current` is the reference the reader is on,
/// so the column can mark "you are here".
@Reducer
public struct BookPickerFeature {
    @ObservableState
    public struct State: Equatable {
        public var current: PassageReference
        public var selectedBook: BibleBook?

        public init(current: PassageReference) {
            self.current = current
        }

        public var oldTestament: [BibleBook] { BibleBook.canon.filter { $0.testament == .old } }
        public var newTestament: [BibleBook] { BibleBook.canon.filter { $0.testament == .new } }
    }

    public enum Action: Equatable {
        case bookTapped(BibleBook)
        case backToBooksTapped
        case chapterTapped(Int)
        case delegate(Delegate)

        @CasePathable
        public enum Delegate: Equatable {
            case chapterSelected(PassageReference)
        }
    }

    public init() {}

    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .bookTapped(let book):
                state.selectedBook = book
                return .none

            case .backToBooksTapped:
                state.selectedBook = nil
                return .none

            case .chapterTapped(let chapter):
                guard let book = state.selectedBook, (1...book.chapterCount).contains(chapter) else { return .none }
                return .send(.delegate(.chapterSelected(PassageReference(bookId: book.id, chapter: chapter))))

            case .delegate:
                return .none
            }
        }
    }
}
