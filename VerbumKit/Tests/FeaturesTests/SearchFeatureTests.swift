import Clients
import ComposableArchitecture
import Foundation
import Models
import Testing
@testable import Features

@MainActor
@Suite struct SearchFeatureTests {
    nonisolated static func response(_ query: String, books: [BibleBook] = [], entities: [BibleEntity] = []) -> SearchResponse {
        SearchResponse(query: query, passages: [], books: books, entities: entities)
    }

    @Test func typingDebouncesThenSearches() async throws {
        let clock = TestClock()
        let store = TestStore(initialState: SearchFeature.State()) {
            SearchFeature()
        } withDependencies: {
            $0.continuousClock = clock
            $0.searchClient.search = { query in Self.response(query, entities: [BibleEntity(id: "fixture.person.david", type: .person, name: "David", summary: nil)]) }
        }

        await store.send(.binding(.set(\.query, "dav"))) {
            $0.query = "dav"
            $0.phase = .searching
        }
        await clock.advance(by: .milliseconds(249))
        await clock.advance(by: .milliseconds(1))
        await store.receive(\.searchResponse) {
            $0.phase = .idle
            $0.results = Self.response("dav", entities: [BibleEntity(id: "fixture.person.david", type: .person, name: "David", summary: nil)])
        }
    }

    @Test func newerTypingCancelsTheOlderSearch() async {
        let clock = TestClock()
        let searched = LockIsolated<[String]>([])
        let store = TestStore(initialState: SearchFeature.State()) {
            SearchFeature()
        } withDependencies: {
            $0.continuousClock = clock
            $0.searchClient.search = { query in
                searched.withValue { $0.append(query) }
                return Self.response(query)
            }
        }

        await store.send(.binding(.set(\.query, "d"))) { $0.query = "d"; $0.phase = .searching }
        await clock.advance(by: .milliseconds(100))
        await store.send(.binding(.set(\.query, "da"))) { $0.query = "da" }
        await clock.advance(by: .milliseconds(250))
        await store.receive(\.searchResponse) {
            $0.phase = .idle
            $0.results = Self.response("da")
        }
        #expect(searched.value == ["da"]) // "d" never reached the client
    }

    @Test func clearingTheQueryClearsResultsAndCancels() async {
        let clock = TestClock()
        let store = TestStore(initialState: SearchFeature.State()) {
            SearchFeature()
        } withDependencies: {
            $0.continuousClock = clock
            $0.searchClient.search = { Self.response($0) }
        }
        await store.send(.binding(.set(\.query, "dav"))) { $0.query = "dav"; $0.phase = .searching }
        await store.send(.binding(.set(\.query, ""))) {
            $0.query = ""
            $0.phase = .idle
            $0.results = nil
        }
        await clock.advance(by: .seconds(1))
        // No response arrives: the search was cancelled.
    }

    @Test func staleResponsesAreIgnored() async {
        var state = SearchFeature.State()
        state.query = "david"
        state.phase = .searching
        let store = TestStore(initialState: state) {
            SearchFeature()
        }
        // An answer for "dav" arriving after the user typed "david" must not be shown.
        await store.send(.searchResponse(Self.response("dav")))
        #expect(store.state.results == nil)
        #expect(store.state.phase == .searching)
    }

    @Test func clientFailureBecomesDeviceOnlyResults() async {
        struct Boom: Error {}
        let clock = TestClock()
        let store = TestStore(initialState: SearchFeature.State()) {
            SearchFeature()
        } withDependencies: {
            $0.continuousClock = clock
            $0.locale = Locale(identifier: "en_US")
            $0.searchClient.search = { _ in throw Boom() }
        }
        await store.send(.binding(.set(\.query, "zzz"))) { $0.query = "zzz"; $0.phase = .searching }
        await clock.advance(by: .milliseconds(250))
        await store.receive(\.searchUnreachable) {
            $0.phase = .idle
            $0.isOffline = true
            $0.results = .empty("zzz")
        }
        #expect(store.state.showsNoResults)
    }

