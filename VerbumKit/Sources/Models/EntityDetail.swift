/// Provenance for a contextual claim (spec §33). Every relationship and every
/// detail field shown to users points at one of these.
public struct SourceReference: Identifiable, Codable, Equatable, Hashable, Sendable {
    public let id: String
    public let citation: String
    public let url: String?

    public init(id: String, citation: String, url: String?) {
        self.id = id
        self.citation = citation
        self.url = url
    }
}

/// One entity's neighbourhood (spec §44): the root, the nodes one hop away,
/// and the edges between them. Depth-limited by the client, never the whole graph.
public struct GraphSnapshot: Codable, Equatable, Sendable {
    public let root: BibleEntity
    public let nodes: [BibleEntity]
    public let edges: [BibleRelationship]

    public init(root: BibleEntity, nodes: [BibleEntity], edges: [BibleRelationship]) {
        self.root = root
        self.nodes = nodes
        self.edges = edges
    }

    public func nodes(of type: BibleEntityType) -> [BibleEntity] {
        nodes.filter { $0.type == type }
    }
}

/// What an entity page shows beyond the graph (spec §9). Fields are optional
/// because not every kind has every fact, and absent is better than invented.
public struct EntityDetail: Codable, Equatable, Sendable {
    public let originalTerm: OriginalTermPresentation?
    public let entity: BibleEntity
    /// Other names: `Simon, Cephas`; `Jebus, Salem`.
    public let aliases: [String]
    /// Always hedged: "c. 1010–970 BC (commonly dated)". Never a bare year.
    public let approximateDates: String?
    /// One line: "King of Israel", "Apostle to the Gentiles".
    public let role: String?
    /// Where it is today, when meaningful (places only).
    public let modernGeography: String?
    /// Passages that matter most for this entity, in reading order.
    public let keyPassages: [PassageReference]
    /// Sources backing the summary and the fields above.
    public let sources: [SourceReference]

    public init(
        entity: BibleEntity,
        aliases: [String] = [],
        approximateDates: String? = nil,
        role: String? = nil,
        modernGeography: String? = nil,
        keyPassages: [PassageReference] = [],
        sources: [SourceReference] = [],
        originalTerm: OriginalTermPresentation? = nil
    ) {
        self.originalTerm = originalTerm
        self.entity = entity
        self.aliases = aliases
        self.approximateDates = approximateDates
        self.role = role
        self.modernGeography = modernGeography
        self.keyPassages = keyPassages
        self.sources = sources
    }
}

public struct OriginalTermPresentation: Codable, Equatable, Sendable {
    public let language: String
    public let transliteration: String
    public let strong: String
}
