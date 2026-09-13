package com.nexussoft.verbum.clients

import com.nexussoft.verbum.clients.api.HttpRequest
import com.nexussoft.verbum.clients.api.HttpResponse
import com.nexussoft.verbum.clients.api.HttpTransport
import com.nexussoft.verbum.clients.api.LiveAskScriptureClient
import com.nexussoft.verbum.clients.api.VerbumApi
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.ScriptureAnswer
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/** `POST /v1/ask` → the §30 contract, and its failures in the page's terms (§52). Twin of iOS `AskScriptureClientTests`. */
class AskScriptureClientTest {
    private class Script : HttpTransport {
        var next = HttpResponse(200, "")
        var request: HttpRequest? = null
        var fail = false
        override suspend fun send(request: HttpRequest): HttpResponse {
            this.request = request
            if (fail) throw IOException("offline")
            return next
        }
    }

    private val answer = """{"answer":"David killed Goliath with a sling.","summary":"A sling and a stone.","passageReferences":[{"bookId":"1Sam","chapter":17,"verseStart":49,"verseEnd":50}],"entityReferences":["fixture.person.david"],"sourceReferences":[{"id":"fixture.source.web","citation":"World English Bible","url":"https://worldenglish.bible"}],"confidence":"high","interpretiveVariance":false}"""

    private fun client(script: Script) = LiveAskScriptureClient(VerbumApi("http://test.local:8080", script))

    @Test
    fun postsTheQuestionAndDecodesTheContract() = runTest {
        val script = Script().apply { next = HttpResponse(200, answer) }
        val got = client(script).ask("  how did David defeat Goliath \n")
        assertEquals("A sling and a stone.", got.summary)
        assertEquals(listOf(PassageReference("1Sam", 17, 49..50)), got.passageReferences)
        assertEquals(listOf("fixture.person.david"), got.entityReferences)
        assertEquals(ScriptureAnswer.Confidence.HIGH, got.confidence)
        assertFalse(got.isEmpty)
        val request = assertNotNull(script.request)
        assertEquals("POST", request.method)
        assertEquals("http://test.local:8080/v1/ask", request.url)
        assertEquals("""{"question":"how did David defeat Goliath"}""", request.body)
    }

    @Test
    fun failuresInThePagesTerms() = runTest {
        val script = Script()
        script.next = HttpResponse(503, """{"code":"ask_unavailable","message":"ask is not configured on this server"}""")
        assertFailsWith<AskScriptureException.Unavailable> { client(script).ask("q") }
        script.next = HttpResponse(404, "404 page not found") // a backend without the route at all
        assertFailsWith<AskScriptureException.Unavailable> { client(script).ask("q") }
        script.next = HttpResponse(502, """{"code":"internal","message":"could not answer this question"}""")
        assertFailsWith<AskScriptureException.Failed> { client(script).ask("q") }
        script.next = HttpResponse(200, "not json")
        assertFailsWith<AskScriptureException.Failed> { client(script).ask("q") }
        script.fail = true
        assertFailsWith<AskScriptureException.NetworkUnavailable> { client(script).ask("q") }
    }
}
