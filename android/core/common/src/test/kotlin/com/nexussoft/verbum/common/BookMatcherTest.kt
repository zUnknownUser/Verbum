package com.nexussoft.verbum.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookMatcherTest {
    @Test fun portugueseNamesAndUnaccentedReferences() {
        val language = com.nexussoft.verbum.models.BookLanguage.PORTUGUESE
        assertEquals("Ps", BookMatcher.books("salmos", language).first().id)
        assertEquals("Gen", BookMatcher.books("genesis", language).first().id)
        assertEquals(com.nexussoft.verbum.models.PassageReference("John", 3, 16..16), PassageReferenceParser.parse("Joao 3:16", language).referenceOrNull)
        assertEquals("Job", PassageReferenceParser.parse("Jó 3", language).referenceOrNull?.bookId)
        assertEquals("John", PassageReferenceParser.parse("Jo 3", language).referenceOrNull?.bookId)
    }
    private fun ids(query: String) = BookMatcher.books(query).map { it.id }

    @Test
    fun prefixOfName() {
        assertEquals(listOf("Gen"), ids("gen"))
        assertEquals(listOf("John", "1John", "2John", "3John"), ids("john"))
    }

    @Test
    fun prefixOfAnyWord() {
        assertEquals(listOf("1Sam", "2Sam"), ids("sam"))
        assertEquals(listOf("1Kgs", "2Kgs"), ids("kings"))
        assertEquals(listOf("Song"), ids("solomon"))
    }

    @Test
    fun prefixOfAbbreviation() {
        assertEquals(listOf("John", "Jonah"), ids("jn")) // exact abbreviation first, then prefix of "Jnh"
        assertEquals(listOf("Ps"), ids("ps"))
        assertEquals(listOf("1Sam"), ids("1 sam"))
        assertEquals(listOf("1Sam"), ids("1sam"))
    }

    @Test
    fun leadingNumberListsTheNumberedBooks() {
        assertEquals(listOf("1Sam", "1Kgs", "1Chr", "1Cor", "1Thess", "1Tim", "1Pet", "1John"), ids("1"))
        assertEquals(listOf("2Chr", "2Cor"), ids("2 c"))
    }

    @Test
    fun caseAndPunctuationAreIgnored() {
        assertEquals(listOf("John", "Jonah"), ids("JN."))
        assertEquals(listOf("Rom"), ids("  Rom "))
    }

    @Test
    fun emptyAndNonsense() {
        assertTrue(ids("").isEmpty())
        assertTrue(ids("   ").isEmpty())
        assertTrue(ids("xyz").isEmpty())
    }

    @Test
    fun resultsFollowCanonOrder() {
        val orders = BookMatcher.books("j").map { it.order }
        assertEquals(orders.sorted(), orders)
        assertEquals("Josh", ids("j").first())
    }

    @Test
    fun exactNameOutranksPrefixMatches() {
        assertEquals("John", ids("john").first())
        assertEquals(listOf("Ps"), ids("ps"))
        assertEquals(listOf("Job"), ids("job"))
    }
}
