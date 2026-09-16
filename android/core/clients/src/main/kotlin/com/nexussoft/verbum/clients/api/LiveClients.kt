package com.nexussoft.verbum.clients.api

import com.nexussoft.verbum.clients.AskScriptureClient
import com.nexussoft.verbum.clients.AskScriptureException
import com.nexussoft.verbum.clients.ContextClient
import com.nexussoft.verbum.clients.RealtimeSession
import com.nexussoft.verbum.clients.RealtimeSessionClient
import com.nexussoft.verbum.clients.VoiceException
import com.nexussoft.verbum.clients.GraphClient
import com.nexussoft.verbum.clients.GraphClientException
import com.nexussoft.verbum.clients.SearchClient
import com.nexussoft.verbum.clients.TimelineClient
import com.nexussoft.verbum.common.BookMatcher
import com.nexussoft.verbum.common.PassageReferenceParser
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

// The live value of every content client: VerbumApi behind the interface the features already
// use, with the API's failures mapped to each client's own errors (§37, §52). Twins of the iOS
// `.live` values in `Clients+Live.swift`.

class LiveGraphClient(private val api: VerbumApi, private val language: () -> BookLanguage = { BookLanguage.current }) : GraphClient {
    override suspend fun entity(id: EntityId): BibleEntity = mapUnknown(id) { api.entityDetail(id, language()).entity }
    override suspend fun neighbors(id: EntityId, limit: Int): GraphSnapshot = mapUnknown(id) { api.graph(id, limit, language()) }
    override suspend fun detail(id: EntityId): EntityDetail = mapUnknown(id) { api.entityDetail(id, language()) }
    override suspend fun entities(type: BibleEntityType): List<BibleEntity> =
        if (type == BibleEntityType.PASSAGE) emptyList() else api.entities(type, language())

    /** `404 unknown_entity` is the client's own [GraphClientException.UnknownEntity]; anything else passes through. */
    private inline fun <T> mapUnknown(id: EntityId, body: () -> T): T = try {
        body()
    } catch (e: VerbumApiException.Problem) {
        if (e.code == ProblemCode.UNKNOWN_ENTITY) throw GraphClientException.UnknownEntity(id) else throw e
    }
}

/**
 * The server ranks Scripture hits and entities (§27–28); a reference the device can parse still
 * wins outright and books are matched locally, so a typed `Jn 3:16` opens instantly and the two
 * rankings agree.
 */
class LiveSearchClient(private val api: VerbumApi, private val language: () -> BookLanguage = { BookLanguage.current }) : SearchClient {
    override suspend fun search(query: String): SearchResponse {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return SearchResponse.empty(query)
        val local = LocalSearch.of(trimmed, language())
        val remote = api.search(trimmed, language())
        return SearchResponse(
            query = query,
            passages = local.passages + remote.passages.filter { it !in local.passages },
            books = if (local.passages.isEmpty()) local.books + remote.books.filter { it !in local.books } else emptyList(),
            entities = remote.entities,
        )
    }
}

/** What this device knows without a server: the parsed reference and the books whose name the query starts (§28). Never throws. */
object LocalSearch {
    fun of(query: String, language: BookLanguage): SearchResponse {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return SearchResponse.empty(query)
        val reference = PassageReferenceParser.parse(trimmed, language).referenceOrNull
        // A reference is the answer; listing its book beside it is noise.
        return SearchResponse(query, listOfNotNull(reference), if (reference == null) BookMatcher.books(trimmed) else emptyList(), emptyList())
    }
}

class LiveContextClient(private val api: VerbumApi) : ContextClient {
    override suspend fun chapter(reference: PassageReference): PassageContext? = api.context(reference)
}

class LiveTimelineClient(private val api: VerbumApi) : TimelineClient {
    override suspend fun events(): List<TimelineEvent> = api.timeline().events
    override suspend fun eventsFor(entityId: EntityId): List<TimelineEvent> = api.timeline(entityId).events
}

/** `POST /v1/ask`. 503 and a route the server does not have are both [AskScriptureException.Unavailable]; the page says so instead of failing. */
class LiveAskScriptureClient(private val api: VerbumApi) : AskScriptureClient {
    override suspend fun askAbout(question: String, reference: com.nexussoft.verbum.models.PassageReference): ScriptureAnswer =
        try { api.ask(question.trim().take(500),reference) }
        catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: Exception) { throw AskScriptureException.Failed }

    override suspend fun ask(question: String): ScriptureAnswer = try {
        api.ask(question.trim().take(500))
    } catch (e: VerbumApiException.Problem) {
        if (e.code == ProblemCode.ASK_UNAVAILABLE || e.code == ProblemCode.AUTH_UNAVAILABLE || e.status == 404 || e.status == 501) throw AskScriptureException.Unavailable
        throw AskScriptureException.Failed
    } catch (e: VerbumApiException.NetworkUnavailable) {
        throw AskScriptureException.NetworkUnavailable
    } catch (e: VerbumApiException.MalformedResponse) {
        throw AskScriptureException.Failed
    }
}

/** `POST /v1/realtime/session`. 503 (no key on the server) and a backend without the route are both [VoiceException.Unavailable]. */
class LiveRealtimeSessionClient(private val api: VerbumApi) : RealtimeSessionClient {
    override suspend fun create(): RealtimeSession = try {
        api.realtimeSession()
    } catch (e: VerbumApiException.Problem) {
        if (e.code == ProblemCode.REALTIME_UNAVAILABLE || e.code == ProblemCode.AUTH_UNAVAILABLE || e.status == 404 || e.status == 501) throw VoiceException.Unavailable
        throw VoiceException.Failed
    } catch (e: VerbumApiException.NetworkUnavailable) {
        throw VoiceException.NetworkUnavailable
    } catch (e: VerbumApiException.MalformedResponse) {
        throw VoiceException.Failed
    }
}
