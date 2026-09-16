package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.AudioPlayerEvent
import com.nexussoft.verbum.clients.helloao.ScriptureAudioClient
import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.feature.scripture.AudioPlayerFeature.Action
import com.nexussoft.verbum.feature.scripture.AudioPlayerFeature.DelegateAction
import com.nexussoft.verbum.feature.scripture.AudioPlayerFeature.State
import com.nexussoft.verbum.models.AudioNarrator
import com.nexussoft.verbum.models.ChapterAudio
import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioPlayerFeatureTest {
    private val john3 = PassageReference("John", 3)
    private val david = AudioNarrator("david", "David", "https://example.test/JHN/3/david.mp3", null)
    private val hays = AudioNarrator("hays", "Hays", "https://example.test/JHN/3/hays.mp3", null)
    private fun audio(reference: PassageReference) = ChapterAudio("BSB", "Berean Standard Bible", reference, listOf(david, hays))

    @Test
    fun playLoadsTheChapterAndStartsTheFirstNarrator() = runTest {
        val player = FakePlayer()
        val store = TestStore(State(), AudioPlayerFeature.reducer(ScriptureAudioClient { b, c -> audio(PassageReference(b, c)) }, player))
        store.send(Action.Play(PassageReference("John", 3, 16..16))) { State(reference = john3, isLoading = true) }
        store.receive(Action.AudioLoaded(audio(john3))) { it.copy(audio = audio(john3), narrator = david) }
        assertEquals(listOf("stop", "load david.mp3 [Berean Standard Bible · David]", "rate 1.0", "play"), player.calls)
        store.send(Action.Event(AudioPlayerEvent.Ready(1337.0))) { it.copy(duration = 1337.0, isLoading = false) }
        store.send(Action.Event(AudioPlayerEvent.Playing(true))) { it.copy(isPlaying = true) }
        store.send(Action.Event(AudioPlayerEvent.Time(12.0))) { it.copy(currentTime = 12.0) }
        assertTrue(store.state.isPlaying(PassageReference("John", 3, 1..2)))
        store.send(Action.StopTapped) { State() }
        assertEquals("stop", player.calls.last())
        store.finish()
    }

    @Test
    fun noRecordingIsAStateNotACrash() = runTest {
        val store = TestStore(State(), AudioPlayerFeature.reducer(ScriptureAudioClient { _, _ -> null }, FakePlayer()))
        store.send(Action.Play(john3)) { State(reference = john3, isLoading = true) }
        store.receive(Action.AudioLoaded(null)) { it.copy(isLoading = false, failed = true) }
        store.send(Action.TogglePlayPause)
        store.finish()
    }

    @Test
    fun controlsDriveThePlayer() = runTest {
        val player = FakePlayer()
        val state = State(reference = john3, audio = audio(john3), narrator = david, duration = 100.0, currentTime = 50.0, isPlaying = true)
        val store = TestStore(state, AudioPlayerFeature.reducer(unimplementedAudio, player))
        store.send(Action.TogglePlayPause)
        store.send(Action.SkipForward)
        store.receive(Action.Seek(65.0)) { it.copy(currentTime = 65.0) }
        store.send(Action.SkipBackward)
        store.receive(Action.Seek(50.0)) { it.copy(currentTime = 50.0) }
        store.send(Action.Seek(98.0)) { it.copy(currentTime = 98.0) }
        store.send(Action.SkipForward)
        store.receive(Action.Seek(100.0)) { it.copy(currentTime = 100.0) }
        store.send(Action.RateTapped) { it.copy(rate = 1.25f) }
        store.send(Action.RateTapped) { it.copy(rate = 1.5f) }
        store.send(Action.RateTapped) { it.copy(rate = 0.8f) }
        store.send(Action.RateTapped) { it.copy(rate = 1f) }
        assertEquals(listOf("pause", "seek 65", "seek 50", "seek 98", "seek 100", "rate 1.25", "rate 1.5", "rate 0.8", "rate 1.0"), player.calls)
        store.finish()
    }

    @Test
    fun endOfChapterContinuesIntoTheNext() = runTest {
        val store = TestStore(State(reference = john3, audio = audio(john3), narrator = david, isPlaying = true), AudioPlayerFeature.reducer(ScriptureAudioClient { _, _ -> null }, FakePlayer()))
        store.send(Action.Event(AudioPlayerEvent.Ended)) { it.copy(isPlaying = false) }
        store.receive(Action.Play(PassageReference("John", 4))) { State(reference = PassageReference("John", 4), narrator = david, isLoading = true) }
        store.receive(Action.AudioLoaded(null)) { it.copy(isLoading = false, failed = true) }
        store.finish()
    }

    @Test
    fun switchingNarratorRestartsWithTheSameChapter() = runTest {
        val player = FakePlayer()
        val store = TestStore(State(reference = john3, audio = audio(john3), narrator = david, currentTime = 40.0), AudioPlayerFeature.reducer(unimplementedAudio, player))
        store.send(Action.NarratorSelected(hays)) { it.copy(narrator = hays, currentTime = 0.0, isLoading = true) }
        store.send(Action.NarratorSelected(hays))
        assertEquals(listOf("load hays.mp3 [Berean Standard Bible · Hays]", "rate 1.0", "play"), player.calls)
        store.finish()
    }

    @Test
    fun miniPlayerTapAsksTheShellToOpenTheChapter() = runTest {
        val store = TestStore(State(reference = john3), AudioPlayerFeature.reducer(unimplementedAudio, FakePlayer()))
        store.send(Action.ChapterTapped)
        store.receive(Action.Delegate(DelegateAction.OpenChapter(john3)))
        store.finish()
    }
}

