import Foundation
import Testing
import Models
@testable import Clients

@Suite struct SearchClientFixturesTests {
    /// The fixture reads the device language; tests pin it.
    private func search(_ query: String, locale: String = "en_US") async throws -> SearchResponse {
        let language: BookLanguage = locale.hasPrefix("pt") ? .portuguese : .english
        return try await SearchClient.fixtures(language: language).search(query: query)
    }

    @Test func referenceQueryRanksThePassageFirst() async throws {
        let response = try await search("Jn 3:16")
        #expect(response.passages == [PassageReference(bookId: "John", chapter: 3, verses: 16...16)])
        #expect(response.books.isEmpty) // the reference is the answer
        #expect(response.entities.isEmpty)
    }

    @Test func bookNameQuery() async throws {
        let response = try await search("sam")
        #expect(response.passages.isEmpty)
        #expect(response.books.map(\.id) == ["1Sam", "2Sam"])
        #expect(response.entities.map(\.name) == ["Samuel"])
    }

    @Test func entityQueryGroupsByType() async throws {
        let response = try await search("j")
        #expect(response.entities(of: .person).map(\.name) == ["Jesus", "John"])
        #expect(response.entities(of: .passage).isEmpty)
        #expect(response.entities(of: .place).map(\.name) == ["Jerusalem"])
        #expect(response.entities(of: .theme).map(\.name) == ["Justice"])
        #expect(!response.books.isEmpty)
    }

    @Test func nameCanBeMatchedByAnyWord() async throws {
        #expect(try await search("dav").entities(of: .person).map(\.name) == ["David"])
        #expect(try await search("dav").entities(of: .event).map(\.name) == ["Anointing of David", "David and Goliath", "David takes Jerusalem"])
        #expect(try await search("forgive").entities.map(\.name) == ["Forgiveness"])
    }

    @Test func emptyAndNoMatch() async throws {
        #expect(try await search("").isEmpty)
        #expect(try await search("   ").isEmpty)
        #expect(try await search("why did job suffer").isEmpty)
    }

    @Test func referencesFollowTheDeviceLanguage() async throws {
        let pt = try await search("Jo 3:16", locale: "pt_BR")
        #expect(pt.passages == [PassageReference(bookId: "John", chapter: 3, verses: 16...16)])
    }

    @Test func fixtureEntityIdsAreLabelled() {
        // Spec §78.7 — fixtures must be recognisable as fixtures.
        let searchable = EntityFixtureData.entities.filter { $0.type != .passage }
        #expect(searchable.allSatisfy { $0.id.hasPrefix("fixture.") })
        #expect(Set(EntityFixtureData.entities.map(\.id)).count == EntityFixtureData.entities.count)
    }
}
