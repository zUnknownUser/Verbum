package com.nexussoft.verbum.audio

import kotlin.test.*

class SpeechChunksTest {
    @Test fun longTextIsNotTruncated() {
        val text = "Texto longo com espaços e parágrafos.\n".repeat(500)
        val chunks = speechChunks(text, 100)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length in 1..100 })
    }
    @Test fun surrogatePairsAreNotSplit() {
        val text = "ab😀c😀def"
        val chunks = speechChunks(text, 3)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.none { Character.isHighSurrogate(it.last()) })
    }
}
