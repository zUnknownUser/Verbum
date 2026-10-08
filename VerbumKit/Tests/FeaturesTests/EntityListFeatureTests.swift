import ComposableArchitecture
import Models
import Testing
@testable import Features

@MainActor
@Suite struct EntityListFeatureTests {
    private let abel = BibleEntity(id: "a", type: .person, name: "Abel", summary: "Son of Adam")
    private let anna = BibleEntity(id: "b", type: .person, name: "Anna", summary: nil)

    @Test func themeCategoryResetsFiltersAndUsesBoundedServerRequest() async {
        var state = EntityListFeature.State(type: .theme)
        state.query = "old"; state.letter = "A"; state.hasLoaded = true; state.nextOffset = 30
        let page = EntityCatalogPage(entities: [], letters: [])
        let store = TestStore(initialState: state) { EntityListFeature() } withDependencies: {
            $0.graphClient.entityPage = { request in
                #expect(request.type == .theme && request.category == "emotions")
                #expect(request.query.isEmpty && request.letter.isEmpty && request.offset == 0 && request.limit == 30)
                return page
            }
        }
        await store.send(.categoryChanged("emotions")) {
            $0.category = "emotions"; $0.browsingThemes = true; $0.query = ""; $0.letter = ""
            $0.hasLoaded = false; $0.nextOffset = nil; $0.isLoading = true; $0.generation = 1
        }
        await store.receive(.pageResponse(1, 0, page)) { $0.isLoading = false; $0.hasLoaded = true }
        store.dependencies.graphClient.entityPage = { request in
            #expect(request.category.isEmpty)
            return page
        }
        await store.send(.discoverThemes) {
            $0.browsingThemes = false; $0.category = ""; $0.hasLoaded = false; $0.isLoading = true; $0.generation = 2
        }
        await store.receive(.pageResponse(2, 0, page)) { $0.isLoading = false; $0.hasLoaded = true }
    }

    @Test func eventsUseBoundedSearchInsteadOfTheLegacyList() async {
        var state = EntityListFeature.State(type: .event)
        state.query = "exodo"
        let page = EntityCatalogPage(entities: [], letters: [])
        let store = TestStore(initialState: state) { EntityListFeature() } withDependencies: {
            $0.graphClient.entityPage = { request in
                #expect(request.type == .event && request.query == "exodo" && request.limit == 30)
                return page
            }
        }
        await store.send(.task) { $0.isLoading = true; $0.generation = 1 }
        await store.receive(.pageResponse(1, 0, page)) { $0.isLoading = false; $0.hasLoaded = true }
    }

    @Test func placesUseServerPaginationAndOpenTheirOwnDetail() async {
        let jerusalem = BibleEntity(id: "place.jerusalem", type: .place, name: "Jerusalém", summary: "Cidade")
        var state = EntityListFeature.State(type: .place)
        state.query = "jerusalem"; state.letter = "J"
        let page = EntityCatalogPage(entities: [jerusalem], letters: ["J"], nextOffset: 30)
        let store = TestStore(initialState: state) { EntityListFeature() } withDependencies: {
            $0.graphClient.entityPage = { request in
                #expect(request == EntityCatalogRequest(type: .place, query: "jerusalem", letter: "J"))
                return page
            }
        }
        await store.send(.task) { $0.isLoading = true; $0.generation = 1 }
        await store.receive(.pageResponse(1, 0, page)) {
            $0.isLoading = false; $0.hasLoaded = true; $0.entities = [jerusalem]; $0.letters = ["J"]; $0.nextOffset = 30
        }
        await store.send(.entityTapped(jerusalem))
        await store.receive(.delegate(.openEntity(jerusalem)))
        await store.send(.cancel) { $0.generation = 2 }
        await store.send(.task)
        #expect(store.state.query == "jerusalem" && store.state.letter == "J")
    }

