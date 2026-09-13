import Foundation
import Testing
import Models
@testable import Clients

/// `POST /v1/ask` → the §30 contract, and its failures in the page's terms (§52).
@Suite struct AskScriptureClientTests {
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

    static let answer = """
    {"answer":"David killed Goliath with a sling.","summary":"A sling and a stone.","passageReferences":[{"bookId":"1Sam","chapter":17,"verseStart":49,"verseEnd":50}],"entityReferences":["fixture.person.david"],"sourceReferences":[{"id":"fixture.source.web","citation":"World English Bible","url":"https://worldenglish.bible"}],"confidence":"high","interpretiveVariance":false}
    """

    func client(_ script: Script) -> AskScriptureClient {
        .live(api: VerbumAPI(baseURL: URL(string: "http://test.local:8080")!, transport: script.transport, cache: .inMemory))
    }

    @Test func postsTheQuestionAndDecodesTheContract() async throws {
        let script = Script()
        script.next = (200, Self.answer)
        let answer = try await client(script).ask(question: "  how did David defeat Goliath \n")
        #expect(answer.summary == "A sling and a stone.")
        #expect(answer.passageReferences == [PassageReference(bookId: "1Sam", chapter: 17, verses: 49...50)])
        #expect(answer.entityReferences == ["fixture.person.david"])
        #expect(answer.confidence == .high)
        #expect(!answer.isEmpty)
        let request = try #require(script.request)
        #expect(request.httpMethod == "POST")
        #expect(request.url?.absoluteString == "http://test.local:8080/v1/ask")
        #expect(request.value(forHTTPHeaderField: "Content-Type") == "application/json")
        #expect(String(decoding: request.httpBody ?? Data(), as: UTF8.self) == #"{"question":"how did David defeat Goliath"}"#)
    }

    @Test func failuresInThePagesTerms() async {
        let script = Script()
        script.next = (503, #"{"code":"ask_unavailable","message":"ask is not configured on this server"}"#)
        await #expect(throws: AskScriptureError.unavailable) { try await client(script).ask(question: "q") }
        script.next = (404, "404 page not found")   // a backend without the route at all
        await #expect(throws: AskScriptureError.unavailable) { try await client(script).ask(question: "q") }
        script.next = (502, #"{"code":"internal","message":"could not answer this question"}"#)
        await #expect(throws: AskScriptureError.failed) { try await client(script).ask(question: "q") }
        script.next = (200, "not json")
        await #expect(throws: AskScriptureError.failed) { try await client(script).ask(question: "q") }
        script.fail = true
        await #expect(throws: AskScriptureError.networkUnavailable) { try await client(script).ask(question: "q") }
    }

    @Test func anEmptyAnswerIsTheFallbackShape() throws {
        let data = Data(#"{"answer":"","summary":"No sufficiently relevant Scripture passages were found for this question.","passageReferences":[],"entityReferences":[],"sourceReferences":[],"confidence":"low","interpretiveVariance":false}"#.utf8)
        let answer = try JSONDecoder().decode(ScriptureAnswer.self, from: data)
        #expect(answer.isEmpty)
        #expect(answer.confidence == .low)
    }
}
