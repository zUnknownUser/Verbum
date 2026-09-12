package com.nexussoft.verbum.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleBookDivisionTest {
    @Test
    fun everyBookHasExactlyOneDivisionAndCountsAddUp() {
        val counts = BibleBook.canon.groupingBy { it.division }.eachCount()
        assertEquals(5, counts[Division.LAW])
        assertEquals(12, counts[Division.HISTORY])
        assertEquals(5, counts[Division.POETRY])
        assertEquals(17, counts[Division.PROPHETS])
        assertEquals(5, counts[Division.GOSPELS_AND_ACTS])
        assertEquals(13, counts[Division.LETTERS_OF_PAUL])
        assertEquals(8, counts[Division.GENERAL_LETTERS])
        assertEquals(1, counts[Division.REVELATION])
        assertEquals(66, counts.values.sum())
    }

    @Test
    fun boundariesAreRight() {
        val expected = mapOf(
            "Deut" to Division.LAW, "Josh" to Division.HISTORY, "Esth" to Division.HISTORY, "Job" to Division.POETRY,
            "Song" to Division.POETRY, "Isa" to Division.PROPHETS, "Mal" to Division.PROPHETS, "Matt" to Division.GOSPELS_AND_ACTS,
            "Acts" to Division.GOSPELS_AND_ACTS, "Rom" to Division.LETTERS_OF_PAUL, "Phlm" to Division.LETTERS_OF_PAUL,
            "Heb" to Division.GENERAL_LETTERS, "Jude" to Division.GENERAL_LETTERS, "Rev" to Division.REVELATION,
        )
        for ((id, division) in expected) assertEquals(division, BibleBook.book(id)?.division, id)
    }

    @Test
    fun divisionsFollowCanonOrderAndTestament() {
        val firstOrders = Division.entries.map { it.books.first().order }
        assertEquals(firstOrders.sorted(), firstOrders)
        for (division in Division.entries) assertTrue(division.books.all { it.testament == division.testament })
    }
}
