import Foundation
import Testing
@testable import Clients

@Suite struct RealtimeSessionClientTests {
    final class Script: @unchecked Sendable {
        var next: (Int, String) = (200, "")
        var request: URLRequest?
        var fail = false
        var transport: VerbumAPI.Transport {
            { [self] request in
                self.request = request
                if fail { throw URLError(.notConnectedToInternet) }
                return (Data(next.1.utf8), HTTPURLResponse(url: request.url!, statusCode: next.0, httpVersion: nil, headerFields: nil)!)
            }
        }
    }

    func client(_ script: Script) -> RealtimeSessionClient {
        .live(api: VerbumAPI(baseURL: URL(string: "http://test.local:8080")!, transport: script.transport, cache: .inMemory))
    }

    @Test func mintsASession() async throws {
        let script = Script()
        script.next = (200, #"{"clientSecret":"ek_abc","expiresAt":1757800000,"model":"gpt-realtime"}"#)
        let session = try await client(script).create()
        #expect(session == RealtimeSession(clientSecret: "ek_abc", expiresAt: 1_757_800_000, model: "gpt-realtime"))
        #expect(script.request?.httpMethod == "POST")
        #expect(script.request?.url?.path == "/v1/realtime/session")
    }

    @Test func failuresInTheSheetsTerms() async {
        let script = Script()
        script.next = (503, #"{"code":"realtime_unavailable","message":"realtime is not configured on this server"}"#)
        await #expect(throws: VoiceError.unavailable) { try await client(script).create() }
        script.next = (404, "404 page not found")
        await #expect(throws: VoiceError.unavailable) { try await client(script).create() }
        script.next = (502, #"{"code":"internal","message":"could not create a realtime session"}"#)
        await #expect(throws: VoiceError.failed) { try await client(script).create() }
        script.fail = true
        await #expect(throws: VoiceError.networkUnavailable) { try await client(script).create() }
    }
}
