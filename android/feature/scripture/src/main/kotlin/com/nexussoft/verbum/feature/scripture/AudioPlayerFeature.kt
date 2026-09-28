package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.AudioPlayerClient
import com.nexussoft.verbum.clients.AudioPlayerEvent
import com.nexussoft.verbum.clients.NowPlayingInfo
import com.nexussoft.verbum.clients.helloao.ScriptureAudioClient
import com.nexussoft.verbum.common.arch.Effect
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.runEffect
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.models.AudioCue
import com.nexussoft.verbum.models.AudioReadingPosition
import com.nexussoft.verbum.models.AudioNarrator
import com.nexussoft.verbum.models.ChapterAudio
import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import com.nexussoft.verbum.clients.api.VerbumApiException

/**
 * The one player in the app. Lives on `AppFeature`, so listening survives navigation;
 * the mini player renders it anywhere. Chapters chain. Twin of iOS `AudioPlayerFeature`.
 */
object AudioPlayerFeature {
    enum class Failure { UNAVAILABLE, PREPARATION, RESTRICTED, PLAYBACK }
    data class State(
        val reference: PassageReference? = null,
        val audio: ChapterAudio? = null,
        val narrator: AudioNarrator? = null,
        val isPlaying: Boolean = false,
        val isLoading: Boolean = false,
        val currentTime: Double = 0.0,
        val duration: Double = 0.0,
        val rate: Float = 1f,
        val failed: Boolean = false,
        val failure: Failure? = null,
        val isBuffering: Boolean = false,
        val revision: Int = 0,
        val resumeTime: Double = 0.0,
    ) {
        val readingPosition: AudioReadingPosition? get() {
            if(failed || isLoading || audio==null) return null
            val cue=AudioCue.active(narrator?.cues.orEmpty(),currentTime) ?: return null
            return AudioReadingPosition(audio.reference,audio.translationId,cue,isPlaying)
        }
        val isActive: Boolean get() = reference != null
        val progress: Float get() = if (duration > 0) (currentTime / duration).coerceAtMost(1.0).toFloat() else 0f
        fun isPlaying(reference: PassageReference) = isPlaying && this.reference?.bookId == reference.bookId && this.reference?.chapter == reference.chapter
    }

    sealed interface Action {
        data class Play(val reference: PassageReference) : Action
        data class AudioLoaded(val audio: ChapterAudio?) : Action
        data class Loaded(val revision: Int, val audio: ChapterAudio?) : Action
        data class PreparationFailed(val revision: Int, val reason: Failure) : Action
        data class SessionEvent(val revision: Int, val event: AudioPlayerEvent) : Action
        data class Stalled(val revision: Int) : Action
        data object RetryTapped : Action
        data object TogglePlayPause : Action
        data object SkipForward : Action
        data object SkipBackward : Action
        data class Seek(val seconds: Double) : Action
        data object RateTapped : Action
        data class NarratorSelected(val narrator: AudioNarrator) : Action
        data object StopTapped : Action
        data object ChapterTapped : Action
        data class Event(val event: AudioPlayerEvent) : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }

    sealed interface DelegateAction {
        data class OpenChapter(val reference: PassageReference) : DelegateAction
    }

    const val SKIP_SECONDS = 15.0
    val RATES = listOf(1f, 1.25f, 1.5f, 0.8f)

    private object LoadId
    private object PlayerId
    private object StallId

