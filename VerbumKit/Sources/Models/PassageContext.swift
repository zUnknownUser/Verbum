/// Chapter-level context. Missing coverage is represented by a nil response,
/// not by a generated explanation. References remain structured for navigation.
public struct PassageContext: Equatable, Sendable, Codable {
    public let reference: PassageReference
    public let entities: [BibleEntity]
    public let relatedPassages: [PassageReference]
    public let sources: [SourceReference]
    public let isFixture: Bool

    public init(reference: PassageReference, entities: [BibleEntity], relatedPassages: [PassageReference], sources: [SourceReference], isFixture: Bool) {
        self.reference = reference
        self.entities = entities
        self.relatedPassages = relatedPassages
        self.sources = sources
        self.isFixture = isFixture
    }
}
