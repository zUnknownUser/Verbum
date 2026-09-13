import ComposableArchitecture
import Models

@DependencyClient
public struct AccountClient: Sendable {
    public var sessions: @Sendable () -> AsyncStream<AuthSession?> = { .finished }
    public var signIn: @Sendable (_ email: String, _ password: String) async throws -> AuthSession
    public var register: @Sendable (_ email: String, _ password: String) async throws -> AuthSession
    public var anonymous: @Sendable () async throws -> AuthSession
    public var resetPassword: @Sendable (_ email: String) async throws -> Void
    public var sendVerification: @Sendable () async throws -> Void
    public var refresh: @Sendable () async throws -> AuthSession?
    public var signOut: @Sendable () async throws -> Void
    public var deleteAccount: @Sendable (_ password: String) async throws -> Void
}

extension AccountClient: DependencyKey {
    public static let liveValue = Self.firebase
    public static let previewValue = Self(sessions: { AsyncStream { $0.yield(nil); $0.finish() } })
}
extension DependencyValues {
    public var accountClient: AccountClient {
        get { self[AccountClient.self] }
        set { self[AccountClient.self] = newValue }
    }
}
