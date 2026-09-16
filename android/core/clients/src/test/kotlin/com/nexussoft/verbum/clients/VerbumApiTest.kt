package com.nexussoft.verbum.clients

import com.nexussoft.verbum.clients.api.BinaryHttpResponse
import com.nexussoft.verbum.clients.api.BinaryHttpTransport
import com.nexussoft.verbum.clients.api.HttpRequest
import com.nexussoft.verbum.clients.api.HttpResponse
import com.nexussoft.verbum.clients.api.HttpTransport
import com.nexussoft.verbum.clients.api.ProblemCode
import com.nexussoft.verbum.clients.api.ResponseCache
import com.nexussoft.verbum.clients.api.VerbumApi
import com.nexussoft.verbum.clients.api.VerbumApiException
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.test.assertNotSame
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plumbing of [VerbumApi]: URLs, error mapping (§52) and the offline cache (§39). Twin of iOS `VerbumAPITests`. */
class VerbumApiTest {
    /** A transport scripted per request; records what was asked. */
    private class Script : HttpTransport {
        val responses = ArrayDeque<HttpResponse>()
        val requests = mutableListOf<HttpRequest>()
        var failWith: Exception? = null

        override suspend fun send(request: HttpRequest): HttpResponse {
            requests += request
            failWith?.let { throw it }
            return responses.removeFirstOrNull() ?: HttpResponse(200, "{}")
        }
    }

    private val base = "http://test.local:8080"

    @Test fun editorialRequestsLeaveTheCallerThreadAndKeepCacheHits() = runTest {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { ui ->
            withContext(ui) {
                val caller = Thread.currentThread()
                var requests = 0
                val api = VerbumApi(base, HttpTransport {
                    assertNotSame(caller, Thread.currentThread())
                    requests++
                    HttpResponse(200, """{"entities":[]}""")
                }, now = {
                    // Runs both when saving a response and when checking a fresh cache hit.
                    assertNotSame(caller, Thread.currentThread())
                    0L
                })
                repeat(2) { assertTrue(api.entities(BibleEntityType.PERSON).isEmpty()) }
                assertEquals(1, requests)
                assertTrue(Thread.currentThread() === caller, "caller resumes on its own dispatcher")
            }
        }
    }

    @Test fun speechManifestIsCachedAndRefreshesAfterOneHour() = runTest {
        val script = Script()
        val first = "a".repeat(64); val second = "b".repeat(64)
        script.responses += HttpResponse(200, """{"version":"$first"}""")
        script.responses += HttpResponse(200, """{"version":"$second"}""")
        var time = 0L
        val api = VerbumApi(base, script, cache = ResponseCache(null), now = { time })
        assertEquals(first, api.speechVersion("pt-BR"))
        assertEquals(first, api.speechVersion("pt-BR"))
        assertEquals(1, script.requests.size)
        time = 3_600_001L
        assertEquals(second, api.speechVersion("pt-BR"))
        time = 7_200_002L
        script.failWith = IOException("offline")
        assertEquals(second, api.speechVersion("pt-BR"))
    }
    private val emptyTimeline = """{"events":[],"entityNames":{}}"""

    @Test
    fun buildsTheContractUrls() = runTest {
        val script = Script()
        script.responses += HttpResponse(200, """{"entities":[]}""")
        script.responses += HttpResponse(200, """{"root":{"id":"a","type":"person","name":"A","summary":null},"nodes":[],"edges":[]}""")
        script.responses += HttpResponse(200, emptyTimeline)
        script.responses += HttpResponse(200, """{"query":"x y","passages":[],"books":[],"entities":[]}""")
        script.responses += HttpResponse(200, """{"verses":[]}""")
        val api = VerbumApi(base, script)
        api.entities(BibleEntityType.PERSON, BookLanguage.ENGLISH)
        api.graph("a", 24)
        api.timeline("fixture.person.david")
        api.search("x y", BookLanguage.PORTUGUESE)
        api.dailyVerses("2026-09-13", 7)
        assertEquals(
            listOf(
                "$base/v1/entities?type=person&lang=en",
                "$base/v1/entities/a/graph?limit=24&lang=${BookLanguage.current.tag}",
                "$base/v1/timeline?entity=fixture.person.david&lang=${BookLanguage.current.tag}",
                "$base/v1/search?q=x%20y&lang=pt",
                "$base/v1/daily-verse?from=2026-09-13&days=7",
            ),
            script.requests.map { it.url },
        )
        assertTrue(script.requests.all { it.method == "GET" && it.body == null })
    }

