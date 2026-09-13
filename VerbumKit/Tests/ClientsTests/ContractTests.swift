import Foundation
import Testing
import Models
@testable import Clients

/// The apps' side of the contract (api/README.md): every `api/examples/*.json`
/// — the responses the backend proves it serves byte-for-byte — decodes
/// through the live clients into exactly what the fixture clients answer.
/// A change to the JSON breaks here before it breaks a user.
@Suite struct ContractTests {
    static let examples = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .appendingPathComponent("api/examples", isDirectory: true)

    /// Serves one example file for one exact path + query; anything else is a test failure.
    static func api(_ file: String, at path: String, query: String? = nil) -> VerbumAPI {
        let data = try! Data(contentsOf: examples.appendingPathComponent(file))
        return VerbumAPI(baseURL: URL(string: "http://contract.test")!, transport: { request in
            let url = request.url!
            #expect(url.path == path)
            #expect(url.query == query)
            return (data, HTTPURLResponse(url: url, statusCode: 200, httpVersion: nil, headerFields: nil)!)
        }, cache: .inMemory)
    }

    @Test func entityDetail() async throws {
        let live = GraphClient.live(api: Self.api("entities/david.json", at: "/v1/entities/fixture.person.david"))
        #expect(try await live.detail(id: "fixture.person.david") == GraphClient.fixtures.detail(id: "fixture.person.david"))
        #expect(try await live.entity(id: "fixture.person.david") == GraphClient.fixtures.entity(id: "fixture.person.david"))
    }

    @Test func entityList() async throws {
        let live = GraphClient.live(api: Self.api("entities/people.json", at: "/v1/entities", query: "type=person&lang=en"), language: .english)
        let expected = try await GraphClient.fixtures.entities(type: .person)
        #expect(try await live.entities(type: .person) == expected)
        #expect(!expected.isEmpty)
    }

    /// The server orders neighbours by confidence then name and returns every
    /// edge among `root + nodes` (§44); the fixture keeps insertion order and
    /// only the root's edges. Same neighbourhood, richer edges.
    @Test func graph() async throws {
        let live = GraphClient.live(api: Self.api("graph/david.json", at: "/v1/entities/fixture.person.david/graph", query: "limit=24"))
        let got = try await live.neighbors(id: "fixture.person.david", limit: 24)
        let expected = try await GraphClient.fixtures.neighbors(id: "fixture.person.david", limit: 24)
        #expect(got.root == expected.root)
        #expect(Set(got.nodes) == Set(expected.nodes))
        #expect(Set(expected.edges).isSubset(of: Set(got.edges)))
        let visible = Set(got.nodes.map(\.id) + [got.root.id])
        #expect(got.edges.allSatisfy { visible.contains($0.sourceId) && visible.contains($0.targetId) })
        #expect(got.edges.allSatisfy { !$0.sourceReferenceIds.isEmpty }) // §33
    }

    @Test func context() async throws {
        let live = ContextClient.live(api: Self.api("context/1Sam.17.json", at: "/v1/passages/1Sam.17/context"))
        let reference = PassageReference(bookId: "1Sam", chapter: 17, verses: 45...47)
        let got = try #require(try await live.chapter(reference: reference))
        let expected = try #require(try await ContextClient.fixtures.chapter(reference: reference))
        #expect(got.reference == expected.reference)
        #expect(got.entities == expected.entities)
        #expect(got.relatedPassages == expected.relatedPassages)
        #expect(got.sources == expected.sources)
        #expect(!got.isFixture && expected.isFixture, "the server's content is not labelled as a fixture")
    }

    @Test func timeline() async throws {
        let live = TimelineClient.live(api: Self.api("timeline/all.json", at: "/v1/timeline"))
        #expect(try await live.events() == TimelineClient.fixtures.events())
    }

    @Test func search() async throws {
        let live = SearchClient.live(api: Self.api("search/david.json", at: "/v1/search", query: "q=David&lang=en"), language: .english)
        #expect(try await live.search(query: "David") == SearchClient.fixtures(language: .english).search(query: "David"))
    }

    @Test func dailyVerse() async throws {
        let api = Self.api("daily-verse/week.json", at: "/v1/daily-verse", query: "from=2026-09-13&days=7")
        let week = try await api.dailyVerses(from: "2026-09-13", days: 7)
        #expect(week.count == 7)
        for (offset, verse) in week.enumerated() {
            #expect(verse.reference == DailyVerses.verse(year: 2026, month: 9, day: 13 + offset), "\(verse.date)")
        }
    }
}
