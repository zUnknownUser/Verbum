import Clients
import ComposableArchitecture
import Models
import Testing
@testable import Features

@MainActor
@Suite struct EntityDetailFeatureTests {
    nonisolated static let david = BibleEntity(id: "fixture.person.david", type: .person, name: "David", summary: "King.")
    nonisolated static let goliath = BibleEntity(id: "fixture.person.goliath", type: .person, name: "Goliath", summary: nil)
    nonisolated static let sam17 = BibleEntity(id: "passage.1Sam.17", type: .passage, name: "1 Samuel 17", summary: nil)
    nonisolated static let source = SourceReference(id: "fixture.source.web", citation: "WEB", url: nil)

    nonisolated static var page: EntityDetailFeature.Page {
        .init(
            detail: EntityDetail(entity: david, role: "King", keyPassages: [PassageReference(bookId: "1Sam", chapter: 17)], sources: [source]),
            neighborhood: GraphSnapshot(root: david, nodes: [goliath, sam17], edges: [
                BibleRelationship(id: "e1", sourceId: david.id, targetId: goliath.id, type: .relatedTo, confidence: 1, sourceReferenceIds: [source.id]),
                BibleRelationship(id: "e2", sourceId: david.id, targetId: sam17.id, type: .appearsIn, confidence: 1, sourceReferenceIds: [source.id]),
            ])
        )
    }

    @Test func taskLoadsDetailAndNeighbourhood() async {
        let store = TestStore(initialState: EntityDetailFeature.State(entityID: Self.david.id)) {
            EntityDetailFeature()
        } withDependencies: {
            $0.graphClient.detail = { _ in Self.page.detail }
            $0.graphClient.neighbors = { id, limit in
                #expect(id == Self.david.id && limit == 12)
                return Self.page.neighborhood
            }
            $0.timelineClient.eventsFor = { _ in [] }
        }
        await store.send(.task) { $0.content = .loading }
        await store.receive(\.pageResponse.success) { $0.content = .loaded(Self.page) }
        if case .loaded(let page) = store.state.content {
            #expect(page.related(.person) == [Self.goliath])
            #expect(page.passages == [PassageReference(bookId: "1Sam", chapter: 17)])
            #expect(page.sources == [Self.source])
        }
    }

    @Test func failureIsAStateNotAnErrorString() async {
        struct Boom: Error {}
        let store = TestStore(initialState: EntityDetailFeature.State(entityID: "x")) {
            EntityDetailFeature()
        } withDependencies: {
            $0.graphClient.detail = { _ in throw Boom() }
            $0.graphClient.neighbors = { _, _ in throw Boom() }
            $0.timelineClient.eventsFor = { _ in [] }
        }
        await store.send(.task) { $0.content = .loading }
        await store.receive(\.pageResponse.failure) { $0.content = .failed }
        await store.send(.retryTapped) { $0.content = .loading }
        await store.receive(\.pageResponse.failure) { $0.content = .failed }
    }

    @Test func passagesAndEntitiesBecomeDelegates() async {
        let store = TestStore(initialState: EntityDetailFeature.State(entityID: Self.david.id)) {
            EntityDetailFeature()
        }
        await store.send(.passageTapped(PassageReference(bookId: "1Sam", chapter: 17)))
        await store.receive(\.delegate.openPassage, PassageReference(bookId: "1Sam", chapter: 17))
        await store.send(.entityTapped(Self.goliath))
        await store.receive(\.delegate.openEntity, Self.goliath)
        // A passage node opens the reader, not another page.
        await store.send(.entityTapped(Self.sam17))
        await store.receive(\.delegate.openPassage, PassageReference(bookId: "1Sam", chapter: 17))
    }

    @Test func passagesFallBackToNeighbourhoodWhenDetailListsNone() {
        let page = EntityDetailFeature.Page(
            detail: EntityDetail(entity: Self.goliath),
            neighborhood: GraphSnapshot(root: Self.goliath, nodes: [Self.sam17], edges: [])
        )
        #expect(page.passages == [PassageReference(bookId: "1Sam", chapter: 17)])
    }
}

