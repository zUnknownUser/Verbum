import Clients
import Models
import Testing

@Suite struct TimelineClientTests {
    @Test func coversTheSpecsListInOrderWithHedgedDates() async throws {
        let events = try await TimelineClient.fixtures.events()
        #expect(events.count == 16)
        #expect(events.first?.title == "Abraham")
        #expect(events.last?.title == "Pauline missions")
        // Chronological, unknown last.
        let starts = events.compactMap(\.startYear)
        #expect(starts == starts.sorted())
        // Nothing is asserted as exact; the split datings say so.
        #expect(events.allSatisfy { $0.datePrecision != .exact })
        #expect(events.first { $0.id.hasSuffix("exodus") }?.datePrecision == .debated)
        #expect(events.first { $0.id.hasSuffix("crucifixion") }?.datePrecision == .debated)
        // Every claim is sourced and every linked entity exists in the graph fixture (§33).
        #expect(events.allSatisfy { !$0.sourceReferenceIds.isEmpty && $0.summary?.isEmpty == false })
        for event in events {
            for id in event.entityIds {
                _ = try await GraphClient.fixtures.entity(id: id)
            }
        }
    }

    @Test func eventsForAnEntityAreItsOwn() async throws {
        let david = try await TimelineClient.fixtures.eventsFor(entityID: "fixture.person.david")
        #expect(david.map(\.title) == ["United Monarchy", "Reign of David"])
        #expect(try await TimelineClient.fixtures.eventsFor(entityID: "fixture.theme.love").isEmpty)
    }
}
