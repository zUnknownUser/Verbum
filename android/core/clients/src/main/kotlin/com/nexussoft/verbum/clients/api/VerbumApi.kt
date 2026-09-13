package com.nexussoft.verbum.clients.api

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

data class HttpRequest(val method: String, val url: String, val body: String? = null)
data class HttpResponse(val status: Int, val body: String)

/** Plain `HttpURLConnection` with short timeouts: the API answers from a database, and the reader must not hang on a dead server. */
object UrlConnectionHttpTransport : HttpTransport {
    override suspend fun send(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
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

/**
 * Why a call failed, in the terms the features switch on (§52). The server's `message` is
 * never carried: it is for logs, not users. Twin of iOS `VerbumAPIError`.
 */
sealed class VerbumApiException : Exception() {
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
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

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
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        /** Mirrors the server's `Cache-Control: public, max-age=3600` on editorial routes. */
        const val FRESH_FOR_MS = 3_600_000L
        const val PRODUCTION = "https://api.verbum.app"
        /** The Android emulator's route to the host machine. */
        const val LOCAL = "http://10.0.2.2:8080"

        private val json = Json { ignoreUnknownKeys = true }
    }

    // ---- entities

    /** `GET /v1/entities/{id}` (§9). */
    suspend fun entityDetail(id: EntityId): EntityDetail =
        get("/v1/entities/${encode(id)}", WireEntityDetail.serializer()).toModel()

    /** `GET /v1/entities?type=` (§7). Passage nodes are never listed. */
    suspend fun entities(type: BibleEntityType, language: BookLanguage = BookLanguage.current): List<BibleEntity> =
        get("/v1/entities", WireEntities.serializer(), "type" to type.wireValue, "lang" to language.tag).entities.map { it.toModel() }

    // ---- graph

    /** `GET /v1/entities/{id}/graph?limit=` (§8, §44). One hop, never the whole graph. */
    suspend fun graph(id: EntityId, limit: Int): GraphSnapshot =
        get("/v1/entities/${encode(id)}/graph", WireGraphSnapshot.serializer(), "limit" to limit.coerceIn(1, 48).toString()).toModel()

    // ---- context

    /**
     * `GET /v1/passages/{Book.Chapter}/context` (§10). Verses are ignored: context is per chapter.
     * Missing coverage is `null`, never invented (§3.5).
     */
    suspend fun context(reference: PassageReference): PassageContext? = try {
        get("/v1/passages/${reference.bookId}.${reference.chapter}/context", WirePassageContext.serializer()).toModel()
    } catch (e: VerbumApiException.Problem) {
        if (e.code == ProblemCode.CONTENT_UNAVAILABLE) null else throw e
    }

    // ---- timeline

    data class Timeline(val events: List<TimelineEvent>, val entityNames: Map<EntityId, String>)

    /** `GET /v1/timeline?entity=` (§4.2). Chronological, unknown dates last. */
    suspend fun timeline(entity: EntityId? = null): Timeline {
        val wire = if (entity == null) get("/v1/timeline", WireTimeline.serializer())
        else get("/v1/timeline", WireTimeline.serializer(), "entity" to entity)
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
    suspend fun ask(question: String): ScriptureAnswer =
        post("/v1/ask", json.encodeToString(WireAskRequest.serializer(), WireAskRequest(question)), WireAskResponse.serializer()).toModel()

    // ---- plumbing

    /**
     * `GET path?query`, decoded. Cache-first while fresh; on a network failure the last good
     * answer is returned even if stale, and only when there is none does the call fail.
     */
    internal suspend fun <T> get(path: String, strategy: DeserializationStrategy<T>, vararg query: Pair<String, String>): T {
        val url = url(path, query.toList())
        cache.read(url)?.let { hit -> if (now() - hit.storedAt < FRESH_FOR_MS) return decode(hit.body, strategy) }
        val body = try {
            send(HttpRequest("GET", url))
        } catch (e: VerbumApiException.NetworkUnavailable) {
            val stale = cache.read(url) ?: throw e
            return decode(stale.body, strategy)
        }
        val value = decode(body, strategy)
        cache.write(url, body, now())
        return value
    }

    /** `POST path` with a JSON body, decoded. Never cached. */
    internal suspend fun <T> post(path: String, body: String, strategy: DeserializationStrategy<T>): T =
        decode(send(HttpRequest("POST", url(path, emptyList()), body)), strategy)

    private fun url(path: String, query: List<Pair<String, String>>): String {
        val base = baseUrl.trimEnd('/') + path
        if (query.isEmpty()) return base
        return base + "?" + query.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8").replace("+", "%20") }
    }

    private fun encode(segment: String) = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    /** One place that turns transport outcomes into [VerbumApiException] (§52). */
    private suspend fun send(request: HttpRequest): String {
        val response = try {
            transport.send(request)
        } catch (e: VerbumApiException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw VerbumApiException.NetworkUnavailable
        }
        if (response.status !in 200..299) {
            val problem = runCatching { json.decodeFromString(WireProblem.serializer(), response.body) }.getOrNull()
            throw VerbumApiException.Problem(ProblemCode.of(problem?.code), response.status)
        }
        return response.body
    }

    private fun <T> decode(body: String, strategy: DeserializationStrategy<T>): T = try {
        json.decodeFromString(strategy, body)
    } catch (e: SerializationException) {
        throw VerbumApiException.MalformedResponse
    } catch (e: IllegalArgumentException) {
        throw VerbumApiException.MalformedResponse
    }
}
