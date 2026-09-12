import Clients
import ComposableArchitecture
import Models
import Testing
@testable import Features

private let david = TimelineEvent(id: "t.david", title: "Reign of David", startYear: -1010, endYear: -970, datePrecision: .approximate, summary: "s", entityIds: ["fixture.person.david"])
private let exodus = TimelineEvent(id: "t.exodus", title: "The Exodus", startYear: -1446, endYear: -1250, datePrecision: .debated, summary: "s", entityIds: ["fixture.person.moses"])
private let crucifixion = TimelineEvent(id: "t.cross", title: "Crucifixion", startYear: 30, endYear: 33, datePrecision: .debated, summary: "s", entityIds: ["fixture.person.jesus"])
private let temple = TimelineEvent(id: "t.temple", title: "Second Temple", startYear: -516, endYear: 70, datePrecision: .approximate, summary: nil, entityIds: [])
private let samaria = TimelineEvent(id: "t.samaria", title: "Fall of Samaria", startYear: -722, endYear: nil, datePrecision: .approximate, summary: nil, entityIds: [])
private let unknown = TimelineEvent(id: "t.unknown", title: "Job", startYear: nil, endYear: nil, datePrecision: .unknown, summary: nil, entityIds: [])

@MainActor
@Suite struct TimelineFeatureTests {
    @Test func loadsResolvesNamesAndOpensRowsInPlace() async {
        let store = TestStore(initialState: TimelineFeature.State()) {
            TimelineFeature()
        } withDependencies: {
            $0.timelineClient.events = { [exodus, david] }
            $0.graphClient.entity = { id in BibleEntity(id: id, type: .person, name: id.split(separator: ".").last!.capitalized, summary: nil) }
        }
        await store.send(.task) { $0.content = .loading }
        await store.receive(\.eventsResponse.success) { $0.content = .loaded([exodus, david]) }
        await store.receive(\.namesResponse) { $0.entityNames = ["fixture.person.david": "David", "fixture.person.moses": "Moses"] }
        await store.send(.eventTapped(david.id)) { $0.selectedID = david.id }
        await store.send(.eventTapped(exodus.id)) { $0.selectedID = exodus.id }
        await store.send(.eventTapped(exodus.id)) { $0.selectedID = nil }
        await store.send(.entityTapped("fixture.person.david"))
        await store.receive(\.delegate.openEntity, "fixture.person.david")
        await store.send(.task)
    }

    @Test func openedFromAnEntityItsFirstEventStartsOpen() async {
        let store = TestStore(initialState: TimelineFeature.State(highlight: "fixture.person.david")) {
            TimelineFeature()
        } withDependencies: {
            $0.timelineClient.events = { [exodus, david] }
            $0.graphClient.entity = { _ in throw GraphClientError.unknownEntity("x") }
        }
        await store.send(.task) { $0.content = .loading }
        await store.receive(\.eventsResponse.success) {
            $0.content = .loaded([exodus, david])
            $0.selectedID = david.id
        }
        await store.receive(\.namesResponse)
        #expect(store.state.highlightedEventID == david.id)
    }

    @Test func failureCanRetry() async {
        let store = TestStore(initialState: TimelineFeature.State()) {
            TimelineFeature()
        } withDependencies: {
            $0.timelineClient.events = { throw TimelineFeature.Failure() }
        }
        await store.send(.task) { $0.content = .loading }
        await store.receive(\.eventsResponse.failure) { $0.content = .failed }
        await store.send(.retryTapped) { $0.content = .loading }
        await store.receive(\.eventsResponse.failure) { $0.content = .failed }
    }

    /// Locale-independent: the era words come from the catalogue, the numbers never group.
    @Test func datesReadWithTheirUncertainty() {
        let circa = L10n.t("c."), debated = L10n.t("debated")
        let d = TimelineDates.text(for: david)
        #expect(d.hasPrefix(circa + " ") && d.contains("1010–970") && !d.contains(debated))
        let e = TimelineDates.text(for: exodus)
        #expect(!e.hasPrefix(circa) && e.contains("1446–1250") && e.hasSuffix(debated))
        let c = TimelineDates.text(for: crucifixion)
        #expect(c.contains("30–33") && c.hasSuffix(debated))
        let t = TimelineDates.text(for: temple)
        #expect(t.hasPrefix(circa) && t.contains("516") && t.contains(" – ") && t.contains("70"))
        let s = TimelineDates.text(for: samaria)
        #expect(s.hasPrefix(circa) && s.contains("722") && !s.contains("–"))
        #expect(TimelineDates.text(for: unknown) == L10n.t("date unknown"))
        #expect(TimelineDates.year(-1010) == L10n.t("\(String(1010)) BC"))
    }
}
