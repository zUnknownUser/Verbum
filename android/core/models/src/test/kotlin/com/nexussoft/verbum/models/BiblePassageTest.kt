package com.nexussoft.verbum.models

import kotlin.test.Test
import kotlin.test.assertEquals

class BiblePassageTest {
    // Fixture: text is placeholder, not Scripture.
    private val passage = BiblePassage(
        id = "fixture-passage",
        translationId = "fixture",
        bookId = "1Sam",
        chapter = 17,
        verseStart = 45,
        verseEnd = 47,
        text = "fixture text",
    )

    @Test
    fun referenceDerivedFromLocation() {
        assertEquals(PassageReference("1Sam", 17, 45..47), passage.reference)
        assertEquals("1 Samuel 17:45-47", passage.reference.formatted(BookLanguage.ENGLISH))
    }

    @Test
    fun copyPreservesEquality() {
        assertEquals(passage, passage.copy())
    }
}
