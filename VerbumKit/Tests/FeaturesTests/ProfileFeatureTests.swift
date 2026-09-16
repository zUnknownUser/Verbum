import Clients
import ComposableArchitecture
import Foundation
import Models
import Testing
@testable import Features

@MainActor struct ProfileFeatureTests {
    @Test func collectionsLoadIndependentlyFromAnnotationKind() async {
        let reference = PassageReference(bookId: "John", chapter: 1, verses: 14...14)
        let annotation = ReaderAnnotation(reference: reference, highlight: .gold, note: "Minha nota", bookmarked: true)
        let store = TestStore(initialState: ProfileFeature.State()) { ProfileFeature() } withDependencies: {
            $0.readerAnnotations = .init(load: { [annotation] }, save: { _ in })
        }
        await store.send(.task) { $0.loading = true }
        await store.receive(.usageLoaded(nil))
        await store.receive(.loaded([annotation])) { $0.loading = false; $0.annotations = [annotation] }
        #expect(store.state.bookmarks == [annotation])
        #expect(store.state.notes == [annotation])
        #expect(store.state.highlights == [annotation])
    }
    @Test func loadFailureIsNotAnEmptyCollection() async {
        let store = TestStore(initialState: ProfileFeature.State()) { ProfileFeature() } withDependencies: {
            $0.readerAnnotations = .init(load: { throw CocoaError(.fileReadCorruptFile) }, save: { _ in })
        }
        await store.send(.task) { $0.loading = true }
        await store.receive(.usageLoaded(nil))
        await store.receive(.loadFailed) { $0.loading = false; $0.loadFailed = true }
    }
    @Test func readingActivityExcludesUnloadedAndPrefetchedChapters() async {
        var state = ChapterReaderFeature.State(reference: .init(bookId: "John", chapter: 1))
        state.chapters["John.2"] = ChapterReaderFeatureTests.verses("John", 2, count: 3)
        let store = TestStore(initialState: state) { ChapterReaderFeature() }
        await store.send(.recordReading)
        #expect(store.state.readingActivity.visits.isEmpty)
    }
    @Test func bookmarkSavesWithoutRequiringNoteOrHighlight() async {
        let passage = ChapterReaderFeatureTests.verses("John", 1, count: 1)[0]
        let annotation = ReaderAnnotation(reference: .init(bookId: "John", chapter: 1, verses: 1...1), bookmarked: true)
        let store = TestStore(initialState: VerseStudyFeature.State(passage: passage, annotation: nil, context: nil)) { VerseStudyFeature() } withDependencies: {
            $0.readerAnnotations = .init(load: { [] }, save: { #expect($0 == annotation) })
        }
        await store.send(.bookmarkToggled) { $0.annotation.bookmarked = true }
        await store.receive(.save) { $0.saving = true }
        await store.receive(.saved(annotation)) { $0.saving = false; $0.saved = true }
        await store.receive(.delegate(.annotationSaved(annotation)))
    }
}
