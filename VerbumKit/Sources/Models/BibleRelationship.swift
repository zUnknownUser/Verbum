/// Spec §22.2.
public enum RelationshipType: String, Codable, Sendable, CaseIterable {
    case appearsIn
    case participatesIn
    case occursAt
    case occursDuring
    case references
    case relatedToTheme
    case relatedTo
    case precedes
    case follows
    case fulfills
    case quotes
}

/// Spec §22.2. Directed edge from `sourceId` to `targetId`.
public struct BibleRelationship: Identifiable, Codable, Equatable, Hashable, Sendable {
    public let id: String
    public let sourceId: String
    public let targetId: String
    public let type: RelationshipType
    /// Editorial confidence when known (spec §58). `nil` means unrated.
    public let confidence: Double?
    /// Provenance (spec §33). Every relationship shown to users should carry at least one.
    public let sourceReferenceIds: [String]

    public init(
        id: String,
        sourceId: String,
        targetId: String,
        type: RelationshipType,
        confidence: Double?,
        sourceReferenceIds: [String]
    ) {
        self.id = id
        self.sourceId = sourceId
        self.targetId = targetId
        self.type = type
        self.confidence = confidence
        self.sourceReferenceIds = sourceReferenceIds
    }
}
