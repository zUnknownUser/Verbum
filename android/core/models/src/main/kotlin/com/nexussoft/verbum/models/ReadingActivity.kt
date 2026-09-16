package com.nexussoft.verbum.models

import java.time.Instant
import java.time.ZoneId

/** Device-local chapter visits, not a count of completed chapters. */
data class ReadingActivity(val visits: List<Visit> = emptyList(), val days: Set<String> = emptySet()) {
    data class Visit(val reference: PassageReference, val lastOpened: Long)
    fun record(reference: PassageReference, at: Instant, zone: ZoneId): ReadingActivity {
        val book = BibleBook.book(reference.bookId) ?: return this
        if (reference.chapter !in 1..book.chapterCount) return this
        val chapter = PassageReference(reference.bookId, reference.chapter)
        return copy(
            visits = listOf(Visit(chapter, at.toEpochMilli())) + visits.filter { it.reference != chapter },
            days = days + at.atZone(zone).toLocalDate().toString(),
        )
    }
}
