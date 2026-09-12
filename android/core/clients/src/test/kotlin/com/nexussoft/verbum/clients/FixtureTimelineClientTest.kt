package com.nexussoft.verbum.clients

import com.nexussoft.verbum.clients.fixtures.FixtureGraphClient
import com.nexussoft.verbum.clients.fixtures.FixtureTimelineClient
import com.nexussoft.verbum.models.TimelineDatePrecision
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixtureTimelineClientTest {
    @Test fun coversTheSpecsListInOrderWithHedgedDates() = runTest {
        val events = FixtureTimelineClient.events()
        assertEquals(16, events.size)
        assertEquals("Abraham", events.first().title)
        assertEquals("Pauline missions", events.last().title)
        val starts = events.mapNotNull { it.startYear }
        assertEquals(starts.sorted(), starts)
        assertTrue(events.none { it.datePrecision == TimelineDatePrecision.EXACT })
        assertEquals(TimelineDatePrecision.DEBATED, events.first { it.id.endsWith("exodus") }.datePrecision)
        assertEquals(TimelineDatePrecision.DEBATED, events.first { it.id.endsWith("crucifixion") }.datePrecision)
        assertTrue(events.all { it.sourceReferenceIds.isNotEmpty() && !it.summary.isNullOrEmpty() })
        events.flatMap { it.entityIds }.forEach { FixtureGraphClient.entity(it) }
    }

    @Test fun eventsForAnEntityAreItsOwn() = runTest {
        assertEquals(listOf("United Monarchy", "Reign of David"), FixtureTimelineClient.eventsFor("fixture.person.david").map { it.title })
        assertTrue(FixtureTimelineClient.eventsFor("fixture.theme.love").isEmpty())
    }
}
