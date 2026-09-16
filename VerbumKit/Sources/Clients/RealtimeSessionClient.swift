import ComposableArchitecture
import Models
import Foundation

/// The short-lived credential a voice session starts with (`POST /v1/realtime/session`).
/// It is a one-use Verbum relay ticket; the provider key stays on the backend. Never logged, never persisted; it can only *start* a
/// session until `expiresAt`.
public struct RealtimeSession: Decodable, Equatable, Sendable {
    private enum CodingKeys: String, CodingKey { case clientSecret, expiresAt, model, relayPath, maxDurationSeconds }
    public let clientSecret: String
    /// Unix seconds.
    public let expiresAt: Int
    public let model: String
    public var relayPath: String?
    public var relayURL: URL?
    public var maxDurationSeconds: Int?

    public init(clientSecret: String, expiresAt: Int, model: String, relayURL: URL? = nil) {
        self.clientSecret = clientSecret
        self.expiresAt = expiresAt
        self.model = model
        self.relayURL = relayURL
    }
}

@DependencyClient
public struct RealtimeSessionClient: Sendable {
    public var create: @Sendable () async throws -> RealtimeSession
}

extension RealtimeSessionClient: DependencyKey {
    public static let liveValue = RealtimeSessionClient.live(api: .shared)
    public static let previewValue = RealtimeSessionClient(create: { RealtimeSession(clientSecret: "ek_preview", expiresAt: 0, model: "gpt-realtime") })
}

extension DependencyValues {
    public var realtimeSessionClient: RealtimeSessionClient {
        get { self[RealtimeSessionClient.self] }
        set { self[RealtimeSessionClient.self] = newValue }
    }
}

extension RealtimeSessionClient {
    /// 503 (no key on the server) and a backend without the route are both `.unavailable`.
    public static func live(api: VerbumAPI) -> RealtimeSessionClient {
        RealtimeSessionClient(create: {
            do {
                return try await api.realtimeSession()
            } catch VerbumAPIError.problem(.realtimeUnavailable, _), VerbumAPIError.problem(.authUnavailable, _) {
                throw VoiceError.unavailable
            } catch VerbumAPIError.problem(_, status: 404), VerbumAPIError.problem(_, status: 501) {
                throw VoiceError.unavailable
            } catch VerbumAPIError.restricted(let restriction) { throw VoiceError.limited(restriction)
            } catch VerbumAPIError.problem(.rateLimited, _) { throw VoiceError.limited(.init(code: "rate_limited"))
            } catch VerbumAPIError.networkUnavailable {
                throw VoiceError.networkUnavailable
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                throw VoiceError.failed
            }
        })
    }
}

extension VerbumAPI {
    /// `POST /v1/realtime/session`. Never cached (the server says `no-store`).
    public func realtimeSession() async throws -> RealtimeSession {
        struct Empty: Encodable {}
        var session: RealtimeSession = try await post("/v1/realtime/session", body: Empty())
        if let path = session.relayPath {
            guard let url = relayURL(path: path) else { throw VerbumAPIError.malformedResponse }
            session.relayURL = url
        }
        return session
    }
}
