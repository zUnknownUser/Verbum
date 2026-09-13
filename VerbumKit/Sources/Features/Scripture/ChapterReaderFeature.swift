import Clients
import ComposableArchitecture
import Foundation
import Models

/// One chapter on screen: loads it, lets the reader select verses, steps to
/// the neighbouring chapters. Owns no navigation; the parent decides where
/// the reader goes.
@Reducer
public struct ChapterReaderFeature {
    @ObservableState
    public struct State: Equatable {
        public var reference: PassageReference
        public var content: Content = .idle
        public var selectedVerses: Set<Int> = []
        @Shared(.readerTextScale) public var textScale
        @Shared(.lastRead) public var lastRead

        public init(reference: PassageReference) {
            self.reference = PassageReference(bookId: reference.bookId, chapter: reference.chapter)
        }

        public var book: BibleBook? { BibleBook.book(id: reference.bookId) }
        public var title: String { reference.formatted }

        public var canGoToNextChapter: Bool { ChapterNavigation.next(after: reference) != nil }
        public var canGoToPreviousChapter: Bool { ChapterNavigation.previous(before: reference) != nil }

        /// `John 3:16-18, 21` while verses are selected.
        public var selectionCitation: String? {
            SelectionFormatter.format(bookId: reference.bookId, chapter: reference.chapter, verses: selectedVerses)
        }

        public var selectedText: String? {
            guard case .loaded(let verses) = content, !selectedVerses.isEmpty else { return nil }
            return verses
                .filter { selectedVerses.contains($0.verseStart) }
                .map(\.text)
                .joined(separator: " ")
        }
    }

    public enum Content: Equatable, Sendable {
        case idle
        case loading
        case loaded([BiblePassage])
        case failed(ReaderError)
    }

    public enum Action: Equatable {
        case task
        case retryTapped
        case chapterResponse(Result<[BiblePassage], ReaderError>)
        case verseTapped(Int)
        case clearSelectionTapped
        case copySelectionTapped
        case nextChapterTapped
        case previousChapterTapped
        /// Parent-driven jump (book picker). Reloads.
        case go(to: PassageReference)
        case listenTapped
        case talkTapped
        case contextTapped
        case delegate(Delegate)

        @CasePathable
        public enum Delegate: Equatable {
            case listen(PassageReference)
            /// Start a spoken conversation about this chapter.
            case talk(PassageReference)
            case openContext(PassageReference)
        }
    }

    @Dependency(\.bibleClient) var bibleClient
    @Dependency(\.pasteboard) var pasteboard

    public init() {}

    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .task:
                // Returning from context/entity navigation must preserve the
                // loaded page rather than replace it with a loading indicator.
                if case .loaded = state.content { return .none }
                return load(&state)

            case .retryTapped:
                return load(&state)

            case .chapterResponse(.success(let verses)):
                state.content = .loaded(verses)
                let reference = state.reference
                state.$lastRead.withLock { $0 = reference }
                return .none

            case .chapterResponse(.failure(let error)):
                state.content = .failed(error)
                return .none

            case .verseTapped(let verse):
                if state.selectedVerses.contains(verse) {
                    state.selectedVerses.remove(verse)
                } else {
                    state.selectedVerses.insert(verse)
                }
                return .none

            case .clearSelectionTapped:
                state.selectedVerses = []
                return .none

            case .copySelectionTapped:
                guard let citation = state.selectionCitation, let text = state.selectedText else { return .none }
                return .run { [pasteboard] _ in pasteboard.copy(text: "\(text)\n— \(citation)") }

            case .nextChapterTapped:
                guard let next = ChapterNavigation.next(after: state.reference) else { return .none }
                return jump(&state, to: next)

            case .previousChapterTapped:
                guard let previous = ChapterNavigation.previous(before: state.reference) else { return .none }
                return jump(&state, to: previous)

            case .go(let reference):
                return jump(&state, to: reference)

            case .listenTapped:
                return .send(.delegate(.listen(state.reference)))

            case .talkTapped:
                return .send(.delegate(.talk(state.reference)))

            case .contextTapped:
                return .send(.delegate(.openContext(state.reference)))

            case .delegate:
                return .none
            }
        }
    }

    private func jump(_ state: inout State, to reference: PassageReference) -> Effect<Action> {
        state.reference = PassageReference(bookId: reference.bookId, chapter: reference.chapter)
        state.selectedVerses = []
        return load(&state)
    }

    private func load(_ state: inout State) -> Effect<Action> {
        state.content = .loading
        let reference = state.reference
        return .run { [bibleClient] send in
            await send(.chapterResponse(Result {
                try await bibleClient.chapter(bookId: reference.bookId, chapter: reference.chapter)
            }.mapError(ReaderError.init)))
        }
        .cancellable(id: CancelID.load, cancelInFlight: true)
    }

    private enum CancelID { case load }
}

extension Result where Failure == any Error {
    fileprivate func mapError<E: Error>(_ transform: (any Error) -> E) -> Result<Success, E> {
        switch self {
        case .success(let value): .success(value)
        case .failure(let error): .failure(transform(error))
        }
    }
}
