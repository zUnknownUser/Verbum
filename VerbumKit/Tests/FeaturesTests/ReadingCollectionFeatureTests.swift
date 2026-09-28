import Clients
import ComposableArchitecture
import Models
import Testing
@testable import Features

@MainActor
@Suite struct ReadingCollectionFeatureTests {
    @Test func reloadKeepsFilterAndShowsExistingAnnotations() async {
        let item = ReaderAnnotation(reference: .init(bookId: "John", chapter: 3, verses: 16...16), note: "Amor", bookmarked: true)
        var state = ReadingCollectionFeature.State()
        state.query = "amor"
        state.filter = .notes
        let store = TestStore(initialState: state) { ReadingCollectionFeature() } withDependencies: {
            $0.readerAnnotations.load = { [item] }
        }
        await store.send(.task) { $0.loading = true }
        await store.receive(\.loaded) { $0.loading = false; $0.annotations = [item] }
        #expect(store.state.entries == [item])
        #expect(store.state.query == "amor")
        #expect(store.state.filter == .notes)
    }

    @Test func loadFailurePreservesExistingItems() async {
        enum Failure: Error { case unreadable }
        let item = ReaderAnnotation(reference: .init(bookId: "Job", chapter: 38, verses: 4...4), bookmarked: true)
        var state = ReadingCollectionFeature.State()
        state.annotations = [item]
        let store = TestStore(initialState: state) { ReadingCollectionFeature() } withDependencies: {
            $0.readerAnnotations.load = { throw Failure.unreadable }
        }
        await store.send(.task) { $0.loading = true }
        await store.receive(\.failed) { $0.loading = false; $0.failed = true }
        #expect(store.state.annotations == [item])
    }

    @Test func collectionOpensExactVerseInItsOwnTab() async {
        let reference = PassageReference(bookId: "John", chapter: 3, verses: 16...16)
        let store = TestStore(initialState: AppFeature.State()) { AppFeature() }
        await store.send(.tabChanged(.library)) { $0.tab = .library; $0.contentTab = .library }
        await store.send(.collection(.open(reference)))
        await store.receive(\.collection.delegate.open) {
            $0.libraryPath[id: 0] = .reader(ScriptureFeature.State(reference: reference))
        }
        #expect(store.state.homePath.isEmpty)
        await store.send(.tabChanged(.journey)) { $0.tab = .journey; $0.contentTab = .journey }
        await store.send(.collection(.open(reference)))
        await store.receive(\.collection.delegate.open) {
            $0.journeyPath[id: 1] = .reader(ScriptureFeature.State(reference: reference))
        }
    }
}
