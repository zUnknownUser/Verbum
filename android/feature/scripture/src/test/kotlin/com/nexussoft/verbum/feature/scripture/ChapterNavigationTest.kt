package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.models.PassageReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChapterNavigationTest {
    @Test
    fun withinBook() {
        assertEquals(PassageReference("John", 4), ChapterNavigation.next(PassageReference("John", 3)))
        assertEquals(PassageReference("John", 2), ChapterNavigation.previous(PassageReference("John", 3)))
    }

    @Test
    fun acrossBooks() {
        assertEquals(PassageReference("Matt", 1), ChapterNavigation.next(PassageReference("Mal", 4)))
        assertEquals(PassageReference("Mal", 4), ChapterNavigation.previous(PassageReference("Matt", 1)))
        assertEquals(PassageReference("Jonah", 1), ChapterNavigation.next(PassageReference("Obad", 1)))
    }

    @Test
    fun canonEdges() {
        assertNull(ChapterNavigation.previous(PassageReference("Gen", 1)))
        assertNull(ChapterNavigation.next(PassageReference("Rev", 22)))
        assertNull(ChapterNavigation.next(PassageReference("Xyz", 1)))
    }
}
