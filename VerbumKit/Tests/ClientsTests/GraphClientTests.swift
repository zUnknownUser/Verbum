import Testing
import Models
@testable import Clients

@Suite struct GraphClientFixturesTests {
    let client = GraphClient.fixtures

    @Test func entityLookup() async throws {
        #expect(try await client.entity(id: "fixture.person.david").name == "David")
        await #expect(throws: GraphClientError.unknownEntity("nope")) { try await client.entity(id: "nope") }
    }

    @Test func davidNeighbourhoodIsTheGoldenPath() async throws {
        let snapshot = try await client.neighbors(id: "fixture.person.david", limit: 12)
        #expect(snapshot.root.name == "David")
        #expect(snapshot.nodes.count == 12)
        #expect(snapshot.nodes(of: .person).map(\.name) == ["Goliath", "Saul", "Samuel", "Bathsheba", "Solomon"])
        #expect(snapshot.nodes(of: .passage).map(\.name) == ["1 Samuel 16", "1 Samuel 17", "2 Samuel 5", "Psalms 23", "Psalms 51"])
        #expect(snapshot.edges.allSatisfy { !$0.sourceReferenceIds.isEmpty }) // §33
    }

    @Test func limitCapsNodesAndEdgesFollow() async throws {
        let snapshot = try await client.neighbors(id: "fixture.person.david", limit: 3)
        #expect(snapshot.nodes.count == 3)
        let ids = Set(snapshot.nodes.map(\.id))
        #expect(snapshot.edges.allSatisfy { ids.contains($0.sourceId) || ids.contains($0.targetId) })
    }

    @Test func neighbourhoodIsUndirected() async throws {
        // Goliath → David is stored as David → Goliath.
        let snapshot = try await client.neighbors(id: "fixture.person.goliath", limit: 12)
        #expect(snapshot.nodes.map(\.name).contains("David"))
        #expect(snapshot.nodes.map(\.name).contains("1 Samuel 17"))
    }

    @Test func detailsAreHedgedAndSourced() async throws {
        let david = try await client.detail(id: "fixture.person.david")
        #expect(david.role == "Second king of Israel")
        #expect(david.approximateDates?.contains("commonly dated") == true)
        #expect(david.keyPassages.first == PassageReference(bookId: "1Sam", chapter: 16))
        #expect(!david.sources.isEmpty)
        for detail in EntityFixtureData.details.values {
            if let dates = detail.approximateDates {
                #expect(dates.contains("c.") || dates.contains("commonly") || dates.contains("disputed") || dates.contains("vary") || dates.contains("during") || dates.contains("early"), "\(detail.entity.name): \(dates)")
            }
        }
    }

    @Test func entitiesWithoutADetailStillHaveAPage() async throws {
        let mary = try await client.detail(id: "fixture.person.mary")
        #expect(mary.entity.name == "Mary")
        #expect(mary.keyPassages.isEmpty)
        #expect(mary.sources == [EntityFixtureData.editorialSource])
    }

    @Test func everyRelationshipPointsAtKnownEntitiesAndSources() {
        let ids = Set(EntityFixtureData.entities.map(\.id))
        let sourceIds = Set(EntityFixtureData.sources.map(\.id))
        for edge in EntityFixtureData.relationships {
            #expect(ids.contains(edge.sourceId), "\(edge.id)")
            #expect(ids.contains(edge.targetId), "\(edge.id)")
            #expect(edge.sourceReferenceIds.allSatisfy(sourceIds.contains), "\(edge.id)")
        }
        #expect(Set(EntityFixtureData.relationships.map(\.id)).count == EntityFixtureData.relationships.count)
    }

    @Test func entitiesByKindAreAlphabeticalAndNeverPassages() async throws {
        let people = try await client.entities(type: .person)
        #expect(people.count == 13)
        #expect(people.map(\.name) == people.map(\.name).sorted())
        #expect(try await client.entities(type: .place).count == 10)
        #expect(try await client.entities(type: .theme).count == 10)
        #expect(try await client.entities(type: .event).count == 4)
        #expect(try await client.entities(type: .passage).isEmpty)
    }

    @Test func passageNodesRoundTrip() {
        let ref = PassageReference(bookId: "1Sam", chapter: 17)
        #expect(EntityFixtureData.passageReference(for: EntityFixtureData.passageNode(ref)) == ref)
    }
}
