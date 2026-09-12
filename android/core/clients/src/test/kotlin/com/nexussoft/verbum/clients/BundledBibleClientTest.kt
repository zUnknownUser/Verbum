package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.BiblePassage
import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BundledBibleClientTest {
    private val client: BibleClient = BundledBibleClient

    @Test
    fun chapterReturnsOnePassagePerVerseInOrder() = runTest {
        val verses = client.chapter("1Sam", 17)
        assertEquals(58, verses.size)
        assertEquals((1..58).toList(), verses.map { it.verseStart })
        assertTrue(verses.all { it.verseStart == it.verseEnd })
        assertTrue(verses.all { it.bookId == "1Sam" && it.chapter == 17 && it.translationId == "web" })
        assertEquals("web:1Sam.17.1", verses.first().id)
    }

    @Test
    fun textIsRealScripture() = runTest {
        val john316 = client.passage(PassageReference("John", 3, 16..16))
        assertEquals("For God so loved the world, that he gave his only born Son, that whoever believes in him should not perish, but have eternal life.", john316.text)
        assertEquals("web:John.3.16", john316.id)
    }

    @Test
    fun passageRangeJoinsVerses() = runTest {
        val passage = client.passage(PassageReference("John", 3, 16..18))
        val verses = client.chapter("John", 3)
        assertEquals(16, passage.verseStart)
        assertEquals(18, passage.verseEnd)
        assertEquals(verses.subList(15, 18).joinToString(" ") { it.text }, passage.text)
        assertEquals("web:John.3.16-18", passage.id)
        assertEquals(PassageReference("John", 3, 16..18), passage.reference)
    }

    @Test
    fun wholeChapterPassageCoversEveryVerse() = runTest {
        val passage = client.passage(PassageReference("Ps", 23))
        assertEquals(1, passage.verseStart)
        assertEquals(6, passage.verseEnd)
        assertEquals("web:Ps.23.1-6", passage.id)
    }

    @Test
    fun theWholeCanonIsAvailable() {
        val chapters = BundledBibleClient.availableChapters
        assertEquals(1189, chapters.size)
        assertEquals(PassageReference("Gen", 1), chapters.first())
        assertEquals(PassageReference("Rev", 22), chapters.last())
        val perBook = chapters.groupingBy { it.bookId }.eachCount()
        for (book in com.nexussoft.verbum.models.BibleBook.canon) assertEquals(book.chapterCount, perBook[book.id], book.id)
    }

    @Test
    fun everyFixtureChapterHasStandardVerseCount() = runTest {
        val expected = mapOf(
            PassageReference("Gen", 1) to 31, PassageReference("1Sam", 16) to 23,
            PassageReference("1Sam", 17) to 58, PassageReference("2Sam", 5) to 25,
            PassageReference("Ps", 23) to 6, PassageReference("Ps", 51) to 19,
            PassageReference("Matt", 6) to 34, PassageReference("John", 3) to 36,
            PassageReference("Rom", 8) to 39,
        )
        for ((reference, count) in expected) {
            assertEquals(count, client.chapter(reference.bookId, reference.chapter).size, reference.formatted)
        }
    }

    // Errors

    @Test
    fun unknownBook() = runTest {
        val e = assertFailsWith<BibleClientException.UnknownBook> { client.chapter("Xyz", 1) }
        assertEquals(BibleClientException.UnknownBook("Xyz"), e)
    }

    @Test
    fun chapterBeyondTheBook() = runTest {
        val reference = PassageReference("John", 22)
        assertEquals(
            BibleClientException.ContentUnavailable(reference),
            assertFailsWith<BibleClientException.ContentUnavailable> { client.chapter("John", 22) },
        )
    }

    @Test
    fun verseBeyondChapter() = runTest {
        val reference = PassageReference("Ps", 23, 5..7)
        assertEquals(
            BibleClientException.VerseOutOfRange(reference, 6),
            assertFailsWith<BibleClientException.VerseOutOfRange> { client.passage(reference) },
        )
    }

    @Test
    fun stubbingASingleEndpointInTests() = runTest {
        // What features will do: swap only the function under test.
        val stub = BiblePassage("stub", "stub", "John", 3, 16, 16, "stub")
        val client = object : BibleClient by BundledBibleClient {
            override suspend fun passage(reference: PassageReference) = stub
        }
        assertEquals(stub, client.passage(PassageReference("John", 3, 16..16)))
        assertEquals(36, client.chapter("John", 3).size)
    }
}
