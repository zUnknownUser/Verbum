import Foundation
import Testing
@testable import Models

@Suite struct BibleEntityTests {
    @Test func entityTypeRawValuesMatchSpec() {
        // Spec §22.1 — these strings are the wire contract.
        #expect(BibleEntityType.allCases.map(\.rawValue) == [
            "person", "place", "event", "theme", "passage", "book",
            "prophecy", "originalTerm", "historicalPeriod",
        ])
    }

    @Test func relationshipTypeRawValuesMatchSpec() {
        // Spec §22.2.
        #expect(RelationshipType.allCases.map(\.rawValue) == [
            "appearsIn", "participatesIn", "occursAt", "occursDuring", "references",
            "relatedToTheme", "relatedTo", "precedes", "follows", "fulfills", "quotes",
        ])
    }

    @Test func entityCodableRoundTrip() throws {
        let entity = BibleEntity(id: "fixture-david", type: .person, name: "David", summary: nil)
        let data = try JSONEncoder().encode(entity)
        let decoded = try JSONDecoder().decode(BibleEntity.self, from: data)
        #expect(decoded == entity)
        #expect(decoded.summary == nil)
    }

    @Test func relationshipCodableRoundTrip() throws {
        let edge = BibleRelationship(
            id: "fixture-edge",
            sourceId: "fixture-david",
            targetId: "fixture-goliath",
            type: .relatedTo,
            confidence: 0.9,
            sourceReferenceIds: ["fixture-src"]
        )
        let data = try JSONEncoder().encode(edge)
        #expect(try JSONDecoder().decode(BibleRelationship.self, from: data) == edge)
    }

    @Test func unknownEnumValueFailsDecoding() {
        let json = Data(#"{"id":"x","type":"angel","name":"x","summary":null}"#.utf8)
        #expect(throws: DecodingError.self) {
            try JSONDecoder().decode(BibleEntity.self, from: json)
        }
    }
}
