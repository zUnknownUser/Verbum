package com.nexussoft.verbum.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceReferenceTest {
    @Test
    fun opensOnlyCompleteHttpsReferences() {
        for (address in listOf("https://worldenglish.bible", "https://example.org/passage?search=Gen+1%3A1-3", "HTTPS://example.org/source")) {
            assertEquals(address, SourceReference("source", "Citation", address).webUrl)
        }
        for (address in listOf(null, "", "https://", "https:///missing-host", "http://example.org", "javascript:alert(1)", "https://user:password@example.org", "https://example.org/a b")) {
            assertNull(SourceReference("source", "Citation", address).webUrl)
        }
    }
}
