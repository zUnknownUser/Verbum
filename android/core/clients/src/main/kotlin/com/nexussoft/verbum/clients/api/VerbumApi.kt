package com.nexussoft.verbum.clients.api

import com.nexussoft.verbum.models.UsageRestriction
import com.nexussoft.verbum.models.UsageStatus
import com.nexussoft.verbum.clients.RealtimeSession
import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.EntityDetail
import com.nexussoft.verbum.models.EntityId
import com.nexussoft.verbum.models.GraphSnapshot
import com.nexussoft.verbum.models.PassageContext
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.ScriptureAnswer
import com.nexussoft.verbum.models.SearchResponse
import com.nexussoft.verbum.models.TimelineEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** One HTTP exchange; injected so tests never touch the network. */
fun interface HttpTransport {
    suspend fun send(request: HttpRequest): HttpResponse
}

data class HttpRequest(val method: String, val url: String, val body: String? = null, val headers: Map<String, String> = emptyMap())
data class HttpResponse(val status: Int, val body: String)

/** One HTTP exchange whose successful response is raw bytes (audio), not JSON text. */
fun interface BinaryHttpTransport {
    suspend fun send(request: HttpRequest): BinaryHttpResponse
}

/** [bytes] is only meaningful when [status] is 2xx; a failed request's body is JSON text
 * (the [WireProblem] contract), carried in [problemBody] instead. Not a `data class`: a
 * `ByteArray` would give it reference-equality `equals`/`hashCode`, which is never used here. */
class BinaryHttpResponse(val status: Int, val bytes: ByteArray, val problemBody: String = "", val headers: Map<String,String> = emptyMap())

