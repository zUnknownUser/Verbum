package com.nexussoft.verbum.clients

import com.nexussoft.verbum.clients.api.HttpRequest
import com.nexussoft.verbum.clients.api.HttpResponse
import com.nexussoft.verbum.clients.api.HttpTransport
import com.nexussoft.verbum.clients.api.LiveRealtimeSessionClient
import com.nexussoft.verbum.clients.api.VerbumApi
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RealtimeSessionClientTest {
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

    private fun client(script: Script) = LiveRealtimeSessionClient(VerbumApi("http://test.local:8080", script))

    @Test
    fun mintsASession() = runTest {
        val script = Script().apply { next = HttpResponse(200, """{"clientSecret":"ek_abc","expiresAt":1757800000,"model":"gpt-realtime"}""") }
        assertEquals(RealtimeSession("ek_abc", 1_757_800_000, "gpt-realtime"), client(script).create())
        assertEquals("POST", script.request?.method)
        assertEquals("http://test.local:8080/v1/realtime/session", script.request?.url)
    }

    @Test
    fun failuresInTheSheetsTerms() = runTest {
        val script = Script()
        script.next = HttpResponse(503, """{"code":"realtime_unavailable","message":"realtime is not configured on this server"}""")
        assertFailsWith<VoiceException.Unavailable> { client(script).create() }
        script.next = HttpResponse(404, "404 page not found")
        assertFailsWith<VoiceException.Unavailable> { client(script).create() }
        script.next = HttpResponse(502, """{"code":"internal","message":"could not create a realtime session"}""")
        assertFailsWith<VoiceException.Failed> { client(script).create() }
        script.fail = true
        assertFailsWith<VoiceException.NetworkUnavailable> { client(script).create() }
    }
}
