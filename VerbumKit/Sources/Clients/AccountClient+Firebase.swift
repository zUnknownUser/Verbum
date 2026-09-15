import ComposableArchitecture
import FirebaseAuth
import FirebaseCore
import Foundation
import Models

public enum FirebaseBootstrap {
    /// The plist is an app resource, not a domain/package resource. A mismatched
    /// configuration fails safely instead of changing the user's bundle identity.
    public static func configure() {
        guard FirebaseApp.app() == nil,
              let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path),
              options.bundleID == Bundle.main.bundleIdentifier else { return }
        FirebaseApp.configure(options: options)
        Auth.auth().useAppLanguage()
    }
}

extension AccountClient {
    static let firebase = Self(
        sessions: {
            guard let auth = try? configuredAuth() else { return AsyncStream { $0.yield(nil); $0.finish() } }
            return AsyncStream { continuation in
                // SDK listener tokens are opaque NSObjectProtocol values, not Sendable.
                // The token is only accessed under the lock for listener removal.
                let handle = LockIsolated(auth.addStateDidChangeListener { _, user in continuation.yield(user.map(snapshot)) })
                continuation.onTermination = { _ in handle.withValue { auth.removeStateDidChangeListener($0) } }
            }
        },
        signIn: { email, password in
            try await mapped { snapshot(try await configuredAuth().signIn(withEmail: email, password: password).user) }
        },
        register: { email, password in
            try await mapped {
                let auth = try configuredAuth()
                // Preserve a guest's Firebase UID when they create a new account.
                if let guest = auth.currentUser, guest.isAnonymous {
                    return snapshot(try await guest.link(with: EmailAuthProvider.credential(withEmail: email, password: password)).user)
                }
                return snapshot(try await auth.createUser(withEmail: email, password: password).user)
            }
        },
        anonymous: {
            try await mapped {
                _ = try await FirebaseAPITokens.shared.token(createIfNeeded: true)
                let auth = try configuredAuth()
                guard let user = auth.currentUser else { throw AccountFailure.credentials }
                return snapshot(user)
            }
        },
        resetPassword: { email in
            do { try await configuredAuth().sendPasswordReset(withEmail: email) }
            catch {
                // Never reveal whether an address is registered.
                if AuthErrorCode(rawValue: (error as NSError).code) == .userNotFound { return }
                throw mapFailure(error)
            }
        },
        sendVerification: {
            try await mapped {
                guard let user = try configuredAuth().currentUser, !user.isAnonymous else { throw AccountFailure.credentials }
                try await user.sendEmailVerification()
            }
        },
        refresh: {
            try await mapped {
                guard let user = try configuredAuth().currentUser else { return nil }
                try await user.reload()
                return snapshot(user)
            }
        },
        signOut: { try await mapped { try configuredAuth().signOut() } },
        deleteAccount: { password in
            try await mapped {
                guard let user = try configuredAuth().currentUser else { throw AccountFailure.credentials }
                if !user.isAnonymous {
                    guard let email = user.email, !password.isEmpty else { throw AccountFailure.passwordRequired }
                    try await user.reauthenticate(with: EmailAuthProvider.credential(withEmail: email, password: password))
                }
                try await user.delete()
            }
        }
    )
}

/// Serializes first-use anonymous sign-in across Ask, voice, audio and the account
/// sheet. Firebase owns token persistence/refresh; this actor never caches a token.
actor FirebaseAPITokens {
    static let shared = FirebaseAPITokens()
    private var signingIn: Task<Void, Error>?

    func token(createIfNeeded: Bool) async throws -> String? {
        let auth = try configuredAuth()
        if auth.currentUser == nil {
            guard createIfNeeded else { return nil }
            if let pending = signingIn {
                try await pending.value
            } else {
                let task = Task<Void, Error> {
                    if auth.currentUser == nil { _ = try await auth.signInAnonymously() }
                }
                signingIn = task
                defer { signingIn = nil }
                try await task.value
            }
        }
        try Task.checkCancellation()
        guard let user = auth.currentUser else { throw AccountFailure.credentials }
        let token = try await user.getIDToken()
        guard auth.currentUser?.uid == user.uid else { throw AccountFailure.credentials }
        return token
    }
}

private func configuredAuth() throws -> Auth {
    guard FirebaseApp.app() != nil else { throw AccountFailure.configuration }
    return Auth.auth()
}
private func snapshot(_ user: User) -> AuthSession {
    AuthSession(id: user.uid, email: user.email, isAnonymous: user.isAnonymous, isEmailVerified: user.isEmailVerified)
}
private func mapped<T>(_ operation: () async throws -> T) async throws -> T {
    do { return try await operation() } catch { throw mapFailure(error) }
}
private func mapFailure(_ error: Error) -> AccountFailure {
    if let error = error as? AccountFailure { return error }
    switch AuthErrorCode(rawValue: (error as NSError).code) {
    case .invalidEmail: return .invalidEmail
    case .wrongPassword, .invalidCredential, .userNotFound: return .credentials
    case .emailAlreadyInUse, .credentialAlreadyInUse: return .emailInUse
    case .weakPassword: return .passwordTooShort
    case .networkError: return .network
    case .tooManyRequests: return .tooManyRequests
    case .userDisabled: return .disabled
    case .operationNotAllowed, .invalidAPIKey, .appNotAuthorized: return .configuration
    case .requiresRecentLogin: return .recentLoginRequired
    default: return .unexpected
    }
}
