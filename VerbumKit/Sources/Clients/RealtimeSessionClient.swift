import ComposableArchitecture
import Models

/// The short-lived credential a voice session starts with (`POST /v1/realtime/session`).
/// It is OpenAI's ephemeral client secret, minted by our backend so the real key never
/// ships in the app (spec §56). Never logged, never persisted; it can only *start* a
/// session until `expiresAt`.
public struct RealtimeSession: Decodable, Equatable, Sendable {
    public let clientSecret: String
    /// Unix seconds.
    public let expiresAt: Int
    public let model: String

    public init(clientSecret: String, expiresAt: Int, model: String) {
        self.clientSecret = clientSecret
        self.expiresAt = expiresAt
        self.model = model
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
            } catch VerbumAPIError.problem(.realtimeUnavailable, _) {
                throw VoiceError.unavailable
            } catch VerbumAPIError.problem(_, status: 404), VerbumAPIError.problem(_, status: 501) {
                throw VoiceError.unavailable
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
        return try await post("/v1/realtime/session", body: Empty())
    }
}
