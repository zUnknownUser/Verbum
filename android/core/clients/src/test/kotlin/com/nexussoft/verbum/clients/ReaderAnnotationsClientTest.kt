package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ReaderAnnotationsClientTest {
    @Test fun savesUpdatesAndRemovesWithoutDuplicatingVerses()=runTest {
        val prefs=InMemoryPreferencesClient()
        val client=PreferenceReaderAnnotationsClient(prefs)
        val note=ReaderAnnotation(PassageReference("John",1,14..14),HighlightColor.GOLD,"Meu estudo — λόγος")
        client.save(note);client.save(note.copy(highlight=HighlightColor.SAGE))
        assertEquals(listOf(note.copy(highlight=HighlightColor.SAGE)),PreferenceReaderAnnotationsClient(prefs).load())
        client.save(note.copy(highlight=null,note=""))
        assertTrue(client.load().isEmpty())
    }
    @Test fun corruptStorageIsNeverSilentlyOverwritten()=runTest {
        val prefs=InMemoryPreferencesClient();prefs.setString("readerAnnotations","broken-json")
        val client=PreferenceReaderAnnotationsClient(prefs)
        assertFails {client.save(ReaderAnnotation(PassageReference("John",1,1..1),note="new"))}
        assertEquals("broken-json",prefs.string("readerAnnotations"))
    }
}
