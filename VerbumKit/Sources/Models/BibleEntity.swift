/// Spec §22.1.
public enum BibleEntityType: String, Codable, Sendable, CaseIterable {
    case person
    case place
    case event
    case theme
    case passage
    case book
    case prophecy
    case originalTerm
    case historicalPeriod
}

/// Spec §22.1.
public struct BibleEntity: Identifiable, Codable, Equatable, Hashable, Sendable {
    public let id: String
    public let type: BibleEntityType
    public let name: String
    public let summary: String?

    public init(id: String, type: BibleEntityType, name: String, summary: String?) {
        self.id = id
        self.type = type
        self.name = name
        self.summary = summary
    }
}
