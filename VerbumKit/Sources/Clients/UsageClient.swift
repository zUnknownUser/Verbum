import ComposableArchitecture
import Models
@DependencyClient public struct UsageClient: Sendable {
    public var status: @Sendable () async throws -> UsageStatus?
}
extension UsageClient: DependencyKey {
    public static let liveValue = Self(status: { try await VerbumAPI.shared.usageStatus() })
    public static let previewValue = Self(status: { nil })
    public static let testValue = Self(status: { nil })
}
extension DependencyValues {
    public var usageClient: UsageClient { get { self[UsageClient.self] } set { self[UsageClient.self] = newValue } }
}
