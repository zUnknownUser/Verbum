package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GuidedExplorationClientTest {
    @Test fun eachFeelingHasDeterministicSourcedReadings() = runTest {
        for (feeling in ArrivalFeeling.entries) {
            val request = ExplorationRequest(feeling, BookLanguage.PORTUGUESE)
            val plan = EditorialExplorationClient.explore(request)
            assertEquals(plan, EditorialExplorationClient.explore(request))
            assertTrue(plan.isEditorialPreview)
            assertEquals(2, plan.passages.size)
            plan.passages.forEach {
                assertTrue(it.chapter in 1..assertNotNull(BibleBook.book(it.bookId)).chapterCount)
                assertNotNull(it.verses)
            }
        }
    }
    @Test fun memoryIsBoundedAndReadsRefreshRecency() {
        val cache = com.nexussoft.verbum.clients.helloao.ChapterCache(null)
        (1..24).forEach { cache.write("test", PassageReference("Ps", it), emptyList()) }
        cache.read("test", PassageReference("Ps", 1))
        cache.write("test", PassageReference("Ps", 25), emptyList())
        assertNotNull(cache.read("test", PassageReference("Ps", 1)))
        assertNull(cache.read("test", PassageReference("Ps", 2)))
        assertNotNull(cache.read("test", PassageReference("Ps", 25)))
    }
}
