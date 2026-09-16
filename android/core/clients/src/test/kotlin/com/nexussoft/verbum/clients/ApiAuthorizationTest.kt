package com.nexussoft.verbum.clients

import com.nexussoft.verbum.clients.api.*
import com.nexussoft.verbum.models.BibleEntityType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiAuthorizationTest {
    @Test fun publicContentDoesNotCreateGuestAndBothPaidTransportsAttachToken() = runTest {
        val tokenRequests = mutableListOf<Boolean>()
        val requests = mutableListOf<HttpRequest>()
        val api = VerbumApi("https://api.example.test", transport = HttpTransport {
            requests += it
            if (it.method == "GET") HttpResponse(200, """{"entities":[]}""")
            else HttpResponse(200, """{"clientSecret":"ek_test","expiresAt":1,"model":"gpt-realtime"}""")
        }, binaryTransport = BinaryHttpTransport {
            requests += it; BinaryHttpResponse(200, byteArrayOf(1))
        }, tokenProvider = { tokenRequests += it; "test-id-token" })
        api.entities(BibleEntityType.PERSON)
        api.realtimeSession()
        api.synthesizeSpeech("Texto", "pt-BR")
        assertEquals(emptyMap(), requests[0].headers)
        assertEquals("Bearer test-id-token", requests[1].headers["Authorization"])
        assertEquals("Bearer test-id-token", requests[2].headers["Authorization"])
        assertEquals(listOf(true, true), tokenRequests)
    }

    @Test fun searchDoesNotCreateGuestAndSeparatesLexicalAndSemanticCache() = runTest {
        val calls = mutableListOf<HttpRequest>()
        val cache = ResponseCache(null)
        val transport = HttpTransport { calls += it; HttpResponse(200, """{"query":"David","passages":[],"books":[],"entities":[]}""") }
        val base = "https://api.example.test"
        VerbumApi(base, transport, cache).search("David")
        val tokenRequests = mutableListOf<Boolean>()
        VerbumApi(base, transport, cache, tokenProvider = { tokenRequests += it; "test-id-token" }).search("David")
        assertEquals(2, calls.size)
        kotlin.test.assertTrue(calls[1].headers["X-Verbum-Installation"]?.isNotBlank() == true)
        assertEquals(listOf(false), tokenRequests)
        assertEquals("Bearer test-id-token", calls[1].headers["Authorization"])
    }

    @Test fun credentialFailureNeverSendsPaidRequest() = runTest {
        var calls = 0
        val api = VerbumApi("https://api.example.test", binaryTransport = BinaryHttpTransport {
            calls++; BinaryHttpResponse(200, byteArrayOf(1))
        }, tokenProvider = { throw VerbumApiException.Problem(ProblemCode.UNAUTHENTICATED, 401) })
        assertFailsWith<VerbumApiException.Problem> { api.synthesizeSpeech("Texto", "pt-BR") }
        assertEquals(0, calls)
    }
}
