package com.nexussoft.verbum.clients
import com.nexussoft.verbum.clients.api.*
import com.nexussoft.verbum.models.*
import kotlin.test.*

class AudioCueWireTest {
    @Test fun audioMetadataRoundTripsAndFailsClosed() {
        val cues=listOf(AudioCue(1,3,0.0,12.25),AudioCue(4,4,12.25,14.0))
        assertEquals(cues,decodeAudioCues(encodeAudioCues(cues)))
        assertTrue(decodeAudioCues(null).isEmpty())
        assertTrue(decodeAudioCues("broken").isEmpty())
        assertTrue(decodeAudioCues("""[{"verseStart":1,"verseEnd":1,"start":3,"end":2}]""").isEmpty())
    }
}