    fun reducer(audioClient: ScriptureAudioClient, player: AudioPlayerClient): Reducer<State, Action> = Reducer { state, incoming ->
        val action = when (incoming) {
            is Action.Loaded -> {
                if (incoming.revision != state.revision || state.reference == null) return@Reducer state.only()
                Action.AudioLoaded(incoming.audio)
            }
            is Action.SessionEvent -> {
                if (incoming.revision != state.revision || state.audio == null) return@Reducer state.only()
                Action.Event(incoming.event)
            }
            else -> incoming
        }
        when (action) {
            is Action.Play -> prepare(state, action.reference, 0.0, audioClient, player)
            Action.RetryTapped -> if (!state.failed || state.reference == null) state.only() else prepare(state, state.reference, state.currentTime, audioClient, player)
            is Action.Loaded -> state.only()
            is Action.PreparationFailed -> if (action.revision != state.revision || state.reference == null) state.only() else state.copy(isLoading = false, failed = true, failure = action.reason).only()
            is Action.SessionEvent -> state.only()
            is Action.Stalled -> if (action.revision != state.revision || state.audio == null || (!state.isBuffering && !state.isLoading)) state.only() else state.with(Effect.Send(Action.SessionEvent(action.revision, AudioPlayerEvent.Failed)))

            is Action.AudioLoaded -> {
                val audio = action.audio
                if (state.reference == null || (audio != null && audio.reference != state.reference)) state.only()
                else if (audio == null || audio.narrators.isEmpty()) state.copy(isLoading = false, failed = true, failure = Failure.UNAVAILABLE).only()
                else {
                    val narrator = audio.narrators.firstOrNull { it.id == state.narrator?.id } ?: audio.narrators[0]
                    val next = state.copy(audio = audio, narrator = narrator)
                    next.with(start(next, player))
                }
            }

            is Action.NarratorSelected ->
                if (action.narrator == state.narrator) state.only()
                else state.copy(narrator = action.narrator, currentTime = 0.0, resumeTime = 0.0, isLoading = true, isPlaying = false, isBuffering = false, failed = false, failure = null, revision = state.revision + 1).let { it.with(start(it, player)) }

            Action.TogglePlayPause ->
                if (state.audio == null || state.isLoading || state.failed) state.only()
                else state.with(runEffect { if (state.isPlaying || state.isBuffering) player.pause() else player.play() })

            Action.SkipForward -> state.with(Effect.Send(Action.Seek(minOf(state.duration, state.currentTime + SKIP_SECONDS))))
            Action.SkipBackward -> state.with(Effect.Send(Action.Seek(maxOf(0.0, state.currentTime - SKIP_SECONDS))))

            is Action.Seek ->
                if (state.audio == null || state.failed || state.isLoading || !action.seconds.isFinite() || state.duration <= 0) state.only()
                else action.seconds.coerceIn(0.0, state.duration).let { seconds -> state.copy(currentTime = seconds).with(runEffect { player.seek(seconds) }) }

            Action.RateTapped -> {
                val rate = RATES[(RATES.indexOf(state.rate).coerceAtLeast(0) + 1) % RATES.size]
                state.copy(rate = rate).with(runEffect { player.setRate(rate) })
            }

            Action.StopTapped -> State(revision = state.revision + 1).with(
                Effect.Merge(listOf(
                    runEffect(id = PlayerId, cancelInFlight = true) {},
                    runEffect(id = LoadId, cancelInFlight = true) {},
                    runEffect(id = StallId, cancelInFlight = true) {},
                    runEffect { player.stop() },
                )),
            )

            Action.ChapterTapped -> state.reference?.let { state.with(Effect.Send(Action.Delegate(DelegateAction.OpenChapter(it)))) } ?: state.only()

            is Action.Event -> if (state.audio == null) state.only() else when (val e = action.event) {
                is AudioPlayerEvent.Ready -> if (state.failed || !e.durationSeconds.isFinite() || e.durationSeconds < 0) state.only() else state.copy(duration = e.durationSeconds, isLoading = false).with(if (state.isBuffering) Effect.None else cancelStall())
                is AudioPlayerEvent.Time -> if (state.failed || !e.seconds.isFinite() || e.seconds < 0) state.only() else state.copy(currentTime = e.seconds).only()
                is AudioPlayerEvent.Playing -> if (state.failed) state.only() else state.copy(isPlaying = e.isPlaying, isBuffering = if (e.isPlaying) false else state.isBuffering, isLoading = if (e.isPlaying) false else state.isLoading).with(if (e.isPlaying) cancelStall() else Effect.None)
                is AudioPlayerEvent.Buffering -> if (state.failed || e.active == state.isBuffering) state.only() else state.copy(isBuffering = e.active).with(if (e.active) stallTimer(state.revision) else cancelStall())
                AudioPlayerEvent.Ended -> {
                    val next = state.reference?.let(ChapterNavigation::next)
                    if (state.failed) state.only()
                    else if (next == null) state.copy(isPlaying = false, isBuffering = false).with(cancelStall())
                    else state.copy(isPlaying = false, isBuffering = false).with(Effect.Send(Action.Play(next)))
                }
                AudioPlayerEvent.Failed -> state.copy(isLoading = false, isPlaying = false, isBuffering = false, failed = true, failure = Failure.PLAYBACK).with(Effect.Merge(listOf(cancelStall(), runEffect { player.pause() })))
            }

            is Action.Delegate -> state.only()
        }
    }

