package com.nexussoft.verbum.clients.fixtures

import com.nexussoft.verbum.clients.TimelineClient
import com.nexussoft.verbum.models.EntityId
import com.nexussoft.verbum.models.TimelineDatePrecision
import com.nexussoft.verbum.models.TimelineEvent

/**
 * FIXTURE — hand-curated from the spec's own list (§4.2). Dates are the ones commonly given in
 * reference works; where scholarship is split the precision says DEBATED and the span covers both
 * positions rather than picking one. Every entry cites the editorial source (§33). Same table as iOS.
 */
object FixtureTimelineClient : TimelineClient {
    private fun event(id: String, title: String, start: Int?, end: Int?, precision: TimelineDatePrecision, summary: String, entityIds: List<String>) =
        TimelineEvent("fixture.timeline.$id", title, start, end, precision, summary, entityIds, listOf(EntityFixtureData.editorialSource.id))

    /** Chronological: by start year, then longer periods first, unknown last. */
    val events: List<TimelineEvent> = listOf(
        event("abraham", "Abraham", -2000, -1800, TimelineDatePrecision.APPROXIMATE, "Abraham's journey from Ur to Canaan opens the story of Israel. The patriarchal period is dated only broadly, from the kind of life the narratives describe.", listOf("fixture.person.abraham")),
        event("exodus", "The Exodus", -1446, -1250, TimelineDatePrecision.DEBATED, "Israel leaves Egypt under Moses. Two chronologies are held today — a 15th-century date read from 1 Kings 6:1, and a 13th-century date tied to the city of Rameses — and the timeline shows the whole span.", listOf("fixture.person.moses", "fixture.place.egypt", "fixture.event.exodus")),
        event("judges", "Period of the Judges", -1200, -1050, TimelineDatePrecision.APPROXIMATE, "Between the settlement of Canaan and the first king, Israel is led by judges — Deborah, Gideon, Samson — in cycles the book of Judges describes.", listOf()),
        event("united-monarchy", "United Monarchy", -1050, -930, TimelineDatePrecision.APPROXIMATE, "Saul, David and Solomon rule one kingdom from Jerusalem. The dates rest on the reigns 1 and 2 Samuel and 1 Kings record, counted back from later fixed points.", listOf("fixture.person.saul", "fixture.person.david", "fixture.person.solomon", "fixture.place.jerusalem")),
        event("david", "Reign of David", -1010, -970, TimelineDatePrecision.APPROXIMATE, "David unites the tribes, takes Jerusalem and makes it his capital. Commonly dated c. 1010–970 BC.", listOf("fixture.person.david", "fixture.place.jerusalem", "fixture.event.david-takes-jerusalem")),
        event("solomon", "Reign of Solomon", -970, -931, TimelineDatePrecision.APPROXIMATE, "Solomon builds the first temple in Jerusalem. His death is followed by the division of the kingdom.", listOf("fixture.person.solomon", "fixture.place.jerusalem")),
        event("divided-kingdom", "Divided Kingdom", -931, -586, TimelineDatePrecision.APPROXIMATE, "Israel in the north and Judah in the south, with their own kings and prophets, until each falls to an empire.", listOf("fixture.place.jerusalem")),
        event("assyrian-conquest", "Assyrian conquest of Samaria", -722, null, TimelineDatePrecision.APPROXIMATE, "The northern kingdom falls to Assyria and its people are deported. Dated 722 or 721 BC from Assyrian records.", listOf()),
        event("babylonian-exile", "Babylonian exile", -586, -538, TimelineDatePrecision.APPROXIMATE, "Jerusalem and its temple are destroyed and Judah's leaders are taken to Babylon. The fall is placed in 587 or 586 BC; the return begins under Cyrus.", listOf("fixture.place.babylon", "fixture.place.jerusalem")),
        event("persian-period", "Persian period", -538, -332, TimelineDatePrecision.APPROXIMATE, "Under Persian rule exiles return, the temple is rebuilt (Ezra) and the walls restored (Nehemiah).", listOf("fixture.place.jerusalem")),
        event("second-temple", "Second Temple period", -516, 70, TimelineDatePrecision.APPROXIMATE, "From the rebuilt temple to its destruction by Rome in AD 70 — the world of the Gospels and Acts.", listOf("fixture.place.jerusalem")),
        event("birth-of-jesus", "Birth of Jesus", -6, -4, TimelineDatePrecision.DEBATED, "Placed shortly before the death of Herod the Great (4 BC); the exact year is not known.", listOf("fixture.person.jesus", "fixture.person.mary", "fixture.place.bethlehem", "fixture.place.nazareth")),
        event("ministry-of-jesus", "Ministry of Jesus", 27, 30, TimelineDatePrecision.APPROXIMATE, "Jesus's public ministry in Galilee and Judea, about three years by John's count of Passovers.", listOf("fixture.person.jesus", "fixture.place.galilee", "fixture.place.jerusalem")),
        event("crucifixion", "Crucifixion", 30, 33, TimelineDatePrecision.DEBATED, "The Gospels place it at Passover under Pontius Pilate; AD 30 and AD 33 are the two dates usually argued for.", listOf("fixture.person.jesus", "fixture.place.jerusalem")),
        event("early-church", "Early Church", 30, 100, TimelineDatePrecision.APPROXIMATE, "From Pentecost to the end of the apostolic generation, as told in Acts and the letters.", listOf("fixture.person.peter", "fixture.person.paul", "fixture.person.john", "fixture.place.jerusalem")),
        event("pauline-missions", "Pauline missions", 46, 57, TimelineDatePrecision.APPROXIMATE, "Paul's journeys through Asia Minor and Greece — Corinth, Ephesus — and the letters written along the way.", listOf("fixture.person.paul", "fixture.place.corinth", "fixture.place.ephesus", "fixture.place.rome")),
    ).sortedWith(compareBy<TimelineEvent> { it.startYear == null }.thenBy { it.startYear ?: 0 }.thenByDescending { it.endYear ?: it.startYear ?: 0 })

    override suspend fun events(): List<TimelineEvent> = events
    override suspend fun eventsFor(entityId: EntityId): List<TimelineEvent> = events.filter { entityId in it.entityIds }
}
