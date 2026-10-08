/// Spec §22.5. How sure the dating is; the timeline shows this, never a bare year.
public enum TimelineDatePrecision: String, Codable, Sendable, CaseIterable {
    case exact
    case approximate
    case debated
    case unknown
}

/// Spec §22.5. A period or event on the timeline. Years are astronomical-style
/// integers: negative for BC (`-1010` = 1010 BC), positive for AD; `nil` when
/// unknown. `endYear == nil` with a `startYear` means a point in time.
/// `sourceReferenceIds` is added to the spec's shape so dating claims stay
/// traceable (§33), like relationships.
public struct TimelineEvent: Identifiable, Codable, Equatable, Hashable, Sendable {
    public var entityNames: [String: String]? = nil
    public let discovery: TimelineDiscovery?
    public let id: String
    public let title: String
    public let startYear: Int?
    public let endYear: Int?
    public let datePrecision: TimelineDatePrecision
    public let summary: String?
    /// Graph entities this event is about (people, places, events, themes).
    public let entityIds: [String]
    public let sourceReferenceIds: [String]

    public init(
        id: String,
        title: String,
        startYear: Int?,
        endYear: Int?,
        datePrecision: TimelineDatePrecision,
        summary: String?,
        entityIds: [String],
        sourceReferenceIds: [String] = [],
        discovery: TimelineDiscovery? = nil
    ) {
        self.discovery = discovery
        self.id = id
        self.title = title
        self.startYear = startYear
        self.endYear = endYear
        self.datePrecision = datePrecision
        self.summary = summary
        self.entityIds = entityIds
        self.sourceReferenceIds = sourceReferenceIds
    }

    /// A span rather than a moment.
    public var isPeriod: Bool { discovery.map { $0.kind == "period" } ?? (datePrecision != .debated && startYear != nil && endYear != nil && startYear != endYear) }
}

/// Localized editorial study metadata, independent from calendar date estimates.
public struct TimelineDiscovery: Codable, Equatable, Hashable, Sendable {
    public let eraId: String
    public let eraTitle: String
    public let eraSummary: String
    public let kind: String
    public let context: String
    public let keyPassages: [PassageReference]

    public init(eraId: String, eraTitle: String, eraSummary: String, kind: String, context: String, keyPassages: [PassageReference]) {
        self.eraId = eraId; self.eraTitle = eraTitle; self.eraSummary = eraSummary
        self.kind = kind; self.context = context; self.keyPassages = keyPassages
    }
}
