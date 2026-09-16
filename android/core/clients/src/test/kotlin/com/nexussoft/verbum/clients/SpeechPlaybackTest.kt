package com.nexussoft.verbum.clients

import com.nexussoft.verbum.clients.api.*
import com.nexussoft.verbum.models.BiblePassage
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SpeechPlaybackTest {
    private val path="/v1/tts/playback/"+"a".repeat(64)
    @Test fun startsOnceAndPollsWithoutCachingPendingStatus() = runTest {
        val requests=mutableListOf<HttpRequest>()
        val replies=ArrayDeque(listOf(
            """{"statusPath":"$path/status"}""",
            """{"ready":false,"complete":false,"playlistPath":"$path/index.m3u8","audioPath":"$path/chapter.mp3","cues":null}""",
            """{"ready":true,"complete":false,"playlistPath":"$path/index.m3u8","audioPath":"$path/chapter.mp3","cues":null}"""
        ))
        val api=VerbumApi("https://example.test",HttpTransport {request->requests.add(request);HttpResponse(200,replies.removeFirst())})
        val status=api.startSpeechPlayback(listOf(BiblePassage("por_blj:Ps.55.1","por_blj","Ps",55,1,1,"Texto.")),null)
        assertFalse(api.speechPlaybackStatus(status).ready)
        assertTrue(api.speechPlaybackStatus(status).ready)
        assertEquals(listOf("POST","GET","GET"),requests.map{it.method})
    }
    @Test fun refusesExternalOrUnexpectedPlaybackUrls() {
        val api=VerbumApi("https://example.test")
        for (value in listOf("https://evil.test/audio", "$path/../secret", "$path/status?token=x", "/v1/tts/playback/short/status")) {
            assertFailsWith<VerbumApiException.MalformedResponse>{api.playbackUrl(value)}
        }
        assertEquals("https://example.test$path/index.m3u8",api.playbackUrl("$path/index.m3u8"))
    }
}
