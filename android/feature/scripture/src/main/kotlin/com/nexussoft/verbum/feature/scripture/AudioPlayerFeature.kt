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
import com.nexussoft.verbum.models.AudioNarrator
import com.nexussoft.verbum.models.ChapterAudio
import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.ensureActive

/**
 * The one player in the app. Lives on `AppFeature`, so listening survives navigation;
 * the mini player renders it anywhere. Chapters chain. Twin of iOS `AudioPlayerFeature`.
 */
object AudioPlayerFeature {
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
    ) {
        val isActive: Boolean get() = reference != null
        val progress: Float get() = if (duration > 0) (currentTime / duration).coerceAtMost(1.0).toFloat() else 0f
        fun isPlaying(reference: PassageReference) = isPlaying && this.reference?.bookId == reference.bookId && this.reference?.chapter == reference.chapter
    }

    sealed interface Action {
        data class Play(val reference: PassageReference) : Action
        data class AudioLoaded(val audio: ChapterAudio?) : Action
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

    fun reducer(audioClient: ScriptureAudioClient, player: AudioPlayerClient): Reducer<State, Action> = Reducer { state, action ->
        when (action) {
            is Action.Play -> {
                val chapter = PassageReference(action.reference.bookId, action.reference.chapter)
                State(reference = chapter, narrator = state.narrator, rate = state.rate, isLoading = true).with(
                    Effect.Merge(listOf(
                        runEffect(id = PlayerId, cancelInFlight = true) {},
                        runEffect(id = LoadId, cancelInFlight = true) { send ->
                            player.stop()
                            val audio = try { audioClient.chapterAudio(chapter.bookId, chapter.chapter) }
                            catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (_: Exception) { null }
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            send(Action.AudioLoaded(audio))
                        },
                    )),
                )
            }

            is Action.AudioLoaded -> {
                val audio = action.audio
                if (state.reference == null || (audio != null && audio.reference != state.reference)) state.only()
                else if (audio == null || audio.narrators.isEmpty()) state.copy(isLoading = false, failed = true).only()
                else {
                    val narrator = audio.narrators.firstOrNull { it.id == state.narrator?.id } ?: audio.narrators[0]
                    val next = state.copy(audio = audio, narrator = narrator)
                    next.with(start(next, player))
                }
            }

            is Action.NarratorSelected ->
                if (action.narrator == state.narrator) state.only()
                else state.copy(narrator = action.narrator, currentTime = 0.0, isLoading = true).let { it.with(start(it, player)) }

            Action.TogglePlayPause ->
                if (state.audio == null || state.isLoading || state.failed) state.only()
                else state.with(runEffect { if (state.isPlaying) player.pause() else player.play() })

            Action.SkipForward -> state.with(Effect.Send(Action.Seek(minOf(state.duration, state.currentTime + SKIP_SECONDS))))
            Action.SkipBackward -> state.with(Effect.Send(Action.Seek(maxOf(0.0, state.currentTime - SKIP_SECONDS))))

            is Action.Seek ->
                if (!state.isActive) state.only()
                else state.copy(currentTime = action.seconds).with(runEffect { player.seek(action.seconds) })

            Action.RateTapped -> {
                val rate = RATES[(RATES.indexOf(state.rate).coerceAtLeast(0) + 1) % RATES.size]
                state.copy(rate = rate).with(runEffect { player.setRate(rate) })
            }

            Action.StopTapped -> State().with(
                Effect.Merge(listOf(
                    runEffect(id = PlayerId, cancelInFlight = true) {},
                    runEffect(id = LoadId, cancelInFlight = true) {},
                    runEffect { player.stop() },
                )),
            )

            Action.ChapterTapped -> state.reference?.let { state.with(Effect.Send(Action.Delegate(DelegateAction.OpenChapter(it)))) } ?: state.only()

            is Action.Event -> if (state.audio == null) state.only() else when (val e = action.event) {
                is AudioPlayerEvent.Ready -> state.copy(duration = e.durationSeconds, isLoading = false).only()
                is AudioPlayerEvent.Time -> state.copy(currentTime = e.seconds).only()
                is AudioPlayerEvent.Playing -> if (e.isPlaying == state.isPlaying) state.only() else state.copy(isPlaying = e.isPlaying).only()
                AudioPlayerEvent.Ended -> {
                    val next = state.reference?.let(ChapterNavigation::next)
                    if (next == null) state.copy(isPlaying = false).only()
                    else state.copy(isPlaying = false).with(Effect.Send(Action.Play(next)))
                }
                AudioPlayerEvent.Failed -> state.copy(isLoading = false, isPlaying = false, failed = true).only()
            }

            is Action.Delegate -> state.only()
        }
    }

    /** Load the recording, start it, and keep listening to the player until stopped or replaced. */
    private fun start(state: State, player: AudioPlayerClient): Effect<Action> {
        val narrator = state.narrator ?: return Effect.Send(Action.Event(AudioPlayerEvent.Failed))
        val audio = state.audio ?: return Effect.Send(Action.Event(AudioPlayerEvent.Failed))
        val reference = state.reference ?: return Effect.Send(Action.Event(AudioPlayerEvent.Failed))
        val info = NowPlayingInfo(reference.formatted, "${audio.translationName} · ${narrator.name}")
        val rate = state.rate
        return runEffect(id = PlayerId, cancelInFlight = true) { send ->
            player.load(narrator.url, info)
            player.setRate(rate)
            player.play()
            player.events.collect { send(Action.Event(it)) }
        }
    }
}