    @Test func pagesAppendWithoutDuplicatingAndReturnDoesNotReload() async {
        let first = EntityCatalogPage(entities: [abel], letters: ["A"], nextOffset: 30)
        let second = EntityCatalogPage(entities: [abel, anna], letters: ["A"])
        let store = TestStore(initialState: EntityListFeature.State(type: .person)) { EntityListFeature() } withDependencies: {
            $0.graphClient.entityPage = { request in
                #expect(request.limit == 30)
                return request.offset == 0 ? first : second
            }
        }
        await store.send(.task) { $0.isLoading = true; $0.generation = 1 }
        await store.receive(.pageResponse(1, 0, first)) {
            $0.isLoading = false; $0.hasLoaded = true; $0.entities = [abel]; $0.letters = ["A"]; $0.nextOffset = 30
        }
        await store.send(.loadMore) { $0.isLoading = true; $0.generation = 2; $0.loadingOffset = 30 }
        await store.receive(.pageResponse(2, 30, second)) {
            $0.isLoading = false; $0.entities = [abel, anna]; $0.nextOffset = nil
        }
        await store.send(.entityTapped(anna))
        await store.receive(.delegate(.openEntity(anna)))
        await store.send(.cancel) { $0.generation = 3 }
        await store.send(.task)
        await store.send(.loadMore)
    }

    @Test func searchDebouncesAndLetterCancelsPendingSearch() async {
        let clock = TestClock()
        let page = EntityCatalogPage(entities: [abel], letters: ["A"])
        let store = TestStore(initialState: EntityListFeature.State(type: .person)) { EntityListFeature() } withDependencies: {
            $0.continuousClock = clock
            $0.graphClient.entityPage = { request in
                #expect(request.query == "Ab")
                #expect(request.letter == "A")
                #expect(request.offset == 0)
                return page
            }
        }
        await store.send(.queryChanged("A")) { $0.query = "A"; $0.isLoading = true; $0.generation = 1 }
        await store.send(.queryChanged("Ab")) { $0.query = "Ab"; $0.generation = 2 }
        await store.send(.letterChanged("A")) { $0.letter = "A"; $0.generation = 3 }
        await store.receive(.pageResponse(3, 0, page)) {
            $0.isLoading = false; $0.hasLoaded = true; $0.entities = [abel]; $0.letters = ["A"]
        }
        await clock.advance(by: .seconds(1))
        // A late result or error from a cancelled filter must not replace the current page.
        await store.send(.pageResponse(1, 0, .init(entities: [], letters: [])))
        await store.send(.failed(2))
    }

    @Test func failedNextPageKeepsRowsAndRetriesSameOffset() async {
        enum Failure: Error { case offline }
        var state = EntityListFeature.State(type: .person)
        state.entities = [abel]; state.hasLoaded = true; state.nextOffset = 30; state.letters = ["A"]
        let store = TestStore(initialState: state) { EntityListFeature() } withDependencies: {
            $0.graphClient.entityPage = { _ in throw Failure.offline }
        }
        await store.send(.loadMore) { $0.isLoading = true; $0.generation = 1; $0.loadingOffset = 30 }
        await store.receive(.failed(1)) { $0.isLoading = false; $0.failed = true }
        let page = EntityCatalogPage(entities: [anna], letters: ["A"])
        store.dependencies.graphClient.entityPage = { request in
            #expect(request.offset == 30)
            return page
        }
        await store.send(.retry) { $0.isLoading = true; $0.failed = false; $0.generation = 2 }
        await store.receive(.pageResponse(2, 30, page)) {
            $0.entities = [abel, anna]; $0.isLoading = false; $0.nextOffset = nil
        }
    }

    @Test func cancelledInitialSearchCanRestartWhenReturning() async {
        let clock = TestClock()
        let page = EntityCatalogPage(entities: [], letters: [])
        let store = TestStore(initialState: EntityListFeature.State(type: .person)) { EntityListFeature() } withDependencies: {
            $0.continuousClock = clock
            $0.graphClient.entityPage = { _ in page }
        }
        await store.send(.queryChanged("missing")) { $0.query = "missing"; $0.isLoading = true; $0.generation = 1 }
        await store.send(.cancel) { $0.isLoading = false; $0.generation = 2 }
        await store.send(.task) { $0.isLoading = true; $0.generation = 3 }
        await store.receive(.pageResponse(3, 0, page)) { $0.isLoading = false; $0.hasLoaded = true }
        await clock.advance(by: .seconds(1))
    }
}
