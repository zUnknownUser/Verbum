import Foundation
import Models
import Testing
@testable import Clients

@Suite struct UsagePolicyTests {
    @Test func restrictionKeepsResetAndUsageIsNeverCached() async throws {
        let script = VerbumAPITests.Script()
        let status = #"{"plan":"free","resetsAt":"2026-09-17T00:00:00Z","remaining":{"ask":4,"tts":1,"voice":0},"voiceSeconds":60,"restricted":false}"#
        script.responses = [(200, status), (200, status), (429, #"{"code":"quota_exceeded","message":"limited","retryAt":"2026-09-17T00:00:00Z"}"#)]
        let api = VerbumAPI(baseURL: URL(string: "https://api.example.test")!, transport: script.transport, cache: .inMemory, tokenProvider: { _ in "token" })
        #expect(try await api.usageStatus()?.remaining["ask"] == 4)
        _ = try await api.usageStatus()
        #expect(script.requests.count == 2)
        #expect(script.requests.allSatisfy { $0.value(forHTTPHeaderField: "Authorization") == "Bearer token" })
        await #expect(throws: VerbumAPIError.restricted(.init(code: "quota_exceeded", retryAt: "2026-09-17T00:00:00Z"))) {
            try await api.synthesizeSpeech(text: "Texto", language: "pt-BR")
        }
    }
    @Test func relayCannotChangeTheBackendHost() {
        let api = VerbumAPI(baseURL: URL(string: "https://api.example.test")!, cache: .inMemory)
        #expect(api.relayURL(path: "/v1/realtime/connect")?.absoluteString == "wss://api.example.test/v1/realtime/connect")
        #expect(api.relayURL(path: "https://attacker.test/relay") == nil)
    }
    @Test func indexedFallbackDoesNotBecomeAnAIAnswer() throws {
        let raw = #"{"answer":"","summary":"","passageReferences":[{"bookId":"John","chapter":3}],"entityReferences":[],"sourceReferences":[],"confidence":"low","interpretiveVariance":false,"fallback":{"code":"budget_exhausted"}}"#
        let answer = try JSONDecoder().decode(ScriptureAnswer.self, from: Data(raw.utf8))
        #expect(answer.isEmpty && answer.fallback?.code == "budget_exhausted")
        #expect(answer.passageReferences.count == 1)
    }
}