/** Plain `HttpURLConnection` with short timeouts: the API answers from a database, and the reader must not hang on a dead server. */
object UrlConnectionHttpTransport : HttpTransport {
    override suspend fun send(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.instanceFollowRedirects = false
            request.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            connection.connectTimeout = 10_000
            connection.readTimeout = if (request.method == "POST") 45_000 else 15_000
            connection.setRequestProperty("Accept", "application/json")
            request.body?.let {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { out -> out.write(it.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status < 400) connection.inputStream else connection.errorStream
            HttpResponse(status, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "")
        } finally {
            connection.disconnect()
        }
    }
}

/** Plain `HttpURLConnection` reading a binary body (audio), with the long read timeout chapter
 * speech generation needs — matches the server's generation window plus margin. */
object UrlConnectionBinaryHttpTransport : BinaryHttpTransport {
    override suspend fun send(request: HttpRequest): BinaryHttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.instanceFollowRedirects = false
            request.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            connection.connectTimeout = 10_000
            connection.readTimeout = 630_000
            request.body?.let {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { out -> out.write(it.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status < 400) {
                BinaryHttpResponse(status, connection.inputStream.use { it.readBytes() }, headers=mapOf("X-Verbum-Audio-Cues" to (connection.getHeaderField("X-Verbum-Audio-Cues") ?: "")))
            } else {
                BinaryHttpResponse(status, ByteArray(0), connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "")
            }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Why a call failed, in the terms the features switch on (§52). The server's `message` is
 * never carried: it is for logs, not users. Twin of iOS `VerbumAPIError`.
 */
sealed class VerbumApiException : Exception() {
    data class Restricted(val restriction: UsageRestriction) : VerbumApiException()
    /** Could not reach the server and nothing is cached. */
    data object NetworkUnavailable : VerbumApiException() {
        private fun readResolve(): Any = NetworkUnavailable
    }
    /** The server answered with a `Problem` (§52 one error shape). */
    data class Problem(val code: ProblemCode, val status: Int) : VerbumApiException()
    /** The answer was not the contract's JSON. */
    data object MalformedResponse : VerbumApiException() {
        private fun readResolve(): Any = MalformedResponse
    }

    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int
}

/** `Problem.code` of `api/openapi.yaml`; [UNKNOWN] for a code this build does not know. */
enum class ProblemCode(val wire: String) {
    UNKNOWN_ENTITY("unknown_entity"),
    UNKNOWN_BOOK("unknown_book"),
    CONTENT_UNAVAILABLE("content_unavailable"),
    MALFORMED_REQUEST("malformed_request"),
    INTERNAL("internal"),
    REALTIME_UNAVAILABLE("realtime_unavailable"),
    ASK_UNAVAILABLE("ask_unavailable"),
    TTS_UNAVAILABLE("tts_unavailable"),
    TTS_RATE_LIMITED("tts_rate_limited"),
    TTS_TIMEOUT("tts_timeout"),
    TTS_FAILED("tts_failed"),
    UNAUTHENTICATED("unauthenticated"),
    AUTH_UNAVAILABLE("auth_unavailable"),
    RATE_LIMITED("rate_limited"),
    QUOTA_EXCEEDED("quota_exceeded"), BUDGET_EXHAUSTED("budget_exhausted"), PLAN_REQUIRED("plan_required"), USAGE_UNAVAILABLE("usage_unavailable"), REQUEST_IN_PROGRESS("request_in_progress"), IDEMPOTENCY_CONFLICT("idempotency_conflict"),
    UNKNOWN("");

    companion object {
        fun of(wire: String?): ProblemCode = entries.firstOrNull { it.wire == wire && it != UNKNOWN } ?: UNKNOWN
    }
}

/**
 * Answers already received, as files in [directory] keyed by the request URL (null = memory only).
 * Same shape as `ChapterCache`: small, explicit, enough for §39 until a real local store is needed.
 */
class ResponseCache(private val directory: File?) {
    class Entry(val body: String, val storedAt: Long)

    private val memory = HashMap<String, Entry>()

    @Synchronized
    fun read(url: String): Entry? {
        val key = key(url)
        memory[key]?.let { return it }
        val file = file(key)?.takeIf { it.exists() } ?: return null
        return runCatching { Entry(file.readText(), file.lastModified()) }.getOrNull()?.also { memory[key] = it }
    }

    @Synchronized
    fun write(url: String, body: String, at: Long) {
        val key = key(url)
        memory[key] = Entry(body, at)
        val file = file(key) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(body)
            file.setLastModified(at)
        }
    }

    private fun key(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(("localized-v3:" + url).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun file(key: String) = directory?.let { File(it, "$key.json") }
}

/**
 * The Verbum backend (`api/openapi.yaml`, §45): one function per route, decoding the contract's
 * JSON into the app's models. The live `GraphClient`, `SearchClient`, `ContextClient`,
 * `TimelineClient` and `AskScriptureClient` are thin wrappers over this. Twin of iOS `VerbumAPI`.
 *
 * Editorial routes (`GET /v1/...`) are keyless and cacheable: every answer is kept on disk so
 * recent entities, context and the timeline stay readable offline (§39), and a fresh copy is
 * served without a request for as long as the server says it may be (`Cache-Control: max-age`).
 * `POST` routes (Ask, realtime) are never cached (§47: minimise retention of questions).
 */
class VerbumApi(
    val baseUrl: String,
    private val transport: HttpTransport = UrlConnectionHttpTransport,
    private val cache: ResponseCache = ResponseCache(null),
    private val binaryTransport: BinaryHttpTransport = UrlConnectionBinaryHttpTransport,
    private val tokenProvider: suspend (createIfNeeded: Boolean) -> String? = { null },
    private val installationId: String = java.util.UUID.randomUUID().toString(),
    private val appCheckProvider: suspend () -> String? = { null },
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        /** Mirrors the server's `Cache-Control: public, max-age=3600` on editorial routes. */
        const val FRESH_FOR_MS = 3_600_000L
        const val PRODUCTION = "https://api.vendlydigital.com.br"
        /** The Android emulator's route to the host machine. */
        const val LOCAL = "http://10.0.2.2:8080"

        private val json = Json { ignoreUnknownKeys = true }
    }

    // ---- entities

    /** `GET /v1/entities/{id}` (§9). */
    suspend fun entityDetail(id: EntityId, language: BookLanguage = BookLanguage.current): EntityDetail =
        get("/v1/entities/${encode(id)}", WireEntityDetail.serializer(), "lang" to language.tag).toModel()

    /** `GET /v1/entities?type=` (§7). Passage nodes are never listed. */
    suspend fun entities(type: BibleEntityType, language: BookLanguage = BookLanguage.current): List<BibleEntity> =
        get("/v1/entities", WireEntities.serializer(), "type" to type.wireValue, "lang" to language.tag).entities.map { it.toModel() }

    // ---- graph

    /** `GET /v1/entities/{id}/graph?limit=` (§8, §44). One hop, never the whole graph. */
    suspend fun graph(id: EntityId, limit: Int, language: BookLanguage = BookLanguage.current): GraphSnapshot =
        get("/v1/entities/${encode(id)}/graph", WireGraphSnapshot.serializer(), "limit" to limit.coerceIn(1, 48).toString(), "lang" to language.tag).toModel()

    // ---- context

    /**
     * `GET /v1/passages/{Book.Chapter}/context` (§10). An optional verse narrows related occurrences; context stays per chapter.
     * Missing coverage is `null`, never invented (§3.5).
     */
    suspend fun context(reference: PassageReference, language: BookLanguage = BookLanguage.current): PassageContext? = try {
        get("/v1/passages/${reference.bookId}.${reference.chapter}/context", WirePassageContext.serializer(), *listOfNotNull("lang" to language.tag, reference.verses?.let { "verse" to it.first.toString() }).toTypedArray()).toModel()
    } catch (e: VerbumApiException.Problem) {
        if (e.code == ProblemCode.CONTENT_UNAVAILABLE) null else throw e
    }

    // ---- timeline

    data class Timeline(val events: List<TimelineEvent>, val entityNames: Map<EntityId, String>)

    /** `GET /v1/timeline?entity=` (§4.2). Chronological, unknown dates last. */
    suspend fun timeline(entity: EntityId? = null, language: BookLanguage = BookLanguage.current): Timeline {
        val wire = if (entity == null) get("/v1/timeline", WireTimeline.serializer(), "lang" to language.tag)
        else get("/v1/timeline", WireTimeline.serializer(), "entity" to entity, "lang" to language.tag)
        return Timeline(wire.events.map { it.toModel() }, wire.entityNames)
    }

    // ---- search

    /** `GET /v1/search?q=` (§27–28). Books arrive as OSIS ids and are resolved from the canon. */
    suspend fun search(query: String, language: BookLanguage = BookLanguage.current): SearchResponse {
        val wire = get("/v1/search", WireSearchResponse.serializer(), "q" to query.take(200), "lang" to language.tag)
        return SearchResponse(
            query = wire.query,
            passages = wire.passages.map { it.toModel() },
            books = wire.books.mapNotNull { BibleBook.book(it.id) },
            entities = wire.entities.map { it.toModel() },
        )
    }

    // ---- daily

    data class DailyVerse(val date: String, val reference: PassageReference)

    /** `GET /v1/daily-verse?from=&days=` — references only; the text comes from the reader's translation (§14). */
    suspend fun dailyVerses(from: String, days: Int): List<DailyVerse> =
        get("/v1/daily-verse", WireDailyVerses.serializer(), "from" to from, "days" to days.coerceIn(1, 31).toString())
            .verses.map { DailyVerse(it.date, it.reference.toModel()) }

    // ---- ask

    /** `POST /v1/ask {"question"}` → the §30 contract. Never cached. */
    suspend fun ask(question: String, reference: PassageReference? = null): ScriptureAnswer =
        post("/v1/ask", json.encodeToString(WireAskRequest.serializer(), WireAskRequest(question, reference?.let { WirePassageReference(it.bookId,it.chapter,it.verses?.first,it.verses?.last) })), WireAskResponse.serializer()).toModel()

    // ---- realtime

    /** `POST /v1/realtime/session`. Never cached (the server says `no-store`). */
    suspend fun realtimeSession(): RealtimeSession =
        post("/v1/realtime/session", "{}", WireRealtimeSession.serializer()).let { RealtimeSession(it.clientSecret, it.expiresAt, it.model, it.relayPath?.let { path ->
            if(path != "/v1/realtime/connect") throw VerbumApiException.MalformedResponse
            url(path, emptyList()).replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        }, it.maxDurationSeconds) }

    // ---- speech

    /**
     * `POST /v1/tts`: one complete chapter MP3 (Google Cloud Chirp 3 HD by default), cached
     * server-side by exact text and settings. Generation can take minutes. Never cached
     * client-side by this call — the caller decides whether/where to keep the bytes.
     */
    suspend fun speechVersion(language: String): String {
        val version = get("/v1/tts/config", WireSpeechConfiguration.serializer(), "language" to language).version
        require(version.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid audio version" }
        return version
    }

    suspend fun synthesizeSpeech(text: String, language: String, revision: String? = null): ByteArray {
        val body = json.encodeToString(WireSpeechRequest.serializer(), WireSpeechRequest(text, language, revision))
        return sendBinary(HttpRequest("POST", url("/v1/tts", emptyList()), body))
    }

    suspend fun synthesizeChapterSpeech(verses:List<com.nexussoft.verbum.models.BiblePassage>,language:String,revision:String?):Pair<ByteArray,List<com.nexussoft.verbum.models.AudioCue>> {
        val body=json.encodeToString(WireTimedSpeech.serializer(),WireTimedSpeech(verses.first().bookId,verses.first().chapter,verses.first().translationId,verses.joinToString("\n") {it.text},language,revision,verses.map {WireSpeechVerse(it.verseStart,it.text)}))
        val response=sendBinaryResponse(HttpRequest("POST",url("/v1/tts",emptyList()),body))
        val header=response.headers.entries.firstOrNull {it.key.equals("X-Verbum-Audio-Cues",true)}?.value
        return response.bytes to decodeAudioCues(header)
    }

    suspend fun startSpeechPlayback(verses:List<com.nexussoft.verbum.models.BiblePassage>, revision:String?):String {
        val body=json.encodeToString(WireTimedSpeech.serializer(),WireTimedSpeech(verses.first().bookId,verses.first().chapter,verses.first().translationId,verses.joinToString("\n"){it.text},"pt-BR",revision,verses.map{WireSpeechVerse(it.verseStart,it.text)}))
        val path=post("/v1/tts/playback",body,SpeechPlaybackStart.serializer()).statusPath
        playbackUrl(path)
        return path
    }
    fun playbackUrl(path:String):String {
        if(!Regex("/v1/tts/playback/[a-f0-9]{64}/(status|index\\.m3u8|chapter\\.mp3)").matches(path)) throw VerbumApiException.MalformedResponse
        return url(path,emptyList())
    }
    suspend fun speechPlaybackStatus(path:String):SpeechPlaybackStatus =
        decode(send(HttpRequest("GET",playbackUrl(path))),SpeechPlaybackStatus.serializer())
    suspend fun speechPlaybackData(path:String):ByteArray = sendBinary(HttpRequest("GET",playbackUrl(path)))

    // ---- plumbing

    /**
     * `GET path?query`, decoded. Cache-first while fresh; on a network failure the last good
     * answer is returned even if stale, and only when there is none does the call fail.
     */
    internal suspend fun <T> get(path: String, strategy: DeserializationStrategy<T>, vararg query: Pair<String, String>): T = withContext(Dispatchers.IO) {
        val url = url(path, query.toList())
        val token = if (path == "/v1/search") {
            try { tokenProvider(false) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        } else null
        val cacheKey = url + if (path == "/v1/search") (if (token == null) "#lexical" else "#semantic") else ""
        cache.read(cacheKey)?.let { hit -> if (now() - hit.storedAt < FRESH_FOR_MS) return@withContext decode(hit.body, strategy) }
        val body = try {
            send(HttpRequest("GET", url, headers = token?.let { mapOf("Authorization" to "Bearer $it", "X-Verbum-Installation" to installationId) } ?: emptyMap()))
        } catch (e: VerbumApiException.NetworkUnavailable) {
            val stale = cache.read(cacheKey) ?: throw e
            return@withContext decode(stale.body, strategy)
        }
        val value = decode(body, strategy)
        cache.write(cacheKey, body, now())
        value
    }

    /** `POST path` with a JSON body, decoded. Never cached. */
    internal suspend fun <T> post(path: String, body: String, strategy: DeserializationStrategy<T>): T =
        decode(send(HttpRequest("POST", url(path, emptyList()), body, headers = mapOf("Accept-Language" to BookLanguage.current.tag))), strategy)

    private fun url(path: String, query: List<Pair<String, String>>): String {
        val base = baseUrl.trimEnd('/') + path
        if (query.isEmpty()) return base
        return base + "?" + query.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8").replace("+", "%20") }
    }

    private fun encode(segment: String) = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    /** One place that turns transport outcomes into [VerbumApiException] (§52). */
    private suspend fun send(request: HttpRequest): String {
        val response = try {
            transport.send(attest(authorize(request)))
        } catch (e: VerbumApiException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw VerbumApiException.NetworkUnavailable
        }
        if (response.status !in 200..299) {
            val problem = runCatching { json.decodeFromString(WireProblem.serializer(), response.body) }.getOrNull()
            if(problem?.code in setOf("quota_exceeded", "budget_exhausted", "plan_required", "usage_unavailable", "request_in_progress"))
                throw VerbumApiException.Restricted(UsageRestriction(problem!!.code, problem.retryAt))
            throw VerbumApiException.Problem(ProblemCode.of(problem?.code), response.status)
        }
        return response.body
    }

    /** [send]'s twin for a binary response (audio, not the JSON contract). */
    private suspend fun sendBinary(request: HttpRequest): ByteArray = sendBinaryResponse(request).bytes
    private suspend fun sendBinaryResponse(request: HttpRequest): BinaryHttpResponse {
        val response = try {
            binaryTransport.send(attest(authorize(request)))
        } catch (e: VerbumApiException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw VerbumApiException.NetworkUnavailable
        }
        if (response.status !in 200..299) {
            val problem = runCatching { json.decodeFromString(WireProblem.serializer(), response.problemBody) }.getOrNull()
            if(problem?.code in setOf("quota_exceeded", "budget_exhausted", "plan_required", "usage_unavailable", "request_in_progress"))
                throw VerbumApiException.Restricted(UsageRestriction(problem!!.code, problem.retryAt))
            throw VerbumApiException.Problem(ProblemCode.of(problem?.code), response.status)
        }
        return response
    }

    suspend fun usageStatus(): UsageStatus? {
        val token = tokenProvider(false) ?: return null
        return decode(send(HttpRequest("GET", url("/v1/me/usage", emptyList()), headers = mapOf("Authorization" to "Bearer $token", "X-Verbum-Installation" to installationId))), WireUsageStatus.serializer()).let { UsageStatus(it.plan,it.resetsAt,it.remaining,it.voiceSeconds,it.restricted) }
    }

    private suspend fun attest(request: HttpRequest): HttpRequest {
        if (!request.headers.containsKey("Authorization")) return request
        val token = appCheckProvider() ?: return request
        return request.copy(headers = request.headers + ("X-Firebase-AppCheck" to token))
    }

    private suspend fun authorize(request: HttpRequest): HttpRequest {
        if (request.method != "POST") return request
        val token = tokenProvider(true) ?: return request
        return request.copy(headers = request.headers + mapOf("Authorization" to "Bearer $token", "X-Verbum-Installation" to installationId, "Idempotency-Key" to java.util.UUID.randomUUID().toString()))
    }

    private fun <T> decode(body: String, strategy: DeserializationStrategy<T>): T = try {
        json.decodeFromString(strategy, body)
    } catch (e: SerializationException) {
        throw VerbumApiException.MalformedResponse
    } catch (e: IllegalArgumentException) {
        throw VerbumApiException.MalformedResponse
    }
}
