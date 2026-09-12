import ComposableArchitecture
import Models

/// The timeline (spec §4.2, §45 `/timeline`): curated periods and events with
/// their dating and how sure it is. Static, hand-curated until Task 11.
@DependencyClient
public struct TimelineClient: Sendable {
    /// Every event, in chronological order (unknown dates last).
    public var events: @Sendable () async throws -> [TimelineEvent]
    /// The events an entity takes part in, chronological.
    public var eventsFor: @Sendable (_ entityID: EntityID) async throws -> [TimelineEvent]
}

extension TimelineClient: DependencyKey {
    public static let liveValue = TimelineClient.fixtures
    public static let previewValue = TimelineClient.fixtures
}

extension DependencyValues {
    public var timelineClient: TimelineClient {
        get { self[TimelineClient.self] }
        set { self[TimelineClient.self] = newValue }
    }
}

extension TimelineClient {
    public static let fixtures = TimelineClient(
        events: { TimelineFixtureData.events },
        eventsFor: { id in TimelineFixtureData.events.filter { $0.entityIds.contains(id) } }
    )
}

// FIXTURE — hand-curated from the spec's own list (§4.2). Dates are the ones
// commonly given in reference works; where scholarship is split the precision
// says `debated` and the span covers both positions rather than picking one.
// Every entry cites the editorial source (§33); real content arrives with Task 11.
enum TimelineFixtureData {
    private static func event(_ id: String, _ title: String, _ start: Int?, _ end: Int?, _ precision: TimelineDatePrecision, _ summary: String, _ entityIds: [String]) -> TimelineEvent {
        TimelineEvent(id: "fixture.timeline.\(id)", title: title, startYear: start, endYear: end, datePrecision: precision, summary: summary, entityIds: entityIds, sourceReferenceIds: [EntityFixtureData.editorialSource.id])
    }

    /// Chronological: by start year, then by end year (longer periods first), unknown last.
    static let events: [TimelineEvent] = [
        event("abraham", "Abraham", -2000, -1800, .approximate, "Abraham's journey from Ur to Canaan opens the story of Israel. The patriarchal period is dated only broadly, from the kind of life the narratives describe.", ["fixture.person.abraham"]),
        event("exodus", "The Exodus", -1446, -1250, .debated, "Israel leaves Egypt under Moses. Two chronologies are held today — a 15th-century date read from 1 Kings 6:1, and a 13th-century date tied to the city of Rameses — and the timeline shows the whole span.", ["fixture.person.moses", "fixture.place.egypt", "fixture.event.exodus"]),
        event("judges", "Period of the Judges", -1200, -1050, .approximate, "Between the settlement of Canaan and the first king, Israel is led by judges — Deborah, Gideon, Samson — in cycles the book of Judges describes.", []),
        event("united-monarchy", "United Monarchy", -1050, -930, .approximate, "Saul, David and Solomon rule one kingdom from Jerusalem. The dates rest on the reigns 1 and 2 Samuel and 1 Kings record, counted back from later fixed points.", ["fixture.person.saul", "fixture.person.david", "fixture.person.solomon", "fixture.place.jerusalem"]),
        event("david", "Reign of David", -1010, -970, .approximate, "David unites the tribes, takes Jerusalem and makes it his capital. Commonly dated c. 1010–970 BC.", ["fixture.person.david", "fixture.place.jerusalem", "fixture.event.david-takes-jerusalem"]),
        event("solomon", "Reign of Solomon", -970, -931, .approximate, "Solomon builds the first temple in Jerusalem. His death is followed by the division of the kingdom.", ["fixture.person.solomon", "fixture.place.jerusalem"]),
        event("divided-kingdom", "Divided Kingdom", -931, -586, .approximate, "Israel in the north and Judah in the south, with their own kings and prophets, until each falls to an empire.", ["fixture.place.jerusalem"]),
        event("assyrian-conquest", "Assyrian conquest of Samaria", -722, nil, .approximate, "The northern kingdom falls to Assyria and its people are deported. Dated 722 or 721 BC from Assyrian records.", []),
        event("babylonian-exile", "Babylonian exile", -586, -538, .approximate, "Jerusalem and its temple are destroyed and Judah's leaders are taken to Babylon. The fall is placed in 587 or 586 BC; the return begins under Cyrus.", ["fixture.place.babylon", "fixture.place.jerusalem"]),
        event("persian-period", "Persian period", -538, -332, .approximate, "Under Persian rule exiles return, the temple is rebuilt (Ezra) and the walls restored (Nehemiah).", ["fixture.place.jerusalem"]),
        event("second-temple", "Second Temple period", -516, 70, .approximate, "From the rebuilt temple to its destruction by Rome in AD 70 — the world of the Gospels and Acts.", ["fixture.place.jerusalem"]),
        event("birth-of-jesus", "Birth of Jesus", -6, -4, .debated, "Placed shortly before the death of Herod the Great (4 BC); the exact year is not known.", ["fixture.person.jesus", "fixture.person.mary", "fixture.place.bethlehem", "fixture.place.nazareth"]),
        event("ministry-of-jesus", "Ministry of Jesus", 27, 30, .approximate, "Jesus's public ministry in Galilee and Judea, about three years by John's count of Passovers.", ["fixture.person.jesus", "fixture.place.galilee", "fixture.place.jerusalem"]),
        event("crucifixion", "Crucifixion", 30, 33, .debated, "The Gospels place it at Passover under Pontius Pilate; AD 30 and AD 33 are the two dates usually argued for.", ["fixture.person.jesus", "fixture.place.jerusalem"]),
        event("early-church", "Early Church", 30, 100, .approximate, "From Pentecost to the end of the apostolic generation, as told in Acts and the letters.", ["fixture.person.peter", "fixture.person.paul", "fixture.person.john", "fixture.place.jerusalem"]),
        event("pauline-missions", "Pauline missions", 46, 57, .approximate, "Paul's journeys through Asia Minor and Greece — Corinth, Ephesus — and the letters written along the way.", ["fixture.person.paul", "fixture.place.corinth", "fixture.place.ephesus", "fixture.place.rome"])
    ].sorted { a, b in
        switch (a.startYear, b.startYear) {
        case (nil, nil): return false
        case (nil, _): return false
        case (_, nil): return true
        case (let x?, let y?):
            if x != y { return x < y }
            return (a.endYear ?? x) > (b.endYear ?? y)
        }
    }
}
