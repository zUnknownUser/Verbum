package com.nexussoft.verbum.clients

import com.nexussoft.verbum.clients.api.HttpResponse
import com.nexussoft.verbum.clients.api.HttpTransport
import com.nexussoft.verbum.clients.api.VerbumApi
import com.nexussoft.verbum.clients.api.WireEntityDetail
import com.nexussoft.verbum.models.BookLanguage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeStudyContractTest {
    private val examples = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "api/examples/entities") }.first { it.isDirectory }
    private fun example(language: String) = File(examples, "faith-study.$language.json").readText()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun bilingualStudiesDecodeThroughLiveApiWithReferencesAndSourcesIntact() = runTest {
        val details = listOf(BookLanguage.ENGLISH, BookLanguage.PORTUGUESE).map { language ->
            VerbumApi("http://contract.test", HttpTransport { request ->
                assertEquals("http://contract.test/v1/entities/fixture.theme.faith?lang=${language.tag}", request.url)
                HttpResponse(200, example(language.tag))
            }).entityDetail("fixture.theme.faith", language)
        }
        details.forEachIndexed { index, detail ->
            val study = assertNotNull(detail.themeStudy)
            assertEquals(if (index == 0) "Faith" else "Fé", detail.entity.name)
            assertTrue(study.overview.text.isNotBlank())
            assertTrue(study.overview.references.isNotEmpty())
            assertEquals(5, study.readings.size)
            assertEquals(2, study.comparisons.size)
            assertTrue(study.connections.size in 2..4)
            assertTrue(study.readings.all { listOf(it.title, it.context, it.observation, it.significance, it.question).all(String::isNotBlank) })
            assertTrue(study.comparisons.all { it.explanation.isNotBlank() && it.difference.isNotBlank() && it.references.size >= 2 })
            assertTrue(study.connections.all { it.entity.id.isNotBlank() && it.entity.name.isNotBlank() && it.explanation.isNotBlank() })
            assertTrue(study.sourceReferenceIds.all { id -> detail.sources.any { it.id == id } })
            assertTrue(detail.sources.filter { it.url != null }.all { it.webUrl != null })
        }
        val english = assertNotNull(details[0].themeStudy)
        val portuguese = assertNotNull(details[1].themeStudy)
        assertNotEquals(english.overview.text, portuguese.overview.text)
        assertEquals(english.readings.map { it.reference }, portuguese.readings.map { it.reference })
        assertEquals(english.readings.map { it.id }, portuguese.readings.map { it.id })
        assertEquals(english.connections.map { it.entity.id }, portuguese.connections.map { it.entity.id })
        assertEquals(english.sourceReferenceIds, portuguese.sourceReferenceIds)
    }

    @Test
    fun absentAndNullStudyRemainCompatible() {
        val detail = json.parseToJsonElement(example("en")).jsonObject.toMutableMap()
        detail.remove("themeStudy")
        assertNull(json.decodeFromString<WireEntityDetail>(JsonObject(detail).toString()).toModel().themeStudy)
        detail["themeStudy"] = JsonNull
        assertNull(json.decodeFromString<WireEntityDetail>(JsonObject(detail).toString()).toModel().themeStudy)
    }

    @Test
    fun presentStudyCannotSilentlyLoseRequiredContent() {
        val detail = json.parseToJsonElement(example("en")).jsonObject.toMutableMap()
        val study = detail.getValue("themeStudy").jsonObject.toMutableMap()
        for (field in listOf("overview", "readings", "comparisons", "connections", "sourceReferenceIds")) {
            detail["themeStudy"] = JsonObject(study - field)
            assertFailsWith<SerializationException>("missing $field") {
                json.decodeFromString<WireEntityDetail>(JsonObject(detail).toString())
            }
        }
    }
}
