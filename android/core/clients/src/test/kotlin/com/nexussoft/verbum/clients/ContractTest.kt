package com.nexussoft.verbum.clients

import com.nexussoft.verbum.clients.api.HttpRequest
import com.nexussoft.verbum.clients.api.HttpResponse
import com.nexussoft.verbum.clients.api.HttpTransport
import com.nexussoft.verbum.clients.api.LiveContextClient
import com.nexussoft.verbum.clients.api.LiveGraphClient
import com.nexussoft.verbum.clients.api.LiveSearchClient
import com.nexussoft.verbum.clients.api.LiveTimelineClient
import com.nexussoft.verbum.clients.api.VerbumApi
import com.nexussoft.verbum.clients.fixtures.FixtureContextClient
import com.nexussoft.verbum.clients.fixtures.FixtureGraphClient
import com.nexussoft.verbum.clients.fixtures.FixtureSearchClient
import com.nexussoft.verbum.clients.fixtures.FixtureTimelineClient
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.DailyVerses
import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The apps' side of the contract (api/README.md): every file in `api/examples` — the responses the
 * backend proves it serves byte-for-byte — decodes through the live clients into exactly what the
 * fixture clients answer. A change to the JSON breaks here before it breaks a user. Twin of iOS
 * `ContractTests`.
 */