    private fun prepare(state: State, reference: PassageReference, resume: Double, audioClient: ScriptureAudioClient, player: AudioPlayerClient): com.nexussoft.verbum.common.arch.Reduced<State, Action> {
        val chapter = PassageReference(reference.bookId, reference.chapter)
        val revision = state.revision + 1
        val next = State(reference = chapter, narrator = state.narrator, rate = state.rate, isLoading = true, revision = revision, currentTime = resume.coerceAtLeast(0.0), resumeTime = resume.coerceAtLeast(0.0))
        return next.with(Effect.Merge(listOf(cancelStall(), runEffect(id = PlayerId, cancelInFlight = true) {},
            runEffect(id = LoadId, cancelInFlight = true) { send ->
                try {
                    player.stop()
                    val audio = audioClient.chapterAudio(chapter.bookId, chapter.chapter)
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    send(Action.Loaded(revision, audio))
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { send(Action.PreparationFailed(revision, if (error is VerbumApiException.Restricted) Failure.RESTRICTED else Failure.PREPARATION)) }
            },
        )))
    }

    private fun cancelStall(): Effect<Action> = runEffect(id = StallId, cancelInFlight = true) {}
    private fun stallTimer(revision: Int): Effect<Action> = runEffect(id = StallId, cancelInFlight = true) { send ->
        delay(45_000)
        send(Action.Stalled(revision))
    }

    /** Readiness gates retry seeks; no automatic provider retry or chapter pre-generation. */
    private fun start(state: State, player: AudioPlayerClient): Effect<Action> {
        val narrator = state.narrator ?: return Effect.Send(Action.Event(AudioPlayerEvent.Failed))
        val audio = state.audio ?: return Effect.Send(Action.Event(AudioPlayerEvent.Failed))
        val reference = state.reference ?: return Effect.Send(Action.Event(AudioPlayerEvent.Failed))
        val info = NowPlayingInfo(reference.formatted, "${audio.translationName} · ${narrator.name}")
        return Effect.Merge(listOf(stallTimer(state.revision), runEffect(id = PlayerId, cancelInFlight = true) { send ->
            try {
                player.load(narrator.url, info)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                player.setRate(state.rate)
                var pendingResume = state.resumeTime > 0
                if (!pendingResume) player.play()
                player.events.collect { event ->
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (event is AudioPlayerEvent.Ready && pendingResume) {
                        pendingResume = false
                        player.seek(if (event.durationSeconds > 0) minOf(state.resumeTime, (event.durationSeconds - 0.1).coerceAtLeast(0.0)) else state.resumeTime)
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        player.play()
                    }
                    send(Action.SessionEvent(state.revision, event))
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { send(Action.SessionEvent(state.revision, AudioPlayerEvent.Failed)) }
        }))
    }
}
