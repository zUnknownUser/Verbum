package com.nexussoft.verbum.common

import com.nexussoft.verbum.common.PassageReferenceParseError as Err
import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.formatted
import com.nexussoft.verbum.models.PassageReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PassageReferenceParserTest {
    private fun parsed(input: String): PassageReference =
        assertNotNull(PassageReferenceParser.parse(input, BookLanguage.ENGLISH).referenceOrNull, "expected '$input' to parse")

    private fun failed(input: String): Err =
        assertNotNull(PassageReferenceParser.parse(input, BookLanguage.ENGLISH).errorOrNull, "expected '$input' to fail")

    // Inputs required by docs/PRODUCT.md §60 Task 3

    @Test
    fun specInputs() {
        assertEquals(PassageReference("John", 3, 16..16), parsed("John 3:16"))
        assertEquals(PassageReference("John", 3), parsed("John 3"))
        assertEquals(PassageReference("John", 3, 16..18), parsed("John 3:16-18"))
        assertEquals(PassageReference("John", 3, 16..16), parsed("Jn 3:16"))
        assertEquals(PassageReference("Rom", 8, 28..28), parsed("Romans 8:28"))
        assertEquals(PassageReference("1Sam", 17), parsed("1 Samuel 17"))
    }

    // Variants people actually type

    @Test
    fun bookSpellings() {
        val cases = mapOf(
            "jn 3:16" to "John", "JOHN 3:16" to "John", "Jn. 3:16" to "John", "John. 3:16" to "John",
            "1Sam 17:1" to "1Sam", "1 Sam 17:1" to "1Sam", "I Samuel 17:1" to "1Sam", "II Sam 5:1" to "2Sam",
            "III John 1:1" to "3John", "Psalm 23:1" to "Ps", "Psalms 23:1" to "Ps", "Ps 23:1" to "Ps",
            "Song of Solomon 2:1" to "Song", "Song of Songs 2:1" to "Song", "Rev 22:1" to "Rev",
            "Isaiah 1:1" to "Isa", "Is 1:1" to "Isa",
        )
        for ((input, bookId) in cases) assertEquals(bookId, parsed(input).bookId, input)
    }

    @Test
    fun whitespaceIsIrrelevant() {
        val expected = PassageReference("John", 3, 16..18)
        assertEquals(expected, parsed("  John   3 : 16 - 18  "))
        assertEquals(expected, parsed("John3:16-18"))
        assertEquals(expected, parsed("\nJohn 3:16-18\n"))
    }

    @Test
    fun dashVariantsSeparateRanges() {
        val expected = PassageReference("John", 3, 16..18)
        assertEquals(expected, parsed("John 3:16–18"))
        assertEquals(expected, parsed("John 3:16—18"))
    }

    @Test
    fun periodSeparatesChapterFromVerse() {
        assertEquals(PassageReference("John", 3, 16..16), parsed("John 3.16"))
    }

    @Test
    fun singleVerseRangeCollapses() {
        assertEquals(16..16, parsed("John 3:16-16").verses)
    }

    @Test
    fun singleChapterBookReadsNumberAsVerse() {
        assertEquals(PassageReference("Jude", 1, 3..3), parsed("Jude 3"))
        assertEquals(PassageReference("Jude", 1), parsed("Jude 1"))
        assertEquals(PassageReference("Jude", 1, 3..3), parsed("Jude 1:3"))
        assertEquals(PassageReference("Phlm", 1, 6..6), parsed("Philemon 6"))
    }

    @Test
    fun boundaryChapters() {
        assertEquals(150, parsed("Psalm 150").chapter)
        assertEquals(1, parsed("Genesis 1").chapter)
    }

    // Errors

    @Test
    fun emptyInput() {
        assertEquals(Err.Empty, failed(""))
        assertEquals(Err.Empty, failed("   "))
    }

    @Test
    fun malformedInputs() {
        for (input in listOf("John", "3:16", "John three", "John 3:", "John 3:16-", "John 3:16-18-20", "3 John 3 3", "David", "why did Job suffer")) {
            assertEquals(Err.Malformed, failed(input), input)
        }
    }

    @Test
    fun unknownBookIsReportedWithWhatWasTyped() {
        assertEquals(Err.UnknownBook("Hezekiah"), failed("Hezekiah 3:16"))
    }

    @Test
    fun chapterOutOfRange() {
        val john = assertNotNull(BibleBook.book("John"))
        assertEquals(Err.ChapterOutOfRange(22, john), failed("John 22:1"))
        assertEquals(Err.ChapterOutOfRange(0, john), failed("John 0:1"))
    }

    @Test
    fun invalidVerseRanges() {
        assertEquals(Err.InvalidVerseRange, failed("John 3:0"))
        assertEquals(Err.InvalidVerseRange, failed("John 3:18-16"))
    }

    // Determinism

    @Test
    fun sameInputAlwaysSameOutput() {
        val results = (0 until 50).map { PassageReferenceParser.parse("Rom 8:28-30", BookLanguage.ENGLISH) }.toSet()
        assertEquals(1, results.size)
    }

    @Test
    fun formattedOutputRoundTrips() {
        for (input in listOf("John 3", "John 3:16", "John 3:16-18", "1 Samuel 17", "Song of Solomon 2:1-4")) {
            val ref = parsed(input)
            assertEquals(ref, parsed(ref.formatted(BookLanguage.ENGLISH)))
        }
    }
}

class PassageReferenceParserPortugueseTest {
    private fun pt(input: String) = assertNotNull(PassageReferenceParser.parse(input, BookLanguage.PORTUGUESE).referenceOrNull, "expected '$input' to parse")

    @Test
    fun portugueseNames() {
        val cases = mapOf(
            "Jo 3:16" to "John", "João 3:16" to "John", "Joao 3:16" to "John", "Jn 1:1" to "Jonah", "Jó 1" to "Job",
            "Gn 1" to "Gen", "Gênesis 1" to "Gen", "Êx 3" to "Exod", "Sl 23" to "Ps", "1 Sm 17" to "1Sam", "1Sm 17" to "1Sam",
            "Ct 2" to "Song", "Cantares 2" to "Song", "Ap 21" to "Rev", "Tg 1" to "Jas", "1 Jo 4" to "1John", "Fm 1" to "Phlm",
        )
        for ((input, id) in cases) assertEquals(id, pt(input).bookId, input)
    }

    @Test
    fun englishStillWorksOnAPortugueseDevice() {
        assertEquals("John", pt("John 3:16").bookId)
        assertEquals("1Sam", pt("1 Samuel 17").bookId)
    }

    @Test
    fun conflictsResolveToTheDeviceLanguage() {
        assertEquals("John", PassageReferenceParser.parse("Jn 1:1", BookLanguage.ENGLISH).referenceOrNull?.bookId)
        assertEquals("Jonah", pt("Jn 1:1").bookId)
    }

    @Test
    fun portugueseRoundTrips() {
        for (input in listOf("João 3", "João 3:16", "1 Samuel 17", "Cântico dos Cânticos 2:1-4", "Jó 42")) {
            val ref = pt(input)
            assertEquals(ref, pt(ref.formatted(BookLanguage.PORTUGUESE)))
        }
    }
}
