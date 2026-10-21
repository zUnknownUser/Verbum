import Foundation
import Models
import Testing

@Suite struct ThemeStudyTests {
    private static let entities = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .appendingPathComponent("api/examples/entities")

    @Test func legacyEntityDetailStillDecodesWithoutThemeStudy() throws {
        let data = try Data(contentsOf: Self.entities.appendingPathComponent("david.json"))
        let detail = try JSONDecoder().decode(EntityDetail.self, from: data)
        #expect(detail.themeStudy == nil)
        #expect(!detail.keyPassages.isEmpty)
    }

    @Test(arguments: ["en", "pt"])
    func studyRoundTripPreservesStructuredEvidence(language: String) throws {
        let data = try Data(contentsOf: Self.entities.appendingPathComponent("faith-study.\(language).json"))
        let detail = try JSONDecoder().decode(EntityDetail.self, from: data)
        let study = try #require(detail.themeStudy)
        #expect(study.readings.count == 5)
        #expect(study.comparisons.count == 2)
        #expect(!study.overview.references.isEmpty)
        #expect(study.connections.allSatisfy { !$0.entity.id.isEmpty && !$0.explanation.isEmpty })
        #expect(Set(study.sourceReferenceIds).isSubset(of: Set(detail.sources.map(\.id))))
        let restored = try JSONDecoder().decode(EntityDetail.self, from: JSONEncoder().encode(detail))
        #expect(restored == detail)
    }

    @Test func partialStudyFailsDecodingRatherThanSilentlyDroppingStudySections() throws {
        let data = try Data(contentsOf: Self.entities.appendingPathComponent("faith-study.en.json"))
        var json = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
        var study = try #require(json["themeStudy"] as? [String: Any])
        study.removeValue(forKey: "comparisons")
        json["themeStudy"] = study
        let incomplete = try JSONSerialization.data(withJSONObject: json)
        #expect(throws: DecodingError.self) { try JSONDecoder().decode(EntityDetail.self, from: incomplete) }
    }
}
