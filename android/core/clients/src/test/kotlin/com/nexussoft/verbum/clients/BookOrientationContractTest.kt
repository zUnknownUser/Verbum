package com.nexussoft.verbum.clients

import com.nexussoft.verbum.clients.api.*
import com.nexussoft.verbum.models.*
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class BookOrientationContractTest {
    private val examples = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it,"api/examples/context") }.first { it.isDirectory }
    @Test fun bilingualContextPreservesBookScopeReadingsAndSources() = runTest {
        for ((file,lang) in listOf("en" to BookLanguage.ENGLISH,"pt-BR" to BookLanguage.PORTUGUESE)) {
            val api=VerbumApi("http://contract.test", HttpTransport { HttpResponse(200,File(examples,"book-orientation.$file.json").readText()) })
            val page=assertNotNull(api.context(PassageReference("Heb",1),lang))
            val book=assertNotNull(page.bookOrientation)
            assertNull(page.study)
            assertEquals(13,book.outline.last().chapterEnd)
            assertEquals(listOf(4,10,11),book.readings.map { it.chapter })
            assertTrue(book.sourceReferenceIds.all { id -> page.sources.any { it.id==id } })
        }
    }
    @Test fun oldAndNullPayloadsStayCompatibleButPartialGuidesFail() {
        val json=Json { ignoreUnknownKeys=true }
        val payload=json.parseToJsonElement(File(examples,"book-orientation.en.json").readText()).jsonObject
        assertNull(json.decodeFromString<WirePassageContext>(JsonObject(payload-"bookOrientation").toString()).toModel().bookOrientation)
        assertNull(json.decodeFromString<WirePassageContext>(JsonObject(payload+("bookOrientation" to JsonNull)).toString()).toModel().bookOrientation)
        val partial=JsonObject(payload.getValue("bookOrientation").jsonObject-"readings")
        assertFailsWith<kotlinx.serialization.SerializationException> { json.decodeFromString<WirePassageContext>(JsonObject(payload+("bookOrientation" to partial)).toString()) }
    }
}
