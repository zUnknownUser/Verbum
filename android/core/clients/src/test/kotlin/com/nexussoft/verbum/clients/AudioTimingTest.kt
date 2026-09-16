package com.nexussoft.verbum.clients
import com.nexussoft.verbum.clients.helloao.HelloAOScriptureAudioClient
import com.nexussoft.verbum.models.*
import kotlin.test.*

class AudioTimingTest {
    @Test fun timingsMustIdentifyExactRecording() {
        val data="""{"translationId":"BSB","bookId":"JHN","chapterNumber":1,"reader":"david","audioLink":"https://example.test/john.mp3","verses":[2.5,8,13.25]}"""
        val narrator=AudioNarrator("david","David","https://example.test/john.mp3",null)
        val cues=HelloAOScriptureAudioClient.parseTimings(data,narrator,"BSB","JHN",1)
        assertEquals(3,cues.size);assertNull(AudioCue.active(cues,2.0));assertEquals(2,AudioCue.active(cues,8.0)?.verseStart)
        assertTrue(HelloAOScriptureAudioClient.parseTimings(data,narrator,"por_blj","JHN",1).isEmpty())
        assertTrue(HelloAOScriptureAudioClient.parseTimings(data,narrator,"BSB","JHN",2).isEmpty())
    }
}
