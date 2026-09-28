package com.nexussoft.verbum.models

import kotlin.test.*

class ReadingCollectionTest {
    private val john = ReaderAnnotation(PassageReference("John", 3, 16..16), HighlightColor.GOLD, "Esperança e amor", bookmarked = true)
    private val job = ReaderAnnotation(PassageReference("Job", 38, 4..4), note = "Where were you?")
    @Test fun searchesBothLanguagesAccentsReferencesAndNotes() {
        for (query in listOf("João 3:16", "joao", "John", "esperanca")) {
            assertEquals(listOf(john), ReadingCollection.entries(listOf(job, john), ReadingCollectionFilter.ALL, query))
        }
        assertEquals(listOf(job), ReadingCollection.entries(listOf(john, job), ReadingCollectionFilter.NOTES, "where"))
    }
    @Test fun filtersOverlapWithoutDuplicatingVersesAndSortByCanon() {
        val items = listOf(john, job, ReaderAnnotation(PassageReference("Gen", 1, 1..1)))
        assertEquals(listOf(job, john), ReadingCollection.entries(items, ReadingCollectionFilter.ALL, ""))
        assertEquals(listOf(john), ReadingCollection.entries(items, ReadingCollectionFilter.SAVED, ""))
        assertEquals(listOf(john), ReadingCollection.entries(items, ReadingCollectionFilter.HIGHLIGHTS, ""))
        assertTrue(ReadingCollection.entries(items, ReadingCollectionFilter.NOTES, "missing").isEmpty())
    }
}