    @Test
    fun problemCodesBecomeTypedErrors() = runTest {
        val script = Script()
        script.responses += HttpResponse(404, """{"code":"unknown_entity","message":"no entity with id 'nope'"}""")
        script.responses += HttpResponse(404, """{"code":"content_unavailable","message":"no context for Gen.1"}""")
        script.responses += HttpResponse(503, """{"code":"ask_unavailable","message":"ask is not configured"}""")
        script.responses += HttpResponse(500, "not json")
        script.responses += HttpResponse(200, "not json either")
        val api = VerbumApi(base, script)
        assertEquals(VerbumApiException.Problem(ProblemCode.UNKNOWN_ENTITY, 404), assertFailsWith<VerbumApiException.Problem> { api.entityDetail("nope") })
        // Missing context is a state, not an error (§3.5).
        assertNull(api.context(PassageReference("Gen", 1, 3..5)))
        assertEquals(VerbumApiException.Problem(ProblemCode.ASK_UNAVAILABLE, 503), assertFailsWith<VerbumApiException.Problem> { api.entityDetail("x") })
        assertEquals(VerbumApiException.Problem(ProblemCode.UNKNOWN, 500), assertFailsWith<VerbumApiException.Problem> { api.entityDetail("x") })
        assertFailsWith<VerbumApiException.MalformedResponse> { api.entityDetail("x") }
        // Context stays per chapter; the starting verse narrows related occurrences.
        assertEquals("$base/v1/passages/Gen.1/context?lang=${BookLanguage.current.tag}&verse=3", script.requests[1].url)
    }

    @Test
    fun transportFailureIsNetworkUnavailable() = runTest {
        val script = Script().apply { failWith = IOException("no route") }
        assertFailsWith<VerbumApiException.NetworkUnavailable> { VerbumApi(base, script).timeline() }
    }

    @Test
    fun freshAnswersAreServedFromTheCacheAndStaleOnesSurviveAFailure() = runTest {
        val script = Script()
        script.responses += HttpResponse(200, emptyTimeline)
        script.responses += HttpResponse(200, emptyTimeline)
        var now = 1_000_000_000L
        val api = VerbumApi(base, script, ResponseCache(null)) { now }

        api.timeline()
        api.timeline()
        assertEquals(1, script.requests.size, "fresh within max-age: no second request")

        now += VerbumApi.FRESH_FOR_MS + 1
        api.timeline()
        assertEquals(2, script.requests.size, "stale: revalidated")

        now += VerbumApi.FRESH_FOR_MS + 1
        script.failWith = IOException("timeout")
        assertTrue(api.timeline().events.isEmpty(), "stale answer beats no answer (§39)")
        assertEquals(3, script.requests.size)

        assertFailsWith<VerbumApiException.NetworkUnavailable> { api.timeline("never-seen") }
    }

    @Test
    fun cacheSurvivesOnDisk() = runTest {
        val directory = File(System.getProperty("java.io.tmpdir"), "verbum-api-test-${System.nanoTime()}")
        try {
            val script = Script()
            script.responses += HttpResponse(200, emptyTimeline)
            VerbumApi(base, script, ResponseCache(directory)).timeline()
            script.failWith = IOException("timeout")
            VerbumApi(base, script, ResponseCache(directory)).timeline()
        } finally {
            directory.deleteRecursively()
        }
    }

    /** A binary transport scripted per request; records what was asked. */
    private class BinaryScript : BinaryHttpTransport {
        val responses = ArrayDeque<BinaryHttpResponse>()
        val requests = mutableListOf<HttpRequest>()
        var failWith: Exception? = null

        override suspend fun send(request: HttpRequest): BinaryHttpResponse {
            requests += request
            failWith?.let { throw it }
            return responses.removeFirstOrNull() ?: BinaryHttpResponse(200, ByteArray(0))
        }
    }

    @Test
    fun synthesizeSpeechReturnsRawAudioBytes() = runTest {
        val script = BinaryScript()
        script.responses += BinaryHttpResponse(200, "ID3fakeaudio".toByteArray())
        val api = VerbumApi(base, binaryTransport = script)
        val audio = api.synthesizeSpeech("Texto", "pt-BR")
        assertEquals("ID3fakeaudio", audio.toString(Charsets.UTF_8))
        assertEquals("$base/v1/tts", script.requests.single().url)
        assertEquals("POST", script.requests.single().method)
        assertEquals("""{"text":"Texto","language":"pt-BR"}""", script.requests.single().body)
    }

    @Test
    fun synthesizeSpeechMapsProviderErrors() = runTest {
        val script = BinaryScript()
        script.responses += BinaryHttpResponse(503, ByteArray(0), """{"code":"tts_unavailable","message":"speech provider unavailable"}""")
        val api = VerbumApi(base, binaryTransport = script)
        assertEquals(
            VerbumApiException.Problem(ProblemCode.TTS_UNAVAILABLE, 503),
            assertFailsWith<VerbumApiException.Problem> { api.synthesizeSpeech("x", "pt-BR") },
        )
    }
}
