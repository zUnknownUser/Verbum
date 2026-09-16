package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.AudioPlayerClient
import com.nexussoft.verbum.clients.AudioPlayerEvent
import com.nexussoft.verbum.clients.BibleClient
import com.nexussoft.verbum.clients.NowPlayingInfo
import com.nexussoft.verbum.clients.helloao.ScriptureAudioClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import com.nexussoft.verbum.clients.GraphClient
import com.nexussoft.verbum.clients.SearchClient
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.BiblePassage
import com.nexussoft.verbum.models.EntityDetail
import com.nexussoft.verbum.models.EntityId
import com.nexussoft.verbum.models.GraphSnapshot
import com.nexussoft.verbum.models.BookId
import com.nexussoft.verbum.models.PassageReference

// Fixture verses: placeholder text, not Scripture.
internal fun verses(bookId: BookId, chapter: Int, count: Int): List<BiblePassage> =
    (1..count).map { BiblePassage("t:$bookId.$chapter.$it", "t", bookId, chapter, it, it, "v$it") }

/** Unimplemented by default, like TCA's `testValue`: an un-stubbed call fails the test. */
internal class StubBibleClient(
    var chapterStub: suspend (BookId, Int) -> List<BiblePassage> = { b, c -> throw AssertionError("BibleClient.chapter($b, $c) was called but not stubbed") },
    var passageStub: suspend (PassageReference) -> BiblePassage = { throw AssertionError("BibleClient.passage($it) was called but not stubbed") },
) : BibleClient {
    override suspend fun chapter(bookId: BookId, chapter: Int) = chapterStub(bookId, chapter)
    override suspend fun passage(reference: PassageReference) = passageStub(reference)
}

internal val unimplementedSearch = SearchClient { throw AssertionError("SearchClient.search was called but not stubbed: $it") }

internal class StubGraphClient(
    var detailStub: suspend (EntityId) -> EntityDetail = { throw AssertionError("GraphClient.detail($it) was called but not stubbed") },
    var neighborsStub: suspend (EntityId, Int) -> GraphSnapshot = { id, _ -> throw AssertionError("GraphClient.neighbors($id) was called but not stubbed") },
    var entitiesStub: suspend (BibleEntityType) -> List<BibleEntity> = { throw AssertionError("GraphClient.entities($it) was called but not stubbed") },
) : GraphClient {
    override suspend fun entities(type: BibleEntityType) = entitiesStub(type)
    override suspend fun entity(id: EntityId): BibleEntity = throw AssertionError("GraphClient.entity($id) was called but not stubbed")
    override suspend fun neighbors(id: EntityId, limit: Int) = neighborsStub(id, limit)
    override suspend fun detail(id: EntityId) = detailStub(id)
}

internal val unimplementedAudio = ScriptureAudioClient { b, c -> throw AssertionError("ScriptureAudioClient.chapterAudio($b, $c) was called but not stubbed") }

/** A scripted player: records calls, lets the test feed events. */
internal class FakePlayer : AudioPlayerClient {
    val calls = mutableListOf<String>()
    val eventsFlow = MutableSharedFlow<AudioPlayerEvent>(extraBufferCapacity = 16)
    override suspend fun load(url: String, nowPlaying: NowPlayingInfo) { calls += "load ${url.substringAfterLast('/')} [${nowPlaying.subtitle}]" }
    override suspend fun play() { calls += "play" }
    override suspend fun pause() { calls += "pause" }
    override suspend fun seek(seconds: Double) { calls += "seek ${seconds.toInt()}" }
    override suspend fun setRate(rate: Float) { calls += "rate $rate" }
    override suspend fun stop() { calls += "stop" }
    override val events: Flow<AudioPlayerEvent> get() = emptyFlow()
}
