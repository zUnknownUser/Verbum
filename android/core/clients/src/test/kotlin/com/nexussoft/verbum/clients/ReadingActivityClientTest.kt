package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.PassageReference
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.*

class ReadingActivityClientTest {
    @Test fun chapterHistorySurvivesReopeningTheClient() {
        val preferences = InMemoryPreferencesClient()
        val clock = Clock.fixed(Instant.parse("2026-09-16T03:30:00Z"), ZoneId.of("America/Manaus"))
        ReadingActivityClient(preferences, clock).record(PassageReference("John", 1, 14..14))
        val restored = ReadingActivityClient(preferences).load()
        assertEquals(setOf("2026-09-15"), restored.days)
        assertEquals(PassageReference("John", 1), restored.visits.single().reference)
    }
    @Test fun corruptHistoryIsNeverOverwritten() {
        val preferences = InMemoryPreferencesClient()
        preferences.setString(ReadingActivityClient.KEY, "broken-json")
        assertFails { ReadingActivityClient(preferences).record(PassageReference("John", 1)) }
        assertEquals("broken-json", preferences.string(ReadingActivityClient.KEY))
    }
}
