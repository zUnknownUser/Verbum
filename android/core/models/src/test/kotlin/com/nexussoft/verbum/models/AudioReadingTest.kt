package com.nexussoft.verbum.models
import kotlin.test.*

class AudioReadingTest {
    @Test fun exactBoundariesAndBackwardSeeks() {
        val cues=listOf(AudioCue(1,3,0.0,10.0),AudioCue(4,6,10.0,25.0))
        assertEquals(1,AudioCue.active(cues,9.99)?.verseStart)
        assertEquals(4,AudioCue.active(cues,10.0)?.verseStart)
        assertEquals(1,AudioCue.active(cues,2.0)?.verseStart)
        assertNull(AudioCue.active(cues,25.0))
        assertTrue(AudioCue.validated(cues.reversed()).isEmpty())
        assertTrue(AudioCue.validated(listOf(AudioCue(1,1,Double.NaN))).isEmpty())
    }
    @Test fun differentTranslationOrPausedPlaybackNeverHighlights() {
        val verse=BiblePassage("v","por_blj","John",1,1,1,"Texto")
        val cue=AudioCue(1,3,0.0,10.0);val ref=PassageReference("John",1)
        assertFalse(AudioReadingPosition(ref,"BSB",cue,true).contains(verse))
        assertFalse(AudioReadingPosition(ref,"por_blj",cue,false).contains(verse))
        assertTrue(AudioReadingPosition(ref,"por_blj",cue,true).contains(verse))
    }
}
