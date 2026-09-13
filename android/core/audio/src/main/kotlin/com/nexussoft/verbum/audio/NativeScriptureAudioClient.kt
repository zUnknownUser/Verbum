package com.nexussoft.verbum.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.nexussoft.verbum.clients.BibleClient
import com.nexussoft.verbum.clients.helloao.HelloAOScriptureAudioClient
import com.nexussoft.verbum.clients.helloao.ScriptureAudioClient
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/** No offline Portuguese voice is installed; the caller may offer recordings in another language, labelled. */
class VoiceUnavailableException : IllegalStateException("Install a Portuguese (Brazil) offline voice in system speech settings")

/**
 * Production audio: in Portuguese the device reads the translation on screen aloud (no Portuguese
 * recordings exist for these translations); in English the helloao recordings. Without a
 * Portuguese voice the English recordings are offered, labelled as such — never passed off as
 * the reading. Twin of iOS `ScriptureAudioClient.live`.
 */
class LiveScriptureAudioClient(language: BookLanguage, context: Context, bible: BibleClient) : ScriptureAudioClient {
    private val recordings = HelloAOScriptureAudioClient(language)
    private val native: ScriptureAudioClient? = if (language == BookLanguage.PORTUGUESE) NativeScriptureAudioClient(context, bible) else null

    override suspend fun chapterAudio(bookId: BookId, chapter: Int): ChapterAudio? {
        val native = native ?: return recordings.chapterAudio(bookId, chapter)
        return try {
            native.chapterAudio(bookId, chapter)
        } catch (e: VoiceUnavailableException) {
            recordings.chapterAudio(bookId, chapter)
        }
    }
}

/** Generates local speech from the exact reading translation, then uses the
 * existing MediaSession player for pause, seek, speed and background playback.
 * The next chapter is rendered in the background once this one is, so chaining does not wait. */
class NativeScriptureAudioClient(context: Context, private val bible: BibleClient) : ScriptureAudioClient {
    private val prefetch = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val context = context.applicationContext
    private val mutex = Mutex()

    override suspend fun chapterAudio(bookId: BookId, chapter: Int): ChapterAudio? {
        val audio = renderChapter(bookId, chapter)
        nextChapter(bookId, chapter)?.let { next -> prefetch.launch { runCatching { renderChapter(next.bookId, next.chapter) } } }
        return audio
    }

    private suspend fun renderChapter(bookId: BookId, chapter: Int): ChapterAudio? = mutex.withLock {
        val verses = bible.chapter(bookId, chapter)
        val first = verses.firstOrNull() ?: return@withLock null
        val text = verses.joinToString("\n") { it.text }
        val url = withContext(Dispatchers.IO) { render(text) }
        ChapterAudio(first.translationId, "Leitura automática · Português", PassageReference(bookId, chapter),
            listOf(AudioNarrator(AudioNarrator.SYNTHESISED_PREFIX + "pt-BR", "Leitura automática", url.toURI().toString(), null)))
    }

    private fun nextChapter(bookId: BookId, chapter: Int): PassageReference? {
        val book = BibleBook.book(bookId) ?: return null
        if (chapter < book.chapterCount) return PassageReference(bookId, chapter + 1)
        val nextBook = BibleBook.canon.firstOrNull { it.order == book.order + 1 } ?: return null
        return PassageReference(nextBook.id, 1)
    }

