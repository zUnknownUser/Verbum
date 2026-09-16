package com.nexussoft.verbum.models

import kotlin.test.*

class ReaderStudyTest {
    @Test fun preservesTextAndKeepsAmbiguousNames() {
        val entities=listOf(BibleEntity("m1",BibleEntityType.PERSON,"Moisés",null),BibleEntity("m2",BibleEntityType.PERSON,"Moisés",null))
        val text="Moisés, Moisésa e MOISÉS; λόγος."
        val result=ReaderEntityLinker.segments(text,entities)
        assertEquals(text,result.joinToString("") {it.text})
        assertEquals(2,result.count {it.entityIds.isNotEmpty()})
        assertEquals(listOf("m1","m2"),result.first().entityIds)
    }
    @Test fun identityUsesCanonNotTranslationOrDisplayName() {
        assertEquals("John.1.14",ReaderAnnotation(PassageReference("John",1,14..14)).id)
        assertEquals(1189,ReaderCanon.chapters.size)
        assertEquals(PassageReference("Rev",22),ReaderCanon.chapters.last())
    }
}
