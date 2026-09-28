package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.*
import com.nexussoft.verbum.clients.helloao.ScriptureAudioClient
import com.nexussoft.verbum.common.arch.Store
import com.nexussoft.verbum.feature.scripture.AudioPlayerFeature.Action
import com.nexussoft.verbum.feature.scripture.AudioPlayerFeature.State
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Uses the production runtime so delayed effects and cancellation behave as on a device. */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlayerFeatureTest {
    private val chapter = PassageReference("John", 3)
    private val narrator = AudioNarrator("voice", "Voice", "https://example.test/chapter.mp3", null)
    private val audio = ChapterAudio("BSB", "Bible", chapter, listOf(narrator))
    private class Player : AudioPlayerClient {
        val calls = mutableListOf<String>()
        override val events = MutableSharedFlow<AudioPlayerEvent>(extraBufferCapacity = 16)
        override suspend fun load(url: String, nowPlaying: NowPlayingInfo) { calls += "load" }
        override suspend fun play() { calls += "play" }
        override suspend fun pause() { calls += "pause" }
        override suspend fun seek(seconds: Double) { calls += "seek:$seconds" }
        override suspend fun setRate(rate: Float) { calls += "rate:$rate" }
        override suspend fun stop() { calls += "stop" }
    }
    @Test fun finiteExcerptContinuesSameChapterWithoutAnotherGeneration()=runTest {
        val player=Player()
        val excerpt=audio.copy(narrators=listOf(narrator.copy(playbackStatusPath="/existing/status")))
        val client=object:ScriptureAudioClient {
            override suspend fun chapterAudio(bookId:BookId,chapter:Int):ChapterAudio?=error("Must not request a new generation")
            override suspend fun continueAudio(audio:ChapterAudio,after:Double):ChapterAudio {
                assertEquals(chapter,audio.reference);assertEquals(30.0,after)
                return this@AudioPlayerFeatureTest.audio
            }
        }
        val store=Store(State(reference=chapter,audio=excerpt,narrator=excerpt.narrators.first(),duration=30.0,currentTime=29.0,isPlaying=true),AudioPlayerFeature.reducer(client,player),backgroundScope)
        store.send(Action.Event(AudioPlayerEvent.Ended));runCurrent()
        assertEquals(chapter,store.state.value.reference)
        player.events.emit(AudioPlayerEvent.Ready(100.0));runCurrent()
        assertTrue(player.calls.contains("seek:30.0"))
        assertNull(store.state.value.narrator?.playbackStatusPath)
    }

    @Test fun preparesThenLoadsAndPlays() = runTest {
        val player = Player()
        val store = Store(State(), AudioPlayerFeature.reducer(ScriptureAudioClient { _, _ -> audio }, player), backgroundScope)
        store.send(Action.Play(chapter)); runCurrent()
        assertEquals(audio, store.state.value.audio)
        assertTrue(store.state.value.isLoading)
        player.events.emit(AudioPlayerEvent.Ready(100.0)); player.events.emit(AudioPlayerEvent.Playing(true)); runCurrent()
        assertTrue(store.state.value.isPlaying)
        assertFalse(store.state.value.isLoading)
        advanceTimeBy(46_000); runCurrent()
        assertFalse(store.state.value.failed)
    }
    @Test fun retryPreservesPositionAndSeeksOnlyAfterReady() = runTest {
        val player = Player(); var requests = 0
        val store = Store(State(), AudioPlayerFeature.reducer(ScriptureAudioClient { _, _ -> requests++; audio }, player), backgroundScope)
        store.send(Action.Play(chapter)); runCurrent()
        player.events.emit(AudioPlayerEvent.Ready(100.0)); player.events.emit(AudioPlayerEvent.Time(37.0)); player.events.emit(AudioPlayerEvent.Failed); runCurrent()
        assertTrue(store.state.value.failed)
        assertEquals(37.0, store.state.value.currentTime)
        val old = store.state.value.revision
        player.calls.clear()
        store.send(Action.RetryTapped); runCurrent()
        assertFalse(player.calls.contains("play"))
        assertEquals(2, requests)
        store.send(Action.SessionEvent(old, AudioPlayerEvent.Ended))
        assertEquals(chapter, store.state.value.reference)
        player.events.emit(AudioPlayerEvent.Ready(100.0)); runCurrent()
        assertTrue(player.calls.contains("seek:37.0"))
        assertTrue(player.calls.indexOf("seek:37.0") < player.calls.indexOf("play"))
    }
    @Test fun transientBufferingRecoversWithoutNewGeneration() = runTest {
        val player = Player(); var requests = 0
        val store = Store(State(), AudioPlayerFeature.reducer(ScriptureAudioClient { _, _ -> requests++; audio }, player), backgroundScope)
        store.send(Action.Play(chapter)); runCurrent()
        player.events.emit(AudioPlayerEvent.Ready(100.0)); player.events.emit(AudioPlayerEvent.Buffering(true)); runCurrent()
        assertTrue(store.state.value.isBuffering)
        advanceTimeBy(10_000)
        player.events.emit(AudioPlayerEvent.Buffering(false)); player.events.emit(AudioPlayerEvent.Playing(true)); runCurrent()
        advanceTimeBy(46_000); runCurrent()
        assertFalse(store.state.value.failed)
        assertEquals(1, requests)
    }
    @Test fun sustainedStallOffersRetryWithoutNewGeneration() = runTest {
        val player = Player(); var requests = 0
        val store = Store(State(), AudioPlayerFeature.reducer(ScriptureAudioClient { _, _ -> requests++; audio }, player), backgroundScope)
        store.send(Action.Play(chapter)); runCurrent()
        player.events.emit(AudioPlayerEvent.Ready(100.0)); player.events.emit(AudioPlayerEvent.Time(25.0)); player.events.emit(AudioPlayerEvent.Buffering(true)); runCurrent()
        advanceTimeBy(45_001); runCurrent()
        assertTrue(store.state.value.failed)
        assertEquals(AudioPlayerFeature.Failure.PLAYBACK, store.state.value.failure)
        assertEquals(25.0, store.state.value.currentTime)
        assertEquals(1, requests)
    }
    @Test fun oldSessionsCannotChangeNewChapter() = runTest {
        val store = Store(State(), AudioPlayerFeature.reducer(ScriptureAudioClient { b, c -> audio.copy(reference = PassageReference(b, c)) }, Player()), backgroundScope)
        store.send(Action.Play(chapter)); runCurrent()
        val old = store.state.value.revision
        store.send(Action.StopTapped); runCurrent()
        store.send(Action.Play(PassageReference("Ps", 55))); runCurrent()
        val current = store.state.value
        store.send(Action.Loaded(old, null))
        store.send(Action.PreparationFailed(old, AudioPlayerFeature.Failure.PREPARATION))
        store.send(Action.SessionEvent(old, AudioPlayerEvent.Failed))
        assertEquals(current, store.state.value)
    }
    @Test fun completionChainsAcrossBooksButFailureDoesNot() = runTest {
        val player = Player()
        val store = Store(State(), AudioPlayerFeature.reducer(ScriptureAudioClient { b, c -> audio.copy(reference = PassageReference(b, c)) }, player), backgroundScope)
        store.send(Action.Play(PassageReference("Gen", 50))); runCurrent()
        player.events.emit(AudioPlayerEvent.Ended); runCurrent()
        assertEquals(PassageReference("Exod", 1), store.state.value.reference)
        player.events.emit(AudioPlayerEvent.Failed); player.events.emit(AudioPlayerEvent.Ended); runCurrent()
        assertEquals(PassageReference("Exod", 1), store.state.value.reference)
    }
    @Test fun missingRecordingAndPreparationFailureAreDifferent() = runTest {
        val missing = Store(State(), AudioPlayerFeature.reducer(ScriptureAudioClient { _, _ -> null }, Player()), backgroundScope)
        missing.send(Action.Play(chapter)); runCurrent()
        assertEquals(AudioPlayerFeature.Failure.UNAVAILABLE, missing.state.value.failure)
        val failed = Store(State(), AudioPlayerFeature.reducer(ScriptureAudioClient { _, _ -> error("offline") }, Player()), backgroundScope)
        failed.send(Action.Play(chapter)); runCurrent()
        assertEquals(AudioPlayerFeature.Failure.PREPARATION, failed.state.value.failure)
    }
    @Test fun controlsClampSeekAndPauseBuffering() = runTest {
        val player = Player()
        val store = Store(State(reference = chapter, audio = audio, narrator = narrator, duration = 100.0, currentTime = 50.0, isBuffering = true), AudioPlayerFeature.reducer(ScriptureAudioClient { _, _ -> audio }, player), backgroundScope)
        store.send(Action.TogglePlayPause); store.send(Action.Seek(-30.0)); store.send(Action.Seek(300.0)); store.send(Action.Seek(Double.NaN)); store.send(Action.RateTapped); runCurrent()
        assertEquals(100.0, store.state.value.currentTime)
        assertTrue(player.calls.containsAll(listOf("pause", "seek:0.0", "seek:100.0", "rate:1.25")))
    }
    @Test fun readerListenRetriesSameChapterAfterFailure() = runTest {
        val player = Player()
        var requests = 0
        val deps = AppFeature.Dependencies(
            bibleClient = StubBibleClient(), preferences = InMemoryPreferencesClient(),
            searchClient = unimplementedSearch, graphClient = StubGraphClient(),
            audioClient = ScriptureAudioClient { _, _ -> requests++; null }, player = player,
        )
        val state = AppFeature.State(homePath = listOf(AppFeature.Destination.Reader(ScriptureFeature.State.initial(chapter))))
        val store = Store(state, AppFeature.reducer(deps), backgroundScope)
        val listen = AppFeature.Action.HomePath(0, AppFeature.DestinationAction.Reader(
            ScriptureFeature.Action.Delegate(ScriptureFeature.DelegateAction.Listen(chapter))))
        store.send(listen); runCurrent()
        assertTrue(store.state.value.audio.failed)
        store.send(listen); runCurrent()
        assertEquals(2, requests)
        assertEquals(chapter, store.state.value.audio.reference)
    }

}
