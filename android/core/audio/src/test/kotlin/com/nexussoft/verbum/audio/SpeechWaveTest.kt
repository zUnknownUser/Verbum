package com.nexussoft.verbum.audio

import java.io.File
import java.io.RandomAccessFile
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.*

class SpeechWaveTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun concatenatesSamplesWithoutDuplicatingHeaders() {
        val first = wave("one.wav", 1)
        val second = wave("two.wav", 2)
        val output = folder.newFile("joined.wav")
        joinSpeechWaves(listOf(first, second), output)
        RandomAccessFile(output, "r").use {
            assertEquals(48, it.length())
            it.seek(40)
            assertEquals(4, Integer.reverseBytes(it.readInt()))
            assertEquals(1, java.lang.Short.reverseBytes(it.readShort()).toInt())
            assertEquals(2, java.lang.Short.reverseBytes(it.readShort()).toInt())
        }
    }

    @Test fun rejectsInvalidAudioInsteadOfPlayingCorruptBytes() {
        val bad = folder.newFile("bad.wav").also { it.writeText("not speech") }
        assertFails { joinSpeechWaves(listOf(bad), folder.newFile("output.wav")) }
    }

    private fun wave(name: String, sample: Int): File = folder.newFile(name).also { file ->
        RandomAccessFile(file, "rw").use {
            fun word(value: Int) { it.writeInt(Integer.reverseBytes(value)) }
            fun short(value: Int) { it.writeShort(java.lang.Short.reverseBytes(value.toShort()).toInt()) }
            it.writeBytes("RIFF"); word(38); it.writeBytes("WAVEfmt "); word(16)
            short(1); short(1); word(16000); word(32000); short(2); short(16)
            it.writeBytes("data"); word(2); short(sample)
        }
    }
}
