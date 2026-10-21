/// An optional, sourced study attached to an existing theme. Missing coverage
/// stays nil; domain models never fetch passages or decide navigation.
public struct ThemeStudy: Codable, Equatable, Sendable {
    public let overview: StudyNote
    public let readings: [ThemeStudyReading]
    public let comparisons: [ThemeStudyComparison]
    public let connections: [ThemeStudyEntityConnection]
    public let sourceReferenceIds: [String]
}

public struct ThemeStudyReading: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let title: String
    public let reference: PassageReference
    public let context: String
    public let observation: String
    public let significance: String
    public let question: String
}

public struct ThemeStudyComparison: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let title: String
    public let references: [PassageReference]
    public let explanation: String
    public let difference: String
}

public struct ThemeStudyEntityConnection: Codable, Equatable, Sendable {
    public let entity: BibleEntity
    public let reference: PassageReference
    public let explanation: String
}
