import ComposableArchitecture
import Models
import Testing
@testable import Features

@MainActor
@Suite struct ScriptureFeatureTests {
    static let verse = BiblePassage(id: "t", translationId: "t", bookId: "1Sam", chapter: 17, verseStart: 1, verseEnd: 1, text: "v1")

    @Test func startsOnTheReaderWithTheShelfInStep() {
        let state = ScriptureFeature.State(reference: PassageReference(bookId: "John", chapter: 3, verses: 16...16))
        #expect(!state.isShelfPresented)
        #expect(state.reader.reference == PassageReference(bookId: "John", chapter: 3))
        #expect(state.books.current == PassageReference(bookId: "John", chapter: 3))
    }

    @Test func titleOpensTheShelfAndAChapterClosesItIntoTheReader() async throws {
        // The load is held on a test clock so each step can be asserted before the
        // chapter arrives (the reader writes the shared `lastRead` when it does).
        let clock = TestClock()
        var initial = ScriptureFeature.State(reference: PassageReference(bookId: "John", chapter: 3))
        initial.reader.chapters["1Sam.16"] = []
        initial.reader.chapters["1Sam.18"] = []
        let store = TestStore(initialState: initial) {
            ScriptureFeature()
        } withDependencies: {
            $0.bibleClient.chapter = { bookId, chapter in
                try await clock.sleep(for: .seconds(1))
                return [BiblePassage(id: "t", translationId: "t", bookId: bookId, chapter: chapter, verseStart: 1, verseEnd: 1, text: "v1")]
            }
            $0.contextClient.chapter = { _ in
                try await clock.sleep(for: .seconds(1))
                return nil
            }
        }
        let samuel = try #require(BibleBook.book(id: "1Sam"))

        await store.send(.titleTapped) { $0.isShelfPresented = true }
        await store.send(.books(.bookTapped(samuel))) { $0.books.selectedBook = samuel }
        await store.send(.books(.chapterTapped(17)))
        await store.receive(\.books.delegate.chapterSelected) { $0.isShelfPresented = false }
        await store.receive(\.reader.go) {
            $0.reader.reference = PassageReference(bookId: "1Sam", chapter: 17)
            $0.reader.content = .loading
            $0.reader.flow = [PassageReference(bookId: "1Sam", chapter: 17)]
            $0.reader.navigationRevision = 1
            $0.reader.restoreOffset = 0
            $0.books.current = PassageReference(bookId: "1Sam", chapter: 17)
        }
        await store.receive(\.reader.ensureChapter) {
            $0.reader.loadingChapters = ["1Sam.17"]
        }
        await clock.advance(by: .seconds(1))
        await store.receive(\.reader.cachedChapter) {
            $0.reader.loadingChapters = []
            $0.reader.chapters["1Sam.17"] = [Self.verse]
            $0.reader.$lastRead.withLock { $0 = PassageReference(bookId: "1Sam", chapter: 17) }
        }
        await store.receive(\.reader.chapterResponse.success) {
            $0.reader.content = .loaded([Self.verse])
            $0.reader.$lastRead.withLock { $0 = PassageReference(bookId: "1Sam", chapter: 17) }
        }
        await store.receive(\.reader.ensureChapter)
        await store.receive(\.reader.ensureChapter)
        await clock.advance(by: .seconds(1))
        await store.receive(\.reader.contextResponse)
        #expect(store.state.books.selectedBook == samuel)
        await store.finish()
    }

    @Test func swipingTheShelfAwayIsRecorded() async {
        let store = TestStore(initialState: ScriptureFeature.State()) { ScriptureFeature() }
        await store.send(.titleTapped) { $0.isShelfPresented = true }
        await store.send(.shelfDismissed) { $0.isShelfPresented = false }
    }

    @Test func steppingChaptersInReaderMovesTheShelfMarker() async {
        let clock = TestClock()
        var initial = ScriptureFeature.State(reference: PassageReference(bookId: "John", chapter: 3))
        initial.reader.chapters["John.3"] = []
        initial.reader.chapters["John.5"] = []
        let store = TestStore(initialState: initial) {
            ScriptureFeature()
        } withDependencies: {
            $0.bibleClient.chapter = { _, _ in
                try await clock.sleep(for: .seconds(1))
                return []
            }
            $0.contextClient.chapter = { _ in
                try await clock.sleep(for: .seconds(1))
                return nil
            }
        }
        await store.send(.reader(.nextChapterTapped)) {
            $0.reader.reference = PassageReference(bookId: "John", chapter: 4)
            $0.reader.content = .loading
            $0.reader.flow = [PassageReference(bookId: "John", chapter: 4)]
            $0.reader.navigationRevision = 1
            $0.reader.restoreOffset = 0
            $0.books.current = PassageReference(bookId: "John", chapter: 4)
        }
        await store.receive(\.reader.ensureChapter) {
            $0.reader.loadingChapters = ["John.4"]
        }
        await clock.advance(by: .seconds(1))
        await store.receive(\.reader.cachedChapter) {
            $0.reader.loadingChapters = []
            $0.reader.chapters["John.4"] = []
            $0.reader.$lastRead.withLock { $0 = PassageReference(bookId: "John", chapter: 4) }
        }
        await store.receive(\.reader.chapterResponse.success) {
            $0.reader.content = .loaded([])
            $0.reader.$lastRead.withLock { $0 = PassageReference(bookId: "John", chapter: 4) }
        }
        await store.receive(\.reader.ensureChapter)
        await store.receive(\.reader.ensureChapter)
        await clock.advance(by: .seconds(1))
        await store.receive(\.reader.contextResponse)
        await store.finish()
    }

    @Test func textScaleChosenInSettingsIsSeenByReader() async {
        let store = TestStore(initialState: ScriptureFeature.State()) { ScriptureFeature() }
        #expect(store.state.reader.textScale == .standard)
        await store.send(.settingsButtonTapped) { $0.settings = ReaderSettingsFeature.State() }
        await store.send(.settings(.presented(.textScaleChanged(.large)))) {
            $0.settings?.$textScale.withLock { $0 = .large }
        }
        #expect(store.state.reader.textScale == .large)
        await store.send(.settings(.dismiss)) { $0.settings = nil }
    }
}
