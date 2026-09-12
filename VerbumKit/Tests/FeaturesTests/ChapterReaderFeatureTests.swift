import Clients
import ComposableArchitecture
import Models
import Testing
@testable import Features

@MainActor
@Suite struct ChapterReaderFeatureTests {
    // Fixture verses: placeholder text, not Scripture.
    nonisolated static func verses(_ bookId: BookID, _ chapter: Int, count: Int) -> [BiblePassage] {
        (1...count).map {
            BiblePassage(id: "t:\(bookId).\(chapter).\($0)", translationId: "t", bookId: bookId, chapter: chapter, verseStart: $0, verseEnd: $0, text: "v\($0)")
        }
    }

    @Test func taskLoadsChapter() async {
        let store = TestStore(initialState: ChapterReaderFeature.State(reference: PassageReference(bookId: "John", chapter: 3))) {
            ChapterReaderFeature()
        } withDependencies: {
            $0.bibleClient.chapter = { bookId, chapter in Self.verses(bookId, chapter, count: 3) }
        }

        await store.send(.task) { $0.content = .loading }
        await store.receive(\.chapterResponse.success) {
            $0.content = .loaded(Self.verses("John", 3, count: 3))
            $0.$lastRead.withLock { $0 = PassageReference(bookId: "John", chapter: 3) }
        }
    }

    @Test func loadFailureIsMappedToReaderError() async {
        let reference = PassageReference(bookId: "John", chapter: 4)
        let store = TestStore(initialState: ChapterReaderFeature.State(reference: reference)) {
            ChapterReaderFeature()
        } withDependencies: {
            $0.bibleClient.chapter = { _, _ in throw BibleClientError.contentUnavailable(reference) }
        }

        await store.send(.task) { $0.content = .loading }
        await store.receive(\.chapterResponse.failure) { $0.content = .failed(.chapterUnavailable(reference)) }
    }

    @Test func unknownErrorsNeverLeakRaw() async {
        struct Boom: Error {}
        let store = TestStore(initialState: ChapterReaderFeature.State(reference: PassageReference(bookId: "John", chapter: 3))) {
            ChapterReaderFeature()
        } withDependencies: {
            $0.bibleClient.chapter = { _, _ in throw Boom() }
        }

        await store.send(.task) { $0.content = .loading }
        await store.receive(\.chapterResponse.failure) { $0.content = .failed(.unexpected) }
        await store.send(.retryTapped) { $0.content = .loading }
        await store.receive(\.chapterResponse.failure) { $0.content = .failed(.unexpected) }
    }

    @Test func verseSelectionTogglesAndFormats() async {
        let store = TestStore(initialState: ChapterReaderFeature.State(reference: PassageReference(bookId: "John", chapter: 3))) {
            ChapterReaderFeature()
        }
        store.exhaustivity = .off(showSkippedAssertions: false)
        await store.send(.chapterResponse(.success(Self.verses("John", 3, count: 21)))) { $0.content = .loaded(Self.verses("John", 3, count: 21)) }
        store.exhaustivity = .on

        await store.send(.verseTapped(16)) { $0.selectedVerses = [16] }
        await store.send(.verseTapped(17)) { $0.selectedVerses = [16, 17] }
        await store.send(.verseTapped(18)) { $0.selectedVerses = [16, 17, 18] }
        await store.send(.verseTapped(21)) { $0.selectedVerses = [16, 17, 18, 21] }
        #expect(store.state.selectionCitation == "John 3:16-18, 21")
        #expect(store.state.selectedText == "v16 v17 v18 v21")

        await store.send(.verseTapped(17)) { $0.selectedVerses = [16, 18, 21] }
        #expect(store.state.selectionCitation == "John 3:16, 18, 21")

        await store.send(.clearSelectionTapped) { $0.selectedVerses = [] }
        #expect(store.state.selectionCitation == nil)
    }

    @Test func copyWritesTextAndCitationToPasteboard() async {
        let copied = LockIsolated<String?>(nil)
        let store = TestStore(initialState: ChapterReaderFeature.State(reference: PassageReference(bookId: "John", chapter: 3))) {
            ChapterReaderFeature()
        } withDependencies: {
            $0.pasteboard.copy = { text in copied.setValue(text) }
        }
        store.exhaustivity = .off(showSkippedAssertions: false)
        await store.send(.chapterResponse(.success(Self.verses("John", 3, count: 21))))
        await store.send(.verseTapped(16))
        await store.send(.verseTapped(17))
        store.exhaustivity = .on

        await store.send(.copySelectionTapped)
        #expect(copied.value == "v16 v17\n— John 3:16-17")
    }