    @Test func returnKeyOpensAParsedReferenceImmediately() async {
        // Still searching (no results yet): the reference opens anyway.
        var state = SearchFeature.State()
        state.query = "Jn 3:16"
        state.phase = .searching
        let store = TestStore(initialState: state) {
            SearchFeature()
        } withDependencies: {
            $0.locale = Locale(identifier: "en_US")
        }
        await store.send(.submitted)
        await store.receive(\.delegate.openPassage, PassageReference(bookId: "John", chapter: 3, verses: 16...16))
    }

    @Test func returnKeyOpensTheFirstBookMatch() async throws {
        let samuel = try #require(BibleBook.book(id: "1Sam"))
        var state = SearchFeature.State()
        state.query = "sam"
        state.results = Self.response("sam", books: [samuel])
        let store = TestStore(initialState: state) {
            SearchFeature()
        } withDependencies: {
            $0.locale = Locale(identifier: "en_US")
        }
        await store.send(.submitted)
        await store.receive(\.delegate.openPassage, PassageReference(bookId: "1Sam", chapter: 1))
    }

    @Test func returnKeyWithNothingToOpenDoesNothing() async {
        var state = SearchFeature.State()
        state.query = "why did Job suffer"
        let store = TestStore(initialState: state) {
            SearchFeature()
        } withDependencies: {
            $0.locale = Locale(identifier: "en_US")
        }
        await store.send(.submitted)
    }

    @Test func tapsBecomeDelegates() async throws {
        let store = TestStore(initialState: SearchFeature.State()) {
            SearchFeature()
        }
        let romans = try #require(BibleBook.book(id: "Rom"))
        let david = BibleEntity(id: "fixture.person.david", type: .person, name: "David", summary: nil)

        await store.send(.passageTapped(PassageReference(bookId: "Rom", chapter: 8, verses: 28...28)))
        await store.receive(\.delegate.openPassage, PassageReference(bookId: "Rom", chapter: 8, verses: 28...28))
        await store.send(.bookTapped(romans))
        await store.receive(\.delegate.openPassage, PassageReference(bookId: "Rom", chapter: 1))
        await store.send(.entityTapped(david))
        await store.receive(\.delegate.openEntity, david)
    }
}

@MainActor
@Suite struct SearchFeatureOfflineTests {
    /// §21.3, §52: when the content service can't be reached the field still
    /// answers with what the device knows, and says the service is out.
    @Test func unreachableServiceDegradesToDeviceResultsAndSaysSo() async {
        let clock = TestClock()
        let store = TestStore(initialState: SearchFeature.State()) {
            SearchFeature()
        } withDependencies: {
            $0.continuousClock = clock
            $0.locale = Locale(identifier: "en_US")
            $0.searchClient.search = { _ in throw VerbumAPIError.networkUnavailable }
        }

        await store.send(.binding(.set(\.query, "Jn 3:16"))) { $0.query = "Jn 3:16"; $0.phase = .searching }
        await clock.advance(by: .milliseconds(250))
        await store.receive(\.searchUnreachable) {
            $0.phase = .idle
            $0.isOffline = true
            $0.results = SearchResponse(query: "Jn 3:16", passages: [PassageReference(bookId: "John", chapter: 3, verses: 16...16)], books: [], entities: [])
        }

        // A later answer from the service clears the notice.
        store.dependencies.searchClient.search = { query in SearchResponse(query: query, passages: [], books: [], entities: []) }
        await store.send(.binding(.set(\.query, "hope"))) { $0.query = "hope"; $0.phase = .searching }
        await clock.advance(by: .milliseconds(250))
        await store.receive(\.searchResponse) {
            $0.phase = .idle
            $0.isOffline = false
            $0.results = SearchResponse(query: "hope", passages: [], books: [], entities: [])
        }
        #expect(store.state.showsNoResults)
    }

    @Test func clearingTheQueryClearsTheOfflineNotice() async {
        var initial = SearchFeature.State()
        initial.query = "x"
        initial.isOffline = true
        let store = TestStore(initialState: initial) {
            SearchFeature()
        }
        await store.send(.binding(.set(\.query, ""))) { $0.query = ""; $0.isOffline = false }
    }
}
