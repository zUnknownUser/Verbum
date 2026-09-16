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
        @Shared(.readingActivity) var readingActivity
        public var content: Content = .idle
        public var selectedVerses: Set<Int> = []
        public var requestedVerses: ClosedRange<Int>?
        @Shared(.readerTextScale) public var textScale
        @Shared(.lastRead) public var lastRead
        @Shared(.readingMode) public var readingMode
        @Shared(.readerFocusMode) public var focusMode
        @Presents public var study: VerseStudyFeature.State?
        public var chapters: [String: [BiblePassage]] = [:]
        public var chapterErrors: [String: ReaderError] = [:]
        public var loadingChapters: Set<String> = []
        public var contexts: [String: PassageContext] = [:]
        public var mentions: [String: [StudyTextSegment]] = [:]
        public var annotations: [String: ReaderAnnotation] = [:]
        public var annotationLoadFailed = false
        public var flow: [PassageReference] = []
        public var history: [Visit] = []
        public var scrollOffsets: [String: Double] = [:]
        public var restoreOffset: Double?
        public var navigationRevision = 0
        public struct Visit: Equatable, Sendable {
            public var reference: PassageReference
            public var flow: [PassageReference]
            public var offset: Double
        }

        public init(reference: PassageReference) {
            self.reference = PassageReference(bookId: reference.bookId, chapter: reference.chapter)
            self.requestedVerses = reference.verses
            self.flow = [self.reference]
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
        case task, recordReading
        case ensureChapter(PassageReference)
        case cachedChapter(PassageReference, Result<[BiblePassage], ReaderError>)
        case contextResponse(PassageReference, PassageContext?)
        case annotationsResponse([ReaderAnnotation]), annotationsFailed
        case study(PresentationAction<VerseStudyFeature.Action>)
        case studyVerse(PassageReference, Int, [String])
        case appendChapter
        case chapterVisible(PassageReference)
        case visit(PassageReference), backToReading, focusToggled, readingModeChanged
        case scrollOffsetChanged(String, Double)
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

    @Dependency(\.date.now) var now
    @Dependency(\.calendar) var calendar
    @Dependency(\.bibleClient) var bibleClient
    @Dependency(\.pasteboard) var pasteboard
    @Dependency(\.contextClient) var contextClient
    @Dependency(\.readerAnnotations) var readerAnnotations

    public init() {}

    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .recordReading:
                guard state.chapters[ReaderCanon.key(state.reference)]?.isEmpty == false else { return .none }
                state.$readingActivity.withLock { $0.record(state.reference, at: now, calendar: calendar) }
                return .none
            case .task:
                let annotationEffect: Effect<Action> = .run { [readerAnnotations] send in
                    do { await send(.annotationsResponse(try await readerAnnotations.load())) }
                    catch { await send(.annotationsFailed) }
                }.cancellable(id: CancelID.annotations, cancelInFlight: true)
                if case .loaded = state.content { return annotationEffect }
                return .merge(load(&state), annotationEffect)
            case .annotationsResponse(let values):
                state.annotations = Dictionary(values.map { ($0.id, $0) }, uniquingKeysWith: { _, newest in newest })
                state.annotationLoadFailed = false; return .none
            case .annotationsFailed: state.annotationLoadFailed = true; return .none
            case .ensureChapter(let reference):
                let key = ReaderCanon.key(reference)
                guard state.chapters[key] == nil, !state.loadingChapters.contains(key) else { return .none }
                state.loadingChapters.insert(key)
                return .run { [bibleClient] send in
                    do { await send(.cachedChapter(reference, .success(try await bibleClient.chapter(bookId: reference.bookId, chapter: reference.chapter)))) }
                    catch is CancellationError {} catch { await send(.cachedChapter(reference, .failure(ReaderError(error)))) }
                }.cancellable(id: key, cancelInFlight: true)
            case .cachedChapter(let reference, let result):
                let key = ReaderCanon.key(reference); state.loadingChapters.remove(key)
                switch result {
                case .success(let verses):
                    state.chapters[key] = verses; state.chapterErrors[key] = nil
                    if state.reference == reference { return .send(.chapterResponse(.success(verses))) }
                case .failure(let error):
                    state.chapterErrors[key] = error
                    if state.reference == reference { state.content = .failed(error) }
                }
                return .none
            case .contextResponse(let reference, let context):
                if let context {
                    let key = ReaderCanon.key(reference)
                    state.contexts[key] = context
                    for verse in state.chapters[key] ?? [] {
                        state.mentions["\(verse.bookId).\(verse.chapter).\(verse.verseStart)"] = ReaderEntityLinker.segments(verse.text, entities: context.entities)
                    }
                }
                return .none
            case .studyVerse(let reference, let number, let ids):
                guard let verse = state.chapters[ReaderCanon.key(reference)]?.first(where: { $0.verseStart == number }) else { return .none }
                let key = "\(reference.bookId).\(reference.chapter).\(number)"
                let context = state.contexts[ReaderCanon.key(reference)]
                state.study = VerseStudyFeature.State(passage: verse, annotation: state.annotations[key], context: context,
                    candidates: context?.entities.filter { ids.contains($0.id) } ?? [])
                return .none
            case .study(.presented(.delegate(.annotationSaved(let annotation)))):
                state.annotations[annotation.id] = annotation; return .none
            case .study(.presented(.delegate(.openPassage(let reference)))):
                state.study = nil; return .send(.visit(reference))
            case .study(.presented(.delegate(.close))): state.study = nil; return .none
            case .study: return .none
            case .visit(let reference):
                state.history.append(State.Visit(reference: state.reference, flow: state.flow,
                    offset: state.scrollOffsets[state.readingMode == .continuous ? "flow" : ReaderCanon.key(state.reference)] ?? 0))
                if state.history.count > 32 { state.history.removeFirst() }
                return jump(&state, to: reference)
            case .backToReading:
                guard let visit = state.history.popLast() else { return .none }
                let effect = jump(&state, to: visit.reference)
                state.flow = visit.flow; state.restoreOffset = visit.offset
                return effect
            case .scrollOffsetChanged(let key, let offset): state.scrollOffsets[key] = offset; return .none
            case .focusToggled: state.$focusMode.withLock { $0.toggle() }; return .none
            case .readingModeChanged:
                state.flow = [state.reference]; state.navigationRevision += 1
                state.restoreOffset = 0; state.requestedVerses = nil
                return .none
            case .appendChapter:
                guard let last = state.flow.last, state.chapters[ReaderCanon.key(last)] != nil,
                      let next = ChapterNavigation.next(after: last) else { return .none }
                state.flow.append(next)
                return .send(.ensureChapter(next))
            case .chapterVisible(let reference):
                guard state.readingMode == .continuous, state.reference != reference else { return .none }
                state.reference = reference
                if let verses = state.chapters[ReaderCanon.key(reference)] { state.content = .loaded(verses) }
                state.$lastRead.withLock { $0 = reference }
                return contextEffect(reference)

            case .retryTapped:
                return load(&state)

            case .chapterResponse(.success(let verses)):
                if let first = verses.first, first.bookId != state.reference.bookId || first.chapter != state.reference.chapter { return .none }
                state.loadingChapters.remove(ReaderCanon.key(state.reference))
                state.content = .loaded(verses)
                state.chapters[ReaderCanon.key(state.reference)] = verses
                if let requested = state.requestedVerses {
                    state.selectedVerses = Set(verses.map(\.verseStart).filter { requested.contains($0) })
                }
                let reference = state.reference
                state.$lastRead.withLock { $0 = reference }
                let neighbors = [ChapterNavigation.previous(before: reference), ChapterNavigation.next(after: reference)].compactMap { $0 }
                return .merge([contextEffect(reference)] + neighbors.map { .send(.ensureChapter($0)) })

            case .chapterResponse(.failure(let error)):
                state.content = .failed(error)
                state.loadingChapters.remove(ReaderCanon.key(state.reference))
                state.chapterErrors[ReaderCanon.key(state.reference)] = error
                return .none

            case .verseTapped(let verse):
                return .send(.studyVerse(state.reference, verse, []))

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
        }.ifLet(\.$study, action: \.study) { VerseStudyFeature() }
    }

    private func contextEffect(_ reference: PassageReference) -> Effect<Action> {
        .run { [contextClient] send in
            let context = try? await contextClient.chapter(reference: reference)
            await send(.contextResponse(reference, context))
        }.cancellable(id: "context-" + ReaderCanon.key(reference), cancelInFlight: true)
    }

    private func jump(_ state: inout State, to reference: PassageReference) -> Effect<Action> {
        state.reference = PassageReference(bookId: reference.bookId, chapter: reference.chapter)
        state.requestedVerses = reference.verses
        state.selectedVerses = []
        state.flow = [state.reference]
        state.navigationRevision += 1
        state.restoreOffset = reference.verses == nil ? (state.readingMode == .continuous ? 0 : state.scrollOffsets[ReaderCanon.key(state.reference)] ?? 0) : nil
        if let verses = state.chapters[ReaderCanon.key(state.reference)] {
            state.content = .loaded(verses)
            state.$lastRead.withLock { $0 = state.reference }
            return .merge(contextEffect(state.reference), .send(.ensureChapter(ChapterNavigation.next(after: state.reference) ?? state.reference)), .send(.ensureChapter(ChapterNavigation.previous(before: state.reference) ?? state.reference)))
        }
        return load(&state)
    }

    private func load(_ state: inout State) -> Effect<Action> {
        state.content = .loading
        // Every response carries its chapter identity, including rapid navigation.
        // Shared per-chapter requests finish into the cache without changing another page.
        return .send(.ensureChapter(state.reference))
    }

    private enum CancelID { case load, annotations }
}

extension Result where Failure == any Error {
    fileprivate func mapError<E: Error>(_ transform: (any Error) -> E) -> Result<Success, E> {
        switch self {
        case .success(let value): .success(value)
        case .failure(let error): .failure(transform(error))
        }
    }
}
