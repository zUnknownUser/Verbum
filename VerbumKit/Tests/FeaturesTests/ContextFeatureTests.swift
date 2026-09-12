import Clients
import ComposableArchitecture
import Models
import Testing
@testable import Features

@MainActor
@Suite struct ContextFeatureTests {
    @Test func missingCoverageIsNotAnError() async {
        let reference = PassageReference(bookId: "Neh", chapter: 9)
        let store = TestStore(initialState: ContextFeature.State(reference: reference)) {
            ContextFeature()
        } withDependencies: {
            $0.contextClient.chapter = { _ in nil }
        }
        await store.send(.task) { $0.content = .loading }
        await store.receive(\.response.success) { $0.content = .unavailable }
        await store.send(.task)
    }

    @Test func loadedContextSurvivesReturnNavigation() async {
        let reference = PassageReference(bookId: "1Sam", chapter: 17)
        let context = PassageContext(reference: reference, entities: [], relatedPassages: [], sources: [], isFixture: true)
        let store = TestStore(initialState: ContextFeature.State(reference: reference)) {
            ContextFeature()
        } withDependencies: {
            $0.contextClient.chapter = { _ in context }
        }
        await store.send(.task) { $0.content = .loading }
        await store.receive(\.response.success) { $0.content = .loaded(context) }
        await store.send(.task)
        await store.send(.passageTapped(reference))
        await store.receive(\.delegate.openPassage, reference)
    }

    @Test func failureAllowsRetry() async {
        struct Unavailable: Error {}
        let store = TestStore(initialState: ContextFeature.State(reference: .init(bookId: "John", chapter: 3))) {
            ContextFeature()
        } withDependencies: {
            $0.contextClient.chapter = { _ in throw Unavailable() }
        }
        await store.send(.task) { $0.content = .loading }
        await store.receive(\.response.failure) { $0.content = .failed }
        store.dependencies.contextClient.chapter = { _ in nil }
        await store.send(.retryTapped) { $0.content = .loading }
        await store.receive(\.response.success) { $0.content = .unavailable }
    }
}
