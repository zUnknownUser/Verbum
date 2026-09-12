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
        sourceReferenceIds: [String] = []
    ) {
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
    public var isPeriod: Bool { startYear != nil && endYear != nil && startYear != endYear }
}
