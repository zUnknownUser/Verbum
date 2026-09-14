import ComposableArchitecture
import Models
import Testing
@testable import Features

@MainActor
@Suite struct BookPickerFeatureTests {
    @Test func liveReferenceSearchAndClose() async {
        let store = TestStore(initialState: BookPickerFeature.State(current: PassageReference(bookId: "Gen", chapter: 1))) { BookPickerFeature() }
        let reference = PassageReference(bookId: "John", chapter: 3, verses: 16...18)
        await store.send(.toggleSearch) { $0.searchVisible = true }
        await store.send(.queryChanged("John 3:16-18")) { $0.query = "John 3:16-18"; $0.matchingReference = reference }
        await store.send(.searchSubmitted)
        await store.receive(\.delegate.chapterSelected, reference)
        await store.send(.toggleSearch) { $0.searchVisible = false; $0.query = ""; $0.matchingReference = nil }
        await store.send(.queryChanged("John 999")) { $0.query = "John 999" }
        await store.send(.searchSubmitted)
    }
    @Test func canonIsSplitByTestament() {
        let state = BookPickerFeature.State(current: PassageReference(bookId: "John", chapter: 3))
        #expect(state.oldTestament.count == 39)
        #expect(state.newTestament.count == 27)
    }

    @Test func bookThenChapterEmitsDelegate() async throws {
        let store = TestStore(initialState: BookPickerFeature.State(current: PassageReference(bookId: "John", chapter: 3))) {
            BookPickerFeature()
        }
        let samuel = try #require(BibleBook.book(id: "1Sam"))

        await store.send(.bookTapped(samuel)) { $0.selectedBook = samuel }
        await store.send(.chapterTapped(17))
        await store.receive(\.delegate.chapterSelected, PassageReference(bookId: "1Sam", chapter: 17))
    }

    @Test func backReturnsToBooks() async throws {
        let store = TestStore(initialState: BookPickerFeature.State(current: PassageReference(bookId: "John", chapter: 3))) {
            BookPickerFeature()
        }
        let samuel = try #require(BibleBook.book(id: "1Sam"))
        await store.send(.bookTapped(samuel)) { $0.selectedBook = samuel }
        await store.send(.backToBooksTapped) { $0.selectedBook = nil }
    }

    @Test func chapterOutOfBookRangeIsIgnored() async throws {
        let store = TestStore(initialState: BookPickerFeature.State(current: PassageReference(bookId: "John", chapter: 3))) {
            BookPickerFeature()
        }
        let jude = try #require(BibleBook.book(id: "Jude"))
        await store.send(.bookTapped(jude)) { $0.selectedBook = jude }
        await store.send(.chapterTapped(2))
        await store.send(.chapterTapped(0))
    }

    @Test func chapterWithoutBookIsIgnored() async {
        let store = TestStore(initialState: BookPickerFeature.State(current: PassageReference(bookId: "John", chapter: 3))) {
            BookPickerFeature()
        }
        await store.send(.chapterTapped(1))
    }
}
