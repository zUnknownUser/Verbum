import ComposableArchitecture
import Models

@DependencyClient
public struct BibleComparisonClient: Sendable {
    public var passage: @Sendable (_ reference: PassageReference) async throws -> BiblePassage
}
extension BibleComparisonClient: DependencyKey {
    public static let liveValue = Self(passage: { reference in
        if BookLanguage.current == .portuguese {
            return try await HelloAOBibleClient(translationId: "por_bsl").passage(reference)
        }
        return try await BibleClient.bundled.passage(reference: reference)
    })
    public static let previewValue = Self(passage: { reference in try await BibleClient.bundled.passage(reference: reference) })
}
extension DependencyValues {
    public var bibleComparison: BibleComparisonClient {
        get { self[BibleComparisonClient.self] }
        set { self[BibleComparisonClient.self] = newValue }
    }
}
