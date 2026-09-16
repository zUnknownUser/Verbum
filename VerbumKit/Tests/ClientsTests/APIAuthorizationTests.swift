import Foundation
import Testing
@testable import Clients

@Suite struct APIAuthorizationTests {
    actor Tokens {
        var requests: [Bool] = []
        func token(_ create: Bool) -> String { requests.append(create); return "test-id-token" }
    }

    @Test func paidJSONAndAudioAttachIdentityButPublicContentDoesNot() async throws {
        let script = VerbumAPITests.Script()
        script.responses = [(200, #"{"entities":[]}"#), (200, "{}"), (200, "audio")]
        let tokens = Tokens()
        let api = VerbumAPI(baseURL: URL(string: "https://api.example.test")!, transport: script.transport, cache: .inMemory, tokenProvider: { await tokens.token($0) })
        _ = try await api.entities(of: .person)
        let _: [String: String] = try await api.post("/v1/ask", body: ["question": "Who was David?"])
        _ = try await api.synthesizeSpeech(text: "Texto", language: "pt-BR")
        #expect(script.requests[0].value(forHTTPHeaderField: "Authorization") == nil)
        #expect(script.requests[1].value(forHTTPHeaderField: "Authorization") == "Bearer test-id-token")
        #expect(script.requests[2].value(forHTTPHeaderField: "Authorization") == "Bearer test-id-token")
        #expect(await tokens.requests == [true, true])
    }

    @Test func searchUsesExistingIdentityWithoutCreatingGuestAndSeparatesCache() async throws {
        let script = VerbumAPITests.Script()
        script.responses = Array(repeating: (200, #"{"query":"David","passages":[],"books":[],"entities":[]}"#), count: 2)
        let base = URL(string: "https://api.example.test")!, cache = ResponseCache.inMemory
        let publicAPI = VerbumAPI(baseURL: base, transport: script.transport, cache: cache)
        _ = try await publicAPI.search("David")
        let tokens = Tokens()
        let signedAPI = VerbumAPI(baseURL: base, transport: script.transport, cache: cache, tokenProvider: { await tokens.token($0) })
        _ = try await signedAPI.search("David")
        #expect(script.requests.count == 2)
        #expect(script.requests[1].value(forHTTPHeaderField: "X-Verbum-Installation")?.isEmpty == false)
        #expect(await tokens.requests == [false])
        #expect(script.requests[1].value(forHTTPHeaderField: "Authorization") == "Bearer test-id-token")
    }

    @Test func credentialFailureNeverSendsPaidRequest() async {
        let script = VerbumAPITests.Script()
        let api = VerbumAPI(baseURL: URL(string: "https://api.example.test")!, transport: script.transport, cache: .inMemory, tokenProvider: { _ in throw VerbumAPIError.problem(.unauthenticated, status: 401) })
        await #expect(throws: VerbumAPIError.problem(.unauthenticated, status: 401)) {
            try await api.synthesizeSpeech(text: "Texto", language: "pt-BR")
        }
        #expect(script.requests.isEmpty)
    }
}
