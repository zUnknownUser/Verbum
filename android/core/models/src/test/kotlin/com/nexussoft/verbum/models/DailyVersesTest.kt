package com.nexussoft.verbum.models

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DailyVersesTest {
    @Test
    fun everyVerseIsASingleVerseInTheCanon() {
        for (reference in DailyVerses.pool) {
            val book = assertNotNull(BibleBook.book(reference.bookId), "${reference.bookId} is not in the canon")
            assertTrue(reference.chapter in 1..book.chapterCount, reference.formatted)
            assertEquals(1, reference.verses?.count(), "${reference.formatted} is not one verse")
        }
        assertEquals(DailyVerses.pool.size, DailyVerses.pool.toSet().size)
    }

    /** The same dates are asserted in the iOS `DailyVersesTests`. */
    @Test
    fun sameVerseOnBothPlatforms() {
        assertEquals(PassageReference("2Tim", 1, 7..7), DailyVerses.verse(LocalDate.of(2026, 9, 12)))
        assertEquals(PassageReference("Ps", 121, 1..1), DailyVerses.verse(LocalDate.of(2026, 9, 13)))
        assertEquals(PassageReference("Mark", 10, 27..27), DailyVerses.verse(LocalDate.of(2027, 1, 1)))
        assertEquals(PassageReference("Eph", 2, 8..8), DailyVerses.verse(-1L))
    }

    @Test
    fun noRepeatWithinACycle() {
        val count = DailyVerses.pool.size
        val start = 205L * count
        val verses = (start until start + count).map { DailyVerses.verse(it) }
        assertEquals(count, verses.toSet().size)
        val next = (start + count until start + 2 * count).map { DailyVerses.verse(it) }
        assertNotEquals(verses, next)
    }
}
