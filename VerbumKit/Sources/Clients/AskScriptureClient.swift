import ComposableArchitecture
import Models

/// Ask Scripture (spec §38 `AskScriptureClient`, §13): a question in the user's
/// words, answered by the server's retrieval-first pipeline (§29). The client
/// never generates anything itself and never caches a question (§47).
@DependencyClient
public struct AskScriptureClient: Sendable {
    public var ask: @Sendable (_ question: String) async throws -> ScriptureAnswer
}

/// Why a question could not be answered, in the states the page shows (§52).
public enum AskScriptureError: Error, Equatable, Sendable {
    /// The server has no synthesis configured (`503 ask_unavailable`) — or the
    /// feature is not offered by this backend at all.
    case unavailable
    /// Could not reach the server. Ask needs a connection (§39: no local model).
    case networkUnavailable
    /// The server tried and could not answer (`502`), or answered outside the contract.
    case failed
}

extension AskScriptureClient: DependencyKey {
    public static let liveValue = AskScriptureClient.live(api: .shared)
    /// A canned, sourced answer so previews render every section.
    public static let previewValue = AskScriptureClient(ask: { _ in .preview })
}

extension DependencyValues {
    public var askScriptureClient: AskScriptureClient {
        get { self[AskScriptureClient.self] }
        set { self[AskScriptureClient.self] = newValue }
    }
}

extension AskScriptureClient {
    /// `POST /v1/ask` (api/openapi.yaml). 503 and a route the server does not
    /// have are both `.unavailable`; the page says so instead of failing.
    public static func live(api: VerbumAPI) -> AskScriptureClient {
        AskScriptureClient(ask: { question in
            let trimmed = String(question.trimmingCharacters(in: .whitespacesAndNewlines).prefix(500))
            do {
                return try await api.ask(trimmed)
            } catch VerbumAPIError.problem(.askUnavailable, _) {
                throw AskScriptureError.unavailable
            } catch VerbumAPIError.problem(_, status: 404), VerbumAPIError.problem(_, status: 501) {
                throw AskScriptureError.unavailable
            } catch VerbumAPIError.networkUnavailable {
                throw AskScriptureError.networkUnavailable
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                throw AskScriptureError.failed
            }
        })
    }
}

extension VerbumAPI {
    /// `POST /v1/ask {"question"}` → the §30 contract. Never cached.
    public func ask(_ question: String) async throws -> ScriptureAnswer {
        struct Body: Encodable { let question: String }
        return try await post("/v1/ask", body: Body(question: question))
    }
}

extension ScriptureAnswer {
    public static let preview = ScriptureAnswer(
        answer: "David refused Saul's armour and met Goliath with a sling and his trust in the LORD, striking him on the forehead with a stone; the narrative frames the victory as the LORD's, not the weapon's.",
        summary: "David killed Goliath with a sling and a stone, crediting the LORD.",
        passageReferences: [
            PassageReference(bookId: "1Sam", chapter: 17, verses: 45...47),
            PassageReference(bookId: "1Sam", chapter: 17, verses: 49...50),
        ],
        entityReferences: ["fixture.person.david", "fixture.person.goliath"],
        sourceReferences: [SourceReference(id: "fixture.source.web", citation: "World English Bible (public domain) — the passages cited", url: "https://worldenglish.bible")],
        confidence: .high,
        interpretiveVariance: false
    )
}