    @Test func copyWithNothingSelectedDoesNothing() async {
        // pasteboard.copy is unimplemented in tests: calling it would fail the test.
        let store = TestStore(initialState: ChapterReaderFeature.State(reference: PassageReference(bookId: "John", chapter: 3))) {
            ChapterReaderFeature()
        }
        await store.send(.copySelectionTapped)
    }

    @Test func nextChapterReloadsAndClearsSelection() async {
        let store = TestStore(initialState: ChapterReaderFeature.State(reference: PassageReference(bookId: "John", chapter: 3))) {
            ChapterReaderFeature()
        } withDependencies: {
            $0.bibleClient.chapter = { bookId, chapter in Self.verses(bookId, chapter, count: 2) }
        }
        store.exhaustivity = .off(showSkippedAssertions: false)
        await store.send(.verseTapped(1))
        store.exhaustivity = .on

        await store.send(.nextChapterTapped) {
            $0.reference = PassageReference(bookId: "John", chapter: 4)
            $0.selectedVerses = []
            $0.content = .loading
        }
        await store.receive(\.chapterResponse.success) {
            $0.content = .loaded(Self.verses("John", 4, count: 2))
            $0.$lastRead.withLock { $0 = PassageReference(bookId: "John", chapter: 4) }
        }
    }

    @Test func chapterStepsRollAcrossBooks() async {
        let store = TestStore(initialState: ChapterReaderFeature.State(reference: PassageReference(bookId: "John", chapter: 21))) {
            ChapterReaderFeature()
        } withDependencies: {
            $0.bibleClient.chapter = { bookId, chapter in Self.verses(bookId, chapter, count: 1) }
        }

        await store.send(.nextChapterTapped) {
            $0.reference = PassageReference(bookId: "Acts", chapter: 1)
            $0.content = .loading
        }
        await store.receive(\.chapterResponse.success) {
            $0.content = .loaded(Self.verses("Acts", 1, count: 1))
            $0.$lastRead.withLock { $0 = PassageReference(bookId: "Acts", chapter: 1) }
        }

        await store.send(.previousChapterTapped) {
            $0.reference = PassageReference(bookId: "John", chapter: 21)
            $0.content = .loading
        }
        await store.receive(\.chapterResponse.success) {
            $0.content = .loaded(Self.verses("John", 21, count: 1))
            $0.$lastRead.withLock { $0 = PassageReference(bookId: "John", chapter: 21) }
        }
    }

    @Test func cannotStepBeforeGenesisOrAfterRevelation() async {
        let genesis = TestStore(initialState: ChapterReaderFeature.State(reference: PassageReference(bookId: "Gen", chapter: 1))) {
            ChapterReaderFeature()
        }
        #expect(!genesis.state.canGoToPreviousChapter)
        await genesis.send(.previousChapterTapped)

        let revelation = TestStore(initialState: ChapterReaderFeature.State(reference: PassageReference(bookId: "Rev", chapter: 22))) {
            ChapterReaderFeature()
        }
        #expect(!revelation.state.canGoToNextChapter)
        await revelation.send(.nextChapterTapped)
    }

    @Test func goToReferenceDropsVersesAndLoadsChapter() async {
        let store = TestStore(initialState: ChapterReaderFeature.State(reference: PassageReference(bookId: "John", chapter: 3))) {
            ChapterReaderFeature()
        } withDependencies: {
            $0.bibleClient.chapter = { bookId, chapter in Self.verses(bookId, chapter, count: 1) }
        }

        await store.send(.go(to: PassageReference(bookId: "1Sam", chapter: 17, verses: 45...47))) {
            $0.reference = PassageReference(bookId: "1Sam", chapter: 17)
            $0.content = .loading
        }
        await store.receive(\.chapterResponse.success) {
            $0.content = .loaded(Self.verses("1Sam", 17, count: 1))
            $0.$lastRead.withLock { $0 = PassageReference(bookId: "1Sam", chapter: 17) }
        }
    }

    @Test func inFlightLoadIsCancelledByANewerOne() async {
        let store = TestStore(initialState: ChapterReaderFeature.State(reference: PassageReference(bookId: "John", chapter: 3))) {
            ChapterReaderFeature()
        } withDependencies: {
            $0.bibleClient.chapter = { bookId, chapter in
                if chapter == 3 { try await Task.never() }
                return Self.verses(bookId, chapter, count: 1)
            }
        }

        await store.send(.task) { $0.content = .loading }
        await store.send(.nextChapterTapped) { $0.reference = PassageReference(bookId: "John", chapter: 4) }
        await store.receive(\.chapterResponse.success) {
            $0.content = .loaded(Self.verses("John", 4, count: 1))
            $0.$lastRead.withLock { $0 = PassageReference(bookId: "John", chapter: 4) }
        }
    }
}
