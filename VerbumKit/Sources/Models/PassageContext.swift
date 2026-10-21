/// Chapter-level context. Missing coverage is represented by a nil response,
/// not by a generated explanation. References remain structured for navigation.
public struct PassageContext: Equatable, Sendable, Codable {
    public let reference: PassageReference
    public let entities: [BibleEntity]
    public let relatedPassages: [PassageReference]
    public let sources: [SourceReference]
    public let isFixture: Bool
    public let study: ChapterStudy?

    public init(reference: PassageReference, entities: [BibleEntity], relatedPassages: [PassageReference], sources: [SourceReference], isFixture: Bool, study: ChapterStudy? = nil) {
        self.reference = reference
        self.entities = entities
        self.relatedPassages = relatedPassages
        self.sources = sources
        self.isFixture = isFixture
        self.study = study
    }
}

public struct StudyNote: Equatable, Sendable, Codable {
    public let text: String
    public let references: [PassageReference]
}

public struct StudyOutline: Equatable, Sendable, Codable {
    public let title: String
    public let reference: PassageReference
}

public struct StudyConnection: Equatable, Sendable, Codable {
    public let reference: PassageReference
    public let explanation: String
}

/// Optional editorial study; all reading actions stay in Features and Clients.
public struct ChapterStudy: Equatable, Sendable, Codable {
    public let title: String
    public let summary: StudyNote
    public let participants: StudyNote
    public let setting: StudyNote
    public let literaryContext: StudyNote
    public let outline: [StudyOutline]
    public let connections: [StudyConnection]
    public let sourceReferenceIds: [String]
}
