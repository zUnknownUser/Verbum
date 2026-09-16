package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.ReadingActivity
import java.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ReadingActivityClient(private val preferences: PreferencesClient, private val clock: Clock = Clock.systemDefaultZone()) {
    @Serializable private data class Visit(val book: String, val chapter: Int, val lastOpened: Long)
    @Serializable private data class Snapshot(val visits: List<Visit> = emptyList(), val days: Set<String> = emptySet())
    companion object { private val lock = Any(); const val KEY = "readingActivity" }
    fun load(): ReadingActivity = synchronized(lock) {
        val raw = preferences.string(KEY) ?: return@synchronized ReadingActivity()
        val snapshot = Json.decodeFromString<Snapshot>(raw)
        ReadingActivity(snapshot.visits.map { ReadingActivity.Visit(PassageReference(it.book, it.chapter), it.lastOpened) }, snapshot.days)
    }
    fun record(reference: PassageReference) = synchronized(lock) {
        val updated = load().record(reference, clock.instant(), clock.zone)
        preferences.setString(KEY, Json.encodeToString(Snapshot(updated.visits.map {
            Visit(it.reference.bookId, it.reference.chapter, it.lastOpened)
        }, updated.days)))
    }
}
