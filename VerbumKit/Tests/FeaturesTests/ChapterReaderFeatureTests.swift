import Clients
import ComposableArchitecture
import Models
import Testing
@testable import Features

@MainActor
@Suite struct ChapterReaderFeatureTests {
    nonisolated static func verses(_ bookId: BookID, _ chapter: Int, count: Int) -> [BiblePassage] {
        (1...count).map { BiblePassage(id: "t:\(bookId).\(chapter).\($0)", translationId: "t", bookId: bookId, chapter: chapter, verseStart: $0, verseEnd: $0, text: "v\($0)") }
    }
    private func store(_ state: ChapterReaderFeature.State) -> TestStoreOf<ChapterReaderFeature> {
        let store = TestStore(initialState: state) { ChapterReaderFeature() } withDependencies: {
            $0.bibleClient.chapter = { Self.verses($0, $1, count: 3) }
            $0.readerAnnotations = .init(load: { [] }, save: { _ in })
            $0.contextClient.chapter = { _ in nil }
        }
        // Assert public behavior; neighbor prefetch and optional context can complete independently.
        store.exhaustivity = .off(showSkippedAssertions: false)
        return store
    }
    @Test func loadsOnlyCurrentChapterAndNeighbors() async {
        let s = store(.init(reference: .init(bookId: "John", chapter: 3)))
        await s.send(.task)
        await s.receive(\.chapterResponse.success)
        #expect(s.state.content == .loaded(Self.verses("John", 3, count: 3)))
        #expect(s.state.lastRead == PassageReference(bookId: "John", chapter: 3))
        await s.finish()
    }
    @Test func lateResponseCannotReplaceAnotherChapter() async {
        var state = ChapterReaderFeature.State(reference: .init(bookId: "John", chapter: 4))
        state.content = .loaded(Self.verses("John", 4, count: 3))
        let s = store(state)
        await s.send(.cachedChapter(.init(bookId: "John", chapter: 3), .success(Self.verses("John", 3, count: 3))))
        #expect(s.state.content == state.content)
        #expect(s.state.chapters["John.3"] != nil)
        await s.send(.chapterResponse(.success(Self.verses("John", 3, count: 3))))
        #expect(s.state.content == state.content)
    }
    @Test func verseTapOpensStudyInPlaceWithExistingNote() async {
        let ref = PassageReference(bookId: "John", chapter: 3, verses: 2...2)
        var state = ChapterReaderFeature.State(reference: ref)
        state.chapters["John.3"] = Self.verses("John", 3, count: 3)
        state.annotations["John.3.2"] = ReaderAnnotation(reference: ref, highlight: .sage, note: "Minha nota")
        let s = store(state)
        await s.send(.studyVerse(state.reference, 2, []))
        #expect(s.state.study?.reference == ref)
        #expect(s.state.study?.annotation.note == "Minha nota")
        #expect(s.state.reference == state.reference)
        #expect(s.state.study?.ask == nil)
    }
    @Test func referenceHistoryRestoresExactOffsetAndContinuousFlow() async {
        var state = ChapterReaderFeature.State(reference: .init(bookId: "Gen", chapter: 50))
        let previous = PassageReference(bookId: "Rom", chapter: 8)
        state.history = [.init(reference: previous, flow: [previous, .init(bookId: "Rom", chapter: 9)], offset: 734.5)]
        state.chapters["Rom.8"] = Self.verses("Rom", 8, count: 3)
        let s = store(state)
        await s.send(.backToReading)
        #expect(s.state.reference == previous)
        #expect(s.state.restoreOffset == 734.5)
        #expect(s.state.flow.count == 2)
        #expect(s.state.history.isEmpty)
        await s.finish()
    }
    @Test func failedNeighborDoesNotReplaceCurrentPage() async {
        var state = ChapterReaderFeature.State(reference: .init(bookId: "John", chapter: 3))
        state.content = .loaded(Self.verses("John", 3, count: 3))
        let s = store(state)
        await s.send(.cachedChapter(.init(bookId: "John", chapter: 4), .failure(.unexpected)))
        #expect(s.state.content == state.content)
        #expect(s.state.chapterErrors["John.4"] == .unexpected)
    }
    @Test func cannotStepOutsideCanon() async {
        let first = store(.init(reference: .init(bookId: "Gen", chapter: 1)))
        await first.send(.previousChapterTapped)
        #expect(!first.state.canGoToPreviousChapter)
        let last = store(.init(reference: .init(bookId: "Rev", chapter: 22)))
        await last.send(.nextChapterTapped)
        #expect(!last.state.canGoToNextChapter)
    }
}
