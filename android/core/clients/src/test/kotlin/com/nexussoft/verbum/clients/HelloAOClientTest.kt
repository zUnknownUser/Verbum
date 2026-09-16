package com.nexussoft.verbum.clients

import com.nexussoft.verbum.clients.helloao.ChapterCache
import com.nexussoft.verbum.clients.helloao.HelloAOBibleClient
import com.nexussoft.verbum.clients.helloao.HelloAOBooks
import com.nexussoft.verbum.clients.helloao.HelloAOChapter
import com.nexussoft.verbum.clients.helloao.HelloAOTranslation
import com.nexussoft.verbum.clients.helloao.LiveBibleClient
import com.nexussoft.verbum.clients.helloao.Transport
import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Samples are real responses saved from bible.helloao.org; tests never touch the network.
private fun sample(name: String): String =
    HelloAOClientTest::class.java.classLoader.getResourceAsStream("samples/$name.json")!!.bufferedReader().readText()

class HelloAOClientTest {
    @Test fun rejectsWrongTranslationBeforeCaching() = runTest {
        val cache = ChapterCache(null)
        val client = HelloAOBibleClient("por_blj", Transport { sample("BSB_PSA_23") }, cache)
        assertFailsWith<BibleClientException.ContentUnavailable> { client.chapter("Ps",23) }
        kotlin.test.assertNull(cache.read("por_blj", PassageReference("Ps",23)))
    }

    @Test
    fun everyCanonBookHasAUsfmCodeAndBack() {
        for (book in BibleBook.canon) {
            val usfm = HelloAOBooks.usfmByOsis[book.id]
            assertTrue(usfm != null, book.id)
            assertEquals(book.id, HelloAOBooks.osisByUsfm[usfm])
        }
        assertEquals("JHN", HelloAOBooks.usfmByOsis["John"])
        assertEquals("BSB", HelloAOTranslation.id(BookLanguage.ENGLISH))
        assertEquals("por_blj", HelloAOTranslation.id(BookLanguage.PORTUGUESE))
    }

    @Test
    fun poetryKeepsItsLines() {
        val verses = HelloAOChapter.passages(sample("BSB_PSA_23"), "Ps")
        assertEquals(6, verses.size)
        assertEquals("BSB:Ps.23.1", verses[0].id)
        assertEquals("The LORD is my shepherd;\nI shall not want.", verses[0].text)
        assertEquals("He makes me lie down in green pastures;\nHe leads me beside quiet waters.", verses[1].text)
    }

    @Test
    fun proseInPortuguese() {
        val verses = HelloAOChapter.passages(sample("por_blj_JHN_3"), "John")
        assertEquals(36, verses.size)
        assertTrue(verses[15].text.startsWith("Porque Deus amou ao mundo de tal maneira"))
        assertEquals("por_blj", verses[15].translationId)
    }

    @Test
    fun fetchesTheRightUrlAndCaches() = runTest {
        val requested = mutableListOf<String>()
        val body = sample("BSB_PSA_23")
        val client = HelloAOBibleClient("BSB", Transport { requested += it; body }, ChapterCache(null))
        val first = client.chapter("Ps", 23)
        val second = client.chapter("Ps", 23)
        assertEquals(first, second)
        assertEquals(listOf("https://bible.helloao.org/api/BSB/PSA/23.json"), requested)
    }

    @Test
    fun networkFailureWithoutCacheIsOffline() = runTest {
        val client = HelloAOBibleClient("BSB", Transport { throw java.io.IOException("down") }, ChapterCache(null))
        assertFailsWith<BibleClientException.NetworkUnavailable> { client.chapter("John", 3) }
    }

    @Test
    fun cachedChapterReadsWhileOffline() = runTest {
        val cache = ChapterCache(null)
        HelloAOBibleClient("BSB", Transport { sample("BSB_PSA_23") }, cache).chapter("Ps", 23)
        val offline = HelloAOBibleClient("BSB", Transport { throw java.io.IOException("down") }, cache)
        assertEquals(6, offline.chapter("Ps", 23).size)
    }

    @Test
    fun unknownBookAndChapterOutOfRangeNeverHitTheNetwork() = runTest {
        val client = HelloAOBibleClient("BSB", Transport { throw AssertionError("network used") }, ChapterCache(null))
        assertFailsWith<BibleClientException.UnknownBook> { client.chapter("Xyz", 1) }
        assertFailsWith<BibleClientException.ContentUnavailable> { client.chapter("John", 22) }
    }

    @Test
    fun liveFallsBackToBundledWebInEnglishOnly() = runTest {
        val down = Transport { throw java.io.IOException("down") }
        val english = LiveBibleClient(BookLanguage.ENGLISH, HelloAOBibleClient("BSB", down, ChapterCache(null)))
        val verses = english.chapter("John", 3)
        assertEquals(36, verses.size)
        assertEquals("web", verses[0].translationId)
        val portuguese = LiveBibleClient(BookLanguage.PORTUGUESE, HelloAOBibleClient("por_blj", down, ChapterCache(null)))
        assertFailsWith<BibleClientException.NetworkUnavailable> { portuguese.chapter("John", 3) }
    }

    @Test
    fun passageJoinsARange() = runTest {
        val client = HelloAOBibleClient("BSB", Transport { sample("BSB_PSA_23") }, ChapterCache(null))
        val passage = client.passage(PassageReference("Ps", 23, 1..2))
        assertEquals("BSB:Ps.23.1-2", passage.id)
        assertTrue(passage.text.contains("quiet waters."))
    }
}
