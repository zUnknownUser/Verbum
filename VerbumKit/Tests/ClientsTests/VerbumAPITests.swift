import Foundation
import Testing
import Models
@testable import Clients

/// Plumbing of `VerbumAPI`: URLs, error mapping (§52) and the offline cache (§39).
@Suite struct VerbumAPITests {
    /// A transport scripted per request; records what was asked.
    final class Script: @unchecked Sendable {
        var responses: [(status: Int, body: String)] = []
        var requests: [URLRequest] = []
        var failWith: Error?

        var transport: VerbumAPI.Transport {
            { [self] request in
                requests.append(request)
                if let failWith { throw failWith }
                let next = responses.isEmpty ? (200, "{}") : responses.removeFirst()
                let http = HTTPURLResponse(url: request.url!, statusCode: next.0, httpVersion: nil, headerFields: nil)!
                return (Data(next.1.utf8), http)
            }
        }
    }

    let base = URL(string: "http://test.local:8080")!

    @Test func buildsTheContractURLs() async throws {
        let script = Script()
        script.responses = [
            (200, #"{"entities":[]}"#),
            (200, #"{"root":{"id":"a","type":"person","name":"A","summary":null},"nodes":[],"edges":[]}"#),
            (200, #"{"events":[],"entityNames":{}}"#),
            (200, #"{"query":"x","passages":[],"books":[],"entities":[]}"#),
            (200, #"{"verses":[]}"#),
        ]
        let api = VerbumAPI(baseURL: base, transport: script.transport, cache: .inMemory)
        _ = try await api.entities(of: .person, language: .english)
        _ = try await api.graph("a", limit: 24)
        _ = try await api.timeline(entity: "fixture.person.david")
        _ = try await api.search("x", language: .portuguese)
        _ = try await api.dailyVerses(from: "2026-09-13", days: 7)
        #expect(script.requests.map { $0.url!.absoluteString } == [
            "http://test.local:8080/v1/entities?type=person&lang=en",
            "http://test.local:8080/v1/entities/a/graph?limit=24",
            "http://test.local:8080/v1/timeline?entity=fixture.person.david",
            "http://test.local:8080/v1/search?q=x&lang=pt",
            "http://test.local:8080/v1/daily-verse?from=2026-09-13&days=7",
        ])
        #expect(script.requests.allSatisfy { $0.value(forHTTPHeaderField: "Accept") == "application/json" })
    }

    @Test func problemCodesBecomeTypedErrors() async throws {
        let script = Script()
        script.responses = [
            (404, #"{"code":"unknown_entity","message":"no entity with id 'nope'"}"#),
            (404, #"{"code":"content_unavailable","message":"no context for Gen.1"}"#),
            (503, #"{"code":"ask_unavailable","message":"ask is not configured"}"#),
            (500, "not json"),
            (200, "not json either"),
        ]
        let api = VerbumAPI(baseURL: base, transport: script.transport, cache: .inMemory)
        await #expect(throws: VerbumAPIError.problem(.unknownEntity, status: 404)) { try await api.entityDetail("nope") }
        // Missing context is a state, not an error (§3.5).
        #expect(try await api.context(PassageReference(bookId: "Gen", chapter: 1, verses: 3...5)) == nil)
        await #expect(throws: VerbumAPIError.problem(.askUnavailable, status: 503)) { try await api.entityDetail("x") }
        await #expect(throws: VerbumAPIError.problem(.unknown, status: 500)) { try await api.entityDetail("x") }
        await #expect(throws: VerbumAPIError.malformedResponse) { try await api.entityDetail("x") }
        // The context path is Book.Chapter — verses never reach the URL.
        #expect(script.requests[1].url!.path == "/v1/passages/Gen.1/context")
    }

    @Test func transportFailureIsNetworkUnavailable() async {
        let script = Script()
        script.failWith = URLError(.notConnectedToInternet)
        let api = VerbumAPI(baseURL: base, transport: script.transport, cache: .inMemory)
        await #expect(throws: VerbumAPIError.networkUnavailable) { try await api.timeline() }
    }

    @Test func freshAnswersAreServedFromTheCacheAndStaleOnesSurviveAFailure() async throws {
        let script = Script()
        let body = #"{"events":[],"entityNames":{}}"#
        script.responses = [(200, body), (200, body)]
        let clock = Clock()
        let api = VerbumAPI(baseURL: base, transport: script.transport, cache: .inMemory, now: { clock.now })

        _ = try await api.timeline()
        _ = try await api.timeline()
        #expect(script.requests.count == 1, "fresh within max-age: no second request")

        clock.now += VerbumAPI.freshFor + 1
        _ = try await api.timeline()
        #expect(script.requests.count == 2, "stale: revalidated")

        clock.now += VerbumAPI.freshFor + 1
        script.failWith = URLError(.timedOut)
        let offline = try await api.timeline()
        #expect(offline.events.isEmpty, "stale answer beats no answer (§39)")
        #expect(script.requests.count == 3)

        await #expect(throws: VerbumAPIError.networkUnavailable) { try await api.timeline(entity: "never-seen") }
    }

    @Test func cacheSurvivesOnDisk() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let script = Script()
        script.responses = [(200, #"{"events":[],"entityNames":{}}"#)]
        let first = VerbumAPI(baseURL: base, transport: script.transport, cache: ResponseCache(directory: directory))
        _ = try await first.timeline()

        script.failWith = URLError(.timedOut)
        let second = VerbumAPI(baseURL: base, transport: script.transport, cache: ResponseCache(directory: directory))
        _ = try await second.timeline()
    }

    @Test func configurationFallsBackToLocalInDebug() {
        // No `VerbumAPIBaseURL` in the test host's Info.plist.
        #expect(VerbumAPI.Configuration.baseURL == VerbumAPI.Configuration.local)
    }

    final class Clock: @unchecked Sendable {
        var now = Date(timeIntervalSince1970: 1_000_000)
    }
}
