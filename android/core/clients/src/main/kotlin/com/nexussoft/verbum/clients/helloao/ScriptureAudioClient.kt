package com.nexussoft.verbum.clients.helloao

import com.nexussoft.verbum.models.BookId
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.ChapterAudio
import com.nexussoft.verbum.models.AudioNarrator
import com.nexussoft.verbum.models.AudioCue
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.coroutines.CancellationException
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
            if (audio.narrators.isNotEmpty()) {
                val narrators=audio.narrators.map {narrator->
                    val cues=try {
                        val uri=narrator.timingsPath?.let {java.net.URI(HelloAOBibleClient.BASE_URL+"/").resolve(it)}
                        if(uri?.scheme=="https" && uri.host=="bible.helloao.org") parseTimings(transport.get(uri.toString()),narrator,translation,usfm,chapter) else emptyList()
                    } catch(e:CancellationException) {throw e} catch(_:Exception) {emptyList()}
                    narrator.copy(cues=cues)
                }
                return audio.copy(narrators=narrators)
            }
        }
        return null
    }

    companion object {
        val RECORDED_FALLBACKS = listOf("BSB")
        private val json = Json { ignoreUnknownKeys = true }

        internal fun parseTimings(body:String,narrator:AudioNarrator,translation:String,book:String,chapter:Int):List<AudioCue> {
            val root=json.parseToJsonElement(body).jsonObject
            if(root["translationId"]?.jsonPrimitive?.content!=translation || root["bookId"]?.jsonPrimitive?.content!=book || root["chapterNumber"]?.jsonPrimitive?.int!=chapter || root["reader"]?.jsonPrimitive?.content!=narrator.id || root["audioLink"]?.jsonPrimitive?.content!=narrator.url) return emptyList()
            val starts=root.getValue("verses").jsonArray.map {it.jsonPrimitive.double}
            return AudioCue.validated(starts.mapIndexed {i,start->AudioCue(i+1,i+1,start,starts.getOrNull(i+1))})
        }

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