class ContractTest {
    companion object {
        /** Gradle runs a module's tests from the module directory: android/core/clients. */
        private val examples = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "api/examples") }.first { it.isDirectory }
    }

    /** Serves one example file for one exact URL; anything else is a test failure. */
    private fun api(file: String, url: String) = VerbumApi("http://contract.test", HttpTransport { request: HttpRequest ->
        assertEquals("http://contract.test$url", request.url)
        HttpResponse(200, File(examples, file).readText())
    })

    @Test
    fun entityDetail() = runTest {
        val live = LiveGraphClient(api("entities/david.json", "/v1/entities/fixture.person.david?lang=${BookLanguage.current.tag}"))
        assertEquals(FixtureGraphClient.detail("fixture.person.david"), live.detail("fixture.person.david"))
        assertEquals(FixtureGraphClient.entity("fixture.person.david"), live.entity("fixture.person.david"))
    }

    @Test
    fun exodusProfilesUseExistingDetailContract() = runTest {
        for (language in listOf(BookLanguage.ENGLISH, BookLanguage.PORTUGUESE)) {
            val suffix = language.tag
            for ((slug, id, en, pt) in listOf(
                listOf("aaron", "step.person.H0175", "Aaron", "Arão"),
                listOf("nebo", "step.place.H5015H", "Mount Nebo", "Monte Nebo"),
            )) {
                val detail = api("entities/$slug.$suffix.json", "/v1/entities/$id?lang=$suffix")
                    .entityDetail(id, language)
                assertEquals(id, detail.entity.id)
                assertEquals(if (language == BookLanguage.PORTUGUESE) pt else en, detail.entity.name)
                assertTrue(detail.entity.summary?.contains("\n\n") == true)
                assertNotNull(detail.role)
                assertTrue(detail.approximateDates == null && detail.modernGeography == null)
                assertEquals(if (slug == "aaron") PassageReference("Exod", 7, 1..13)
                    else PassageReference("Deut", 34, 1..4), detail.keyPassages.first())
                assertTrue(detail.sources.any { it.id == "editorial.profiles.exodus.2026-10" })
                assertTrue(detail.sources.filter { it.id.startsWith("study.scripture.") }
                    .all { it.webUrl != null })
                assertTrue(detail.eventSequence == null && detail.originalTerm == null)
            }
        }
    }

    @Test
    fun entityList() = runTest {
        val live = LiveGraphClient(api("entities/people.json", "/v1/entities?type=person&lang=en")) { BookLanguage.ENGLISH }
        val expected = FixtureGraphClient.entities(BibleEntityType.PERSON)
        assertEquals(expected, live.entities(BibleEntityType.PERSON))
        assertTrue(expected.isNotEmpty())
    }

    /**
     * The server orders neighbours by confidence then name and returns every edge among
     * `root + nodes` (§44); the fixture keeps insertion order and only the root's edges.
     * Same neighbourhood, richer edges.
     */
    @Test
    fun graph() = runTest {
        val live = LiveGraphClient(api("graph/david.json", "/v1/entities/fixture.person.david/graph?limit=24&lang=${BookLanguage.current.tag}"))
        val got = live.neighbors("fixture.person.david", 24)
        val expected = FixtureGraphClient.neighbors("fixture.person.david", 24)
        assertEquals(expected.root, got.root)
        assertEquals(expected.nodes.toSet(), got.nodes.toSet())
        assertTrue(got.edges.containsAll(expected.edges))
        val visible = got.nodes.map { it.id }.toSet() + got.root.id
        assertTrue(got.edges.all { it.sourceId in visible && it.targetId in visible })
        assertTrue(got.edges.all { it.sourceReferenceIds.isNotEmpty() }) // §33
    }

    @Test
    fun context() = runTest {
        val live = LiveContextClient(api("context/1Sam.17.json", "/v1/passages/1Sam.17/context?lang=${BookLanguage.current.tag}&verse=45"))
        val reference = PassageReference("1Sam", 17, 45..47)
        val got = assertNotNull(live.chapter(reference))
        val expected = assertNotNull(FixtureContextClient.chapter(reference))
        assertEquals(expected.reference, got.reference)
        assertEquals(expected.entities, got.entities)
        assertEquals(expected.relatedPassages, got.relatedPassages)
        assertEquals(expected.sources, got.sources)
        assertTrue(got.study == null, "older API responses remain compatible")
        assertFalse(got.isFixture, "the server's content is not labelled as a fixture")
        assertTrue(expected.isFixture)
    }

    @Test
    fun chapterStudyDecodesWithoutEntitiesAndPreservesEvidence() = runTest {
        for (language in listOf(BookLanguage.ENGLISH, BookLanguage.PORTUGUESE)) {
            val suffix = language.tag
            val page = assertNotNull(api("context/Exod.2.$suffix.json", "/v1/passages/Exod.2/context?lang=$suffix")
                .context(PassageReference("Exod", 2), language))
            val study = assertNotNull(page.study)
            assertTrue(page.entities.isEmpty())
            assertEquals(if (language == BookLanguage.PORTUGUESE) "Moisés: resgate, fuga e aliança lembrada" else "Moses: rescue, flight and a remembered covenant", study.title)
            assertEquals(listOf(PassageReference("Exod", 1, 22..22), PassageReference("Exod", 2, 1..25)), study.summary.references)
            assertEquals(listOf(1..10, 11..22, 23..25), study.outline.map { it.reference.verses })
            assertEquals(PassageReference("Gen", 15, 13..16), study.connections.first().reference)
            assertEquals(page.sources.map { it.id }.toSet(), study.sourceReferenceIds.toSet())
            assertFalse(page.isFixture)
        }
    }

    @Test
    fun timeline() = runTest {
        val live = LiveTimelineClient(api("timeline/all.json", "/v1/timeline?lang=${BookLanguage.current.tag}"))
        val events = live.events()
        assertEquals(FixtureTimelineClient.events(), events.map { it.copy(entityNames = null) })
        assertEquals("Abraham", events.first().entityNames?.get("fixture.person.abraham"))
    }

    @Test
    fun search() = runTest {
        val live = LiveSearchClient(api("search/david.json", "/v1/search?q=David&lang=en")) { BookLanguage.ENGLISH }
        assertEquals(FixtureSearchClient { BookLanguage.ENGLISH }.search("David"), live.search("David"))
    }

    @Test
    fun dailyVerse() = runTest {
        val week = api("daily-verse/week.json", "/v1/daily-verse?from=2026-09-13&days=7").dailyVerses("2026-09-13", 7)
        assertEquals(7, week.size)
        week.forEachIndexed { offset, verse ->
            assertEquals(DailyVerses.verse(java.time.LocalDate.of(2026, 9, 13 + offset)), verse.reference, verse.date)
        }
    }
}
