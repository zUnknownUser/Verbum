package com.nexussoft.verbum.clients.helloao

import com.nexussoft.verbum.models.BookId
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.ChapterAudio
import com.nexussoft.verbum.models.AudioNarrator
import com.nexussoft.verbum.models.PassageReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Which recordings exist for a chapter. */
fun interface ScriptureAudioClient {
    suspend fun chapterAudio(bookId: BookId, chapter: Int): ChapterAudio?
}

/**
 * Audio follows the translation being read when it has any; otherwise the English BSB
 * recordings are offered, labelled as such. Mirrors iOS `ScriptureAudioClient.helloAO`.
 */
class HelloAOScriptureAudioClient(language: BookLanguage, private val transport: Transport = UrlConnectionTransport) : ScriptureAudioClient {
    private val candidates: List<String> = listOf(HelloAOTranslation.id(language)) + RECORDED_FALLBACKS.filter { it != HelloAOTranslation.id(language) }

    override suspend fun chapterAudio(bookId: BookId, chapter: Int): ChapterAudio? {
        val usfm = HelloAOBooks.usfmByOsis[bookId] ?: return null
        for (translation in candidates) {
            val body = runCatching { transport.get("${HelloAOBibleClient.BASE_URL}/$translation/$usfm/$chapter.json") }.getOrNull() ?: continue
            val audio = runCatching { parse(body, bookId, chapter) }.getOrNull() ?: continue
            if (audio.narrators.isNotEmpty()) return audio
        }
        return null
    }

    companion object {
        val RECORDED_FALLBACKS = listOf("BSB")
        private val json = Json { ignoreUnknownKeys = true }

        internal fun parse(body: String, bookId: BookId, chapter: Int): ChapterAudio {
            val root = json.parseToJsonElement(body).jsonObject
            val translation = root.getValue("translation").jsonObject
            val links = root["thisChapterAudioLinks"]?.jsonObject ?: emptyMap()
            val timings = root["thisChapterAudioTimings"]?.jsonObject ?: emptyMap()
            val narrators = links.keys.sorted().map { id ->
                AudioNarrator(id, id.replaceFirstChar { it.uppercase() }, links.getValue(id).jsonPrimitive.content, timings[id]?.jsonPrimitive?.content)
            }
            return ChapterAudio(translation.getValue("id").jsonPrimitive.content, translation.getValue("name").jsonPrimitive.content, PassageReference(bookId, chapter), narrators)
        }
    }
}
