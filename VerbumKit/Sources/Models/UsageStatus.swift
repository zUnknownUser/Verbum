import Foundation

public struct UsageRestriction: Codable, Equatable, Sendable {
    public let code: String
    public let retryAt: String?
    public init(code: String, retryAt: String? = nil) { self.code = code; self.retryAt = retryAt }
    public var resetDate: Date? { retryAt.flatMap { ISO8601DateFormatter().date(from: $0) } }
}
public struct UsageStatus: Codable, Equatable, Sendable {
    public let plan: String
    public let resetsAt: String
    public let remaining: [String: Int]
    public let voiceSeconds: Int
    public let restricted: Bool
    public var resetDate: Date? { ISO8601DateFormatter().date(from: resetsAt) }
}
