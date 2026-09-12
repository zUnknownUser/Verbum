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

    @Test
    fun selectionCitations() {
        assertNull(SelectionFormatter.format("John", 3, emptySet()))
        assertEquals("John 3:16", SelectionFormatter.format("John", 3, setOf(16)))
        assertEquals("John 3:16-18", SelectionFormatter.format("John", 3, setOf(18, 16, 17)))
        assertEquals("John 3:16, 18", SelectionFormatter.format("John", 3, setOf(16, 18)))
        assertEquals("1 Samuel 17:1-2, 4-6, 9", SelectionFormatter.format("1Sam", 17, setOf(1, 2, 4, 5, 6, 9)))
    }
}
