package com.nexussoft.verbum.clients
import com.nexussoft.verbum.clients.api.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class UsagePolicyTest {
    @Test fun usageIsAuthenticatedAndNeverCached() = runTest {
        val requests = mutableListOf<HttpRequest>()
        val api = VerbumApi("https://api.example.test", transport = HttpTransport {
            requests += it
            HttpResponse(200, """{"plan":"free","resetsAt":"2026-09-17T00:00:00Z","remaining":{"ask":4},"voiceSeconds":60,"restricted":false}""")
        }, tokenProvider = { "token" })
        assertEquals(4, api.usageStatus()?.remaining?.get("ask"))
        api.usageStatus()
        assertEquals(2,requests.size)
        assertTrue(requests.all { it.headers["Authorization"] == "Bearer token" })
    }
    @Test fun restrictionKeepsRetryDate() = runTest {
        val api = VerbumApi("https://api.example.test", binaryTransport = BinaryHttpTransport {
            BinaryHttpResponse(429, byteArrayOf(), """{"code":"quota_exceeded","message":"limited","retryAt":"2026-09-17T00:00:00Z"}""")
        }, tokenProvider = { "token" })
        val error = assertFailsWith<VerbumApiException.Restricted> {api.synthesizeSpeech("Texto","pt-BR")}
        assertEquals("2026-09-17T00:00:00Z", error.restriction.retryAt)
    }
    @Test fun relayIsBoundToBackendHost() = runTest {
        val api = VerbumApi("https://api.example.test", transport = HttpTransport {
            HttpResponse(200, """{"clientSecret":"ticket","expiresAt":1,"model":"gpt-realtime","relayPath":"/v1/realtime/connect","maxDurationSeconds":60}""")
        }, tokenProvider = { "token" })
        assertEquals("wss://api.example.test/v1/realtime/connect",api.realtimeSession().relayUrl)
    }
}