class ListenIntegrationTest {
    @Test
    fun readerListenStartsTheGlobalPlayerAndTogglesWhenAlreadyOn() = runTest {
        val preferences = com.nexussoft.verbum.clients.InMemoryPreferencesClient()
        val deps = AppFeature.Dependencies(
            bibleClient = StubBibleClient(), preferences = preferences, searchClient = unimplementedSearch, graphClient = StubGraphClient(),
            audioClient = ScriptureAudioClient { _, _ -> null }, player = FakePlayer(), language = { com.nexussoft.verbum.models.BookLanguage.ENGLISH }, searchDebounceMs = 0,
        )
        val store = TestStore(AppFeature.State(), AppFeature.reducer(deps))
        val today = store.state.home.dailyVerse.reference
        val chapter = PassageReference(today.bookId, today.chapter)
        store.send(AppFeature.Action.Home(HomeFeature.Action.DailyVerse(DailyVerseFeature.Action.OpenTapped)))
        store.receive(AppFeature.Action.Home(HomeFeature.Action.DailyVerse(DailyVerseFeature.Action.Delegate(DailyVerseFeature.DelegateAction.OpenPassage(today)))))
        store.receive(AppFeature.Action.Home(HomeFeature.Action.Delegate(HomeFeature.DelegateAction.OpenPassage(today)))) {
            it.copy(homePath = listOf(AppFeature.Destination.Reader(ScriptureFeature.State.initial(today))))
        }
        store.send(AppFeature.Action.HomePath(0, AppFeature.DestinationAction.Reader(ScriptureFeature.Action.Reader(ChapterReaderFeature.Action.ListenTapped))))
        store.receive(AppFeature.Action.HomePath(0, AppFeature.DestinationAction.Reader(ScriptureFeature.Action.Reader(ChapterReaderFeature.Action.Delegate(ChapterReaderFeature.DelegateAction.Listen(chapter))))))
        store.receive(AppFeature.Action.HomePath(0, AppFeature.DestinationAction.Reader(ScriptureFeature.Action.Delegate(ScriptureFeature.DelegateAction.Listen(chapter)))))
        store.receive(AppFeature.Action.Audio(AudioPlayerFeature.Action.Play(chapter))) { it.copy(audio = AudioPlayerFeature.State(reference = chapter, isLoading = true)) }
        store.receive(AppFeature.Action.Audio(AudioPlayerFeature.Action.AudioLoaded(null))) { it.copy(audio = it.audio.copy(isLoading = false, failed = true)) }
        // Same chapter again: toggles instead of reloading.
        store.send(AppFeature.Action.HomePath(0, AppFeature.DestinationAction.Reader(ScriptureFeature.Action.Reader(ChapterReaderFeature.Action.ListenTapped))))
        store.receive(AppFeature.Action.HomePath(0, AppFeature.DestinationAction.Reader(ScriptureFeature.Action.Reader(ChapterReaderFeature.Action.Delegate(ChapterReaderFeature.DelegateAction.Listen(chapter))))))
        store.receive(AppFeature.Action.HomePath(0, AppFeature.DestinationAction.Reader(ScriptureFeature.Action.Delegate(ScriptureFeature.DelegateAction.Listen(chapter)))))
        store.receive(AppFeature.Action.Audio(AudioPlayerFeature.Action.TogglePlayPause))
        store.finish()
    }
}