    private suspend fun render(text: String): File {
        val initialized = CompletableDeferred<Int>()
        val tts = withContext(Dispatchers.Main) { TextToSpeech(context) { initialized.complete(it) } }
        val directory = File(context.cacheDir, "native-speech-v1").also { check(it.isDirectory || it.mkdirs()) }
        val temporary = mutableListOf<File>()
        try {
            check(withTimeout(15_000) { initialized.await() } == TextToSpeech.SUCCESS)
            val voice = withContext(Dispatchers.Main) {
                // Offline voices only: no silent upload of Scripture to an engine service.
                tts.voices.orEmpty().filter {
                    it.locale.language == "pt" && it.locale.country == "BR" && !it.isNetworkConnectionRequired &&
                        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
                }
                    .maxByOrNull { it.quality }?.also { check(tts.setVoice(it) == TextToSpeech.SUCCESS) }
                    ?: throw VoiceUnavailableException()
            }
            val key = MessageDigest.getInstance("SHA-256").digest((voice.name + "\n" + text).toByteArray()).joinToString("") { "%02x".format(it) }
            val output = File(directory, "$key.wav")
            if (output.exists()) { output.setLastModified(System.currentTimeMillis()); return output }
            val pending = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<Unit>>()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) { id?.let { pending.remove(it)?.complete(Unit) } }
                @Deprecated("Android callback")
                override fun onError(id: String?) { id?.let { pending.remove(it)?.completeExceptionally(IllegalStateException("Speech failed")) } }
                override fun onError(id: String?, code: Int) { onError(id) }
            })
            // A voice that is present but still downloading, or the engine failing to speak at all,
            // must not read as "no voice": those fall through as plain failures (retry), not English.
            // Never truncate a long chapter (Psalm 119 exceeds the engine limit).
            for (chunk in speechChunks(text, TextToSpeech.getMaxSpeechInputLength() - 1)) {
                currentCoroutineContext().ensureActive()
                val id = UUID.randomUUID().toString()
                val part = File(directory, "$id.partial.wav").also { temporary += it }
                val done = CompletableDeferred<Unit>().also { pending[id] = it }
                check(tts.synthesizeToFile(chunk, Bundle(), part, id) == TextToSpeech.SUCCESS)
                withTimeout(90_000) { done.await() }
            }
            val joined = File(directory, UUID.randomUUID().toString() + ".partial.wav").also { temporary += it }
            joinSpeechWaves(temporary.dropLast(1), joined)
            currentCoroutineContext().ensureActive()
            check(joined.renameTo(output))
            // Newest first: at most 24 chapters / ~200 MB (16-bit WAV is ~2.9 MB per minute; Psalm 119 is ~44 MB).
            var bytes = 0L
            directory.listFiles().orEmpty().filter { it.extension == "wav" && !it.name.endsWith(".partial.wav") }
                .sortedByDescending { it.lastModified() }.forEachIndexed { index, file ->
                    bytes += file.length()
                    if (file != output && (index >= 24 || bytes > 200_000_000L)) file.delete()
                }
            return output
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { tts.stop(); tts.shutdown() }
            temporary.forEach { it.delete() }
        }
    }
}

internal fun speechChunks(text: String, limit: Int): List<String> {
    require(limit > 1)
    val result = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = minOf(start + limit, text.length)
        if (end < text.length) {
            val space = text.lastIndexOfAny(charArrayOf(' ', '\n'), end - 1)
            if (space > start) end = space + 1
            else if (Character.isHighSurrogate(text[end - 1])) end--
        }
        result += text.substring(start, end)
        start = end
    }
    return result
}

/** Stream WAV chunks rather than loading an entire spoken chapter into memory.
 * Validate format equality before concatenation; unknown formats fail safely. */
internal fun joinSpeechWaves(parts: List<File>, output: File) {
    var format: ByteArray? = null
    var dataSize = 0L
    var dataSizePosition = 0L
    RandomAccessFile(output, "rw").use { out ->
        out.setLength(0)
        out.writeBytes("RIFF"); out.writeInt(0); out.writeBytes("WAVE")
        for (part in parts) RandomAccessFile(part, "r").use { input ->
            fun tag(): String = ByteArray(4).also { input.readFully(it) }.toString(Charsets.US_ASCII)
            check(tag() == "RIFF"); input.skipBytes(4); check(tag() == "WAVE")
            var partFormat: ByteArray? = null
            var hasData = false
            while (input.filePointer + 8 <= input.length()) {
                val name = tag()
                val size = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
                val next = input.filePointer + size + size % 2
                check(next <= input.length())
                when (name) {
                    "fmt " -> {
                        check(size in 16..1024)
                        partFormat = ByteArray(size.toInt()).also { input.readFully(it) }
                    }
                    "data" -> {
                        val currentFormat = checkNotNull(partFormat)
                        if (format == null) {
                            format = currentFormat
                            out.writeBytes("fmt "); out.writeInt(Integer.reverseBytes(currentFormat.size)); out.write(currentFormat)
                            if (currentFormat.size % 2 != 0) out.write(0)
                            out.writeBytes("data"); dataSizePosition = out.filePointer; out.writeInt(0)
                        } else check(format!!.contentEquals(currentFormat))
                        val buffer = ByteArray(8192)
                        var remaining = size
                        while (remaining > 0) {
                            val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                            check(count > 0); out.write(buffer, 0, count); remaining -= count
                        }
                        dataSize += size
                        hasData = true
                    }
                }
                input.seek(next)
            }
            check(hasData)
        }
        check(dataSize in 1..Int.MAX_VALUE.toLong())
        if (dataSize % 2 != 0L) out.write(0)
        val length = out.length()
        out.seek(4); out.writeInt(Integer.reverseBytes((length - 8).toInt()))
        out.seek(dataSizePosition); out.writeInt(Integer.reverseBytes(dataSize.toInt()))
    }
}
