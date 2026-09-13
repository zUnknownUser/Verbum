import Foundation

/// Firebase owns credential persistence. Views receive only this safe snapshot.
public struct AuthSession: Equatable, Sendable {
    public let id: String
    public let email: String?
    public let isAnonymous: Bool
    public let isEmailVerified: Bool
    public init(id: String, email: String?, isAnonymous: Bool, isEmailVerified: Bool) {
        self.id = id; self.email = email; self.isAnonymous = isAnonymous; self.isEmailVerified = isEmailVerified
    }
}

public enum AccountFailure: Error, Equatable, Sendable {
    case invalidEmail, passwordRequired, passwordTooShort, passwordMismatch
    case credentials, emailInUse, network, tooManyRequests, disabled, configuration, recentLoginRequired, unexpected
}

public enum AccountValidation {
    public static func email(_ value: String) -> String { value.trimmingCharacters(in: .whitespacesAndNewlines) }
    public static func validate(email: String, password: String, confirmation: String?, reset: Bool = false) -> AccountFailure? {
        let address = self.email(email)
        guard address.count <= 254, address.range(of: #"^[^\s@]+@[^\s@]+\.[^\s@]+$"#, options: .regularExpression) != nil else { return .invalidEmail }
        if reset { return nil }
        guard !password.isEmpty else { return .passwordRequired }
        if let confirmation {
            guard password.unicodeScalars.count >= 8 else { return .passwordTooShort }
            guard password == confirmation else { return .passwordMismatch }
        }
        return nil
    }
}
