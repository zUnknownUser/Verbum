package com.nexussoft.verbum.clients

import com.nexussoft.verbum.clients.fixtures.FixtureContextClient
import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class FixtureContextClientTest {
    @Test fun goldenPathIsSourcedAndDeduplicated() = runTest {
        val reference = PassageReference("1Sam", 17)
        val page = assertNotNull(FixtureContextClient.chapter(reference))
        assertTrue(page.isFixture)
        assertTrue(page.entities.any { it.id == "fixture.person.david" })
        assertTrue(page.entities.any { it.id == "fixture.place.elah" })
        assertTrue(page.sources.isNotEmpty())
        assertTrue(page.relatedPassages.isNotEmpty())
        assertFalse(reference in page.relatedPassages)
        assertEquals(page.relatedPassages.distinct(), page.relatedPassages)
    }

    @Test fun unsupportedChapterDoesNotInventContext() = runTest {
        assertNull(FixtureContextClient.chapter(PassageReference("Neh", 9)))
    }
}
