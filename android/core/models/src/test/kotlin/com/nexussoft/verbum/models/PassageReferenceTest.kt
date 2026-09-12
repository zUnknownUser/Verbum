package com.nexussoft.verbum.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassageReferenceTest {
    @Test
    fun wholeChapter() {
        val ref = PassageReference("John", 3)
        assertTrue(ref.isWholeChapter)
        assertEquals("John 3", ref.formatted(BookLanguage.ENGLISH))
    }

    @Test
    fun singleVerse() {
        val ref = PassageReference("John", 3, 16..16)
        assertFalse(ref.isWholeChapter)
        assertEquals("John 3:16", ref.formatted(BookLanguage.ENGLISH))
    }

    @Test
    fun verseRange() {
        assertEquals("John 3:16-18", PassageReference("John", 3, 16..18).formatted(BookLanguage.ENGLISH))
    }

    @Test
    fun numberedBookName() {
        assertEquals("1 Samuel 17", PassageReference("1Sam", 17).formatted(BookLanguage.ENGLISH))
    }

    @Test
    fun unknownBookFallsBackToId() {
        assertEquals("Xyz 1:2-3", PassageReference("Xyz", 1, 2..3).formatted(BookLanguage.ENGLISH))
    }

    @Test
    fun valueEquality() {
        assertEquals(PassageReference("Rom", 8, 28..28), PassageReference("Rom", 8, 28..28))
    }
}
