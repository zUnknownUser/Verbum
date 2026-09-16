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

    @Test fun standaloneBookmarkPersistsAndCanBeRemoved() = runTest {
        val prefs = InMemoryPreferencesClient()
        val note = ReaderAnnotation(PassageReference("John", 1, 14..14), bookmarked = true)
        PreferenceReaderAnnotationsClient(prefs).save(note)
        val restored = PreferenceReaderAnnotationsClient(prefs)
        assertEquals(listOf(note), restored.load())
        restored.save(note.copy(bookmarked = false))
        assertTrue(restored.load().isEmpty())
    }
    @Test fun legacyNotesSurviveAddingBookmarks() = runTest {
        val prefs = InMemoryPreferencesClient()
        prefs.setString("readerAnnotations", """[{"book":"John","chapter":1,"verse":14,"color":"GOLD","note":"Minha nota"}]""")
        val client = PreferenceReaderAnnotationsClient(prefs)
        val old = client.load().single()
        assertFalse(old.bookmarked)
        client.save(old.copy(bookmarked = true))
        assertEquals("Minha nota", client.load().single().note)
        assertEquals(HighlightColor.GOLD, client.load().single().highlight)
    }
}
