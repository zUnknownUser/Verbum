import Clients
import ComposableArchitecture
import Foundation
import Models
import Testing
@testable import Features

@MainActor
@Suite struct ReadingHistoryFeatureTests {
    @Test func paginationSearchAndResetPreserveHistory() async {
        var state = ReadingHistoryFeature.State()
        var activity = ReadingActivity()
        for chapter in 1...45 {
            activity.record(.init(bookId: "Gen", chapter: chapter), at: Date(timeIntervalSince1970: Double(chapter)), calendar: .current)
        }
        activity.record(.init(bookId: "John", chapter: 3), at: Date(timeIntervalSince1970: 100), calendar: .current)
        state.$activity.withLock { $0 = activity }
        let store = TestStore(initialState: state) { ReadingHistoryFeature() }
        #expect(store.state.visibleVisits.count == 20)
        await store.send(.showMore) { $0.visibleCount = 40 }
        #expect(store.state.visibleVisits.count == 40)
        await store.send(.showMore) { $0.visibleCount = 60 }
        #expect(store.state.visibleVisits.count == 46)
        #expect(!store.state.hasMore)
        await store.send(.queryChanged("  joao 3 ")) { $0.query = "  joao 3 "; $0.visibleCount = 20 }
        #expect(store.state.visibleVisits.map(\.reference) == [.init(bookId: "John", chapter: 3)])
        await store.send(.queryChanged("Gênesis 3")) { $0.query = "Gênesis 3" }
        #expect(store.state.visibleVisits.map(\.reference) == [.init(bookId: "Gen", chapter: 3)])
        await store.send(.queryChanged("xyz")) { $0.query = "xyz" }
        #expect(store.state.visibleVisits.isEmpty)
        await store.send(.queryChanged("")) { $0.query = "" }
        #expect(store.state.visibleVisits.count == 20)
        #expect(store.state.activity.visits.count == 46)
    }

    @Test func historyOpensReaderOnTopOfJourneyHistory() async {
        var state = AppFeature.State()
        state.tab = .journey
        state.contentTab = .journey
        let store = TestStore(initialState: state) { AppFeature() }
        await store.send(.collection(.historyTapped))
        await store.receive(\.collection.delegate.history) {
            $0.journeyPath[id: 0] = .history(ReadingHistoryFeature.State())
        }
        let reference = PassageReference(bookId: "John", chapter: 3)
        await store.send(.journeyPath(.element(id: 0, action: .history(.open(reference)))))
        await store.receive(\.journeyPath[id: 0].history.delegate.open) {
            $0.journeyPath[id: 1] = .reader(ScriptureFeature.State(reference: reference))
        }
        #expect(store.state.journeyPath.count == 2)
        #expect(store.state.libraryPath.isEmpty)
    }
}
