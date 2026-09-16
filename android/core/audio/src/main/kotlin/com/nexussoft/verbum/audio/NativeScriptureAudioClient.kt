package com.nexussoft.verbum.audio

import android.content.Context
import com.nexussoft.verbum.clients.BibleClient
import com.nexussoft.verbum.clients.api.VerbumApi
import com.nexussoft.verbum.clients.api.decodeAudioCues
import com.nexussoft.verbum.clients.api.encodeAudioCues
import com.nexussoft.verbum.clients.helloao.HelloAOScriptureAudioClient
import com.nexussoft.verbum.clients.helloao.ScriptureAudioClient
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Production audio: in Portuguese the backend's Google Cloud voice reads the translation on
 * screen aloud (no Portuguese recordings exist for these translations); in English the helloao
 * recordings. A Portuguese TTS failure is surfaced to the listener. Twin of iOS `ScriptureAudioClient.live`.
 */
class LiveScriptureAudioClient(language: BookLanguage, context: Context, bible: BibleClient, api: VerbumApi) : ScriptureAudioClient {
    private val recordings = HelloAOScriptureAudioClient(language)
    private val cloud: ScriptureAudioClient? = if (language == BookLanguage.PORTUGUESE) CloudScriptureAudioClient(context, bible, api) else null

    override suspend fun chapterAudio(bookId: BookId, chapter: Int): ChapterAudio? {
        val cloud = cloud ?: return recordings.chapterAudio(bookId, chapter)
        return cloud.chapterAudio(bookId, chapter)
    }
}

/**
 * Generates chapter speech via the backend's Google Cloud TTS (`POST /v1/tts`), then uses the
 * existing MediaSession player for pause, seek, speed and background playback. Only the chapter the listener opens can request a new generation.
 */
class CloudScriptureAudioClient(context: Context, private val bible: BibleClient, private val api: VerbumApi) : ScriptureAudioClient {
    private val context = context.applicationContext
    private val mutex = Mutex()
    private val downloads = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun chapterAudio(bookId: BookId, chapter: Int): ChapterAudio? {
        val audio = withContext(Dispatchers.IO) { renderChapter(bookId, chapter) }
        return audio
    }

    private suspend fun renderChapter(bookId: BookId, chapter: Int): ChapterAudio? = mutex.withLock {
        val verses = bible.chapter(bookId, chapter)
        val first = verses.firstOrNull() ?: return@withLock null
        val (file,cues) = render(verses)
        ChapterAudio(first.translationId, "Leitura automática · Português", PassageReference(bookId, chapter),
            listOf(AudioNarrator(AudioNarrator.SYNTHESISED_PREFIX + "pt-BR", "Leitura automática", file, null,cues)))
    }

    /** One MP3 per exact chapter text and server voice version, cached on disk. The backend also caches server-side by
     * the same text, so a cold local cache (after reinstall, or a pruned entry) still answers
     * without paying for a new generation — only the round trip. */
    private suspend fun render(verses:List<BiblePassage>): Pair<String,List<AudioCue>> {
        val text=verses.joinToString("\n") {it.text}
        val directory = File(context.cacheDir, "cloud-speech-pt-BR-v1").also { check(it.isDirectory || it.mkdirs()) }
        // One manifest check/hour, with the API cache's offline fallback. Older servers
        // keep using legacy files until the version endpoint is deployed.
        val version = try { api.speechVersion("pt-BR") }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        val identity = "sync-v1\n"+verses.joinToString(",") {it.verseStart.toString()}+"\n"+(version?.let { "$it\npt-BR\n$text" } ?: text)
        val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val output = File(directory, "$key.mp3")
        if (output.exists()) {
            output.setLastModified(System.currentTimeMillis())
            return output.toURI().toString() to decodeAudioCues(runCatching {File(output.path+".json").readText()}.getOrNull())
        }
        val statusPath = api.startSpeechPlayback(verses,version)
        repeat(315) {
            currentCoroutineContext().ensureActive()
            val status=api.speechPlaybackStatus(statusPath)
            if(status.complete) {
                save(status,output,directory)
                return output.toURI().toString() to status.cues.orEmpty().map {AudioCue(it.verseStart,it.verseEnd,it.start,it.end)}
            }
            if(status.ready) {
                if(!downloads.containsKey(key)) {
                    downloads[key]=downloadScope.launch {
                        try {
                            repeat(210) {
                                delay(3000)
                                val next=api.speechPlaybackStatus(statusPath)
                                if(next.complete) {save(next,output,directory);return@launch}
                            }
                        } catch(_:Exception) { /* Only complete chapters enter the offline cache. */ }
                        finally {downloads.remove(key)}
                    }
                }
                return api.playbackUrl(status.playlistPath) to emptyList()
            }
            delay(2000)
        }
        throw IllegalStateException("Speech preparation timed out")
    }
    private suspend fun save(status:com.nexussoft.verbum.clients.api.SpeechPlaybackStatus,output:File,directory:File) = withContext(Dispatchers.IO) {
        val audio=api.speechPlaybackData(status.audioPath)
        check(audio.isNotEmpty() && audio.size <= 64*1024*1024)
        val temporary=File(directory,"${UUID.randomUUID()}.partial.mp3")
        temporary.writeBytes(audio)
        check(temporary.renameTo(output))
        val cues=status.cues.orEmpty().map {AudioCue(it.verseStart,it.verseEnd,it.start,it.end)}
        File(output.path+".json").writeText(encodeAudioCues(cues))
        // Newest first: at most 40 chapters (MP3 at 128 kbit/s is ~1 MB/minute; Psalm 119 is ~13 MB).
        var bytes = 0L
        directory.listFiles().orEmpty().filter { it.extension == "mp3" && !it.name.endsWith(".partial.mp3") }
            .sortedByDescending { it.lastModified() }.forEachIndexed { index, file ->
                bytes += file.length()
                if (file != output && (index >= 40 || bytes > 150_000_000L)) {file.delete();File(file.path+".json").delete()}
            }
    }
}
