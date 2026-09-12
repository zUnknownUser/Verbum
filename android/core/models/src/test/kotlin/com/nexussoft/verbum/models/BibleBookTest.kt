package com.nexussoft.verbum.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BibleBookTest {
    @Test
    fun canonHasSixtySixBooksInOrder() {
        assertEquals(66, BibleBook.canon.size)
        assertEquals((1..66).toList(), BibleBook.canon.map { it.order })
        assertEquals("Gen", BibleBook.canon.first().id)
        assertEquals("Rev", BibleBook.canon.last().id)
    }

    @Test
    fun testamentsSplitThirtyNineAndTwentySeven() {
        val old = BibleBook.canon.filter { it.testament == Testament.OLD }
        val new = BibleBook.canon.filter { it.testament == Testament.NEW }
        assertEquals(39, old.size)
        assertEquals(27, new.size)
        assertEquals("Mal", old.last().id)
        assertEquals("Matt", new.first().id)
    }

    @Test
    fun chapterCountsMatchStandardVersification() {
        assertEquals(1189, BibleBook.canon.sumOf { it.chapterCount })
        assertEquals(150, BibleBook.book("Ps")?.chapterCount)
        assertEquals(1, BibleBook.book("Obad")?.chapterCount)
        assertEquals(31, BibleBook.book("1Sam")?.chapterCount)
        assertTrue(BibleBook.canon.all { it.chapterCount >= 1 })
    }

    @Test
    fun idsAndNamesAreUnique() {
        assertEquals(66, BibleBook.canon.map { it.id }.toSet().size)
        assertEquals(66, BibleBook.canon.map { it.name }.toSet().size)
    }

    @Test
    fun abbreviationsNeverCollideAcrossBooks() {
        val seen = mutableMapOf<String, BookId>()
        for (book in BibleBook.canon) {
            for (abbreviation in book.abbreviations.map { it.lowercase() }) {
                assertNull(seen[abbreviation], "$abbreviation used by ${seen[abbreviation]} and ${book.id}")
                seen[abbreviation] = book.id
            }
        }
    }

    @Test
    fun lookupById() {
        assertEquals("John", BibleBook.book("John")?.name)
        assertEquals("1 Samuel", BibleBook.book("1Sam")?.name)
        assertNull(BibleBook.book("Nope"))
    }
}
