package com.nexussoft.verbum.models

import java.time.Instant
import java.time.ZoneId
import kotlin.test.*

class ReadingActivityTest {
    @Test fun visitsDeduplicateChaptersAndLocalDays() {
        val date = Instant.parse("2026-09-16T03:30:00Z")
        val zone = ZoneId.of("America/Manaus")
        val activity = ReadingActivity()
            .record(PassageReference("John", 1, 14..14), date, zone)
            .record(PassageReference("John", 2), date, zone)
            .record(PassageReference("John", 1), date.plusSeconds(3600), zone)
        assertEquals(setOf("2026-09-15", "2026-09-16"), activity.days)
        assertEquals(listOf(PassageReference("John", 1), PassageReference("John", 2)), activity.visits.map { it.reference })
    }
    @Test fun invalidChaptersAreNotCounted() {
        val activity = ReadingActivity().record(PassageReference("John", 99), Instant.now(), ZoneId.of("UTC"))
            .record(PassageReference("missing", 1), Instant.now(), ZoneId.of("UTC"))
        assertTrue(activity.days.isEmpty()); assertTrue(activity.visits.isEmpty())
    }
}
