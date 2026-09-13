package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.AskScriptureClient
import com.nexussoft.verbum.clients.BibleClient
import com.nexussoft.verbum.clients.RealtimeSessionClient
import com.nexussoft.verbum.clients.SearchClient
import com.nexussoft.verbum.clients.VoiceClient
import com.nexussoft.verbum.clients.VoiceEvent
import com.nexussoft.verbum.clients.VoiceException
import com.nexussoft.verbum.clients.VoiceToolHandler
import com.nexussoft.verbum.common.arch.Effect
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.merge
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.runEffect
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.VoiceContext
import kotlinx.coroutines.CancellationException

/**
 * A spoken conversation about the page the user is on (chapter, entity, or an Ask answer).
 * Presented as a sheet by the app shell; one session per presentation. The transcript is state
 * so it can be read and so passages the companion draws on can be opened. Nothing here is
 * persisted (§47). Twin of iOS `VoiceFeature`.
 */
object VoiceFeature {
    data class State(
        val context: VoiceContext,
        val phase: Phase = Phase.Idle,
        val lines: List<Line> = emptyList(),
        /** The companion's sentence in progress. */
        val partial: String = "",
        val isMuted: Boolean = false,
        val isUserSpeaking: Boolean = false,
        /** Passages the companion has drawn on or opened, in order of mention. */
        val passages: List<PassageReference> = emptyList(),
    )

    sealed interface Phase {
        data object Idle : Phase
        data object Connecting : Phase
        data object Listening : Phase
        data object Speaking : Phase
        /** A tool is running for the model. */
        data object Thinking : Phase
        data object Ended : Phase
        data class Failed(val error: VoiceException) : Phase
    }

    data class Line(val id: Int, val role: Role, val text: String) {
        enum class Role { USER, COMPANION }
    }

    sealed interface Action {
        data object Started : Action
        data object EndTapped : Action
        data object MuteToggled : Action
        data object RetryTapped : Action
        data object Connected : Action
        data class Failed(val error: VoiceException) : Action
        data class Event(val event: VoiceEvent) : Action
        data class PassagesMentioned(val references: List<PassageReference>) : Action
        data class OpenRequested(val reference: PassageReference) : Action
        data class PassageTapped(val reference: PassageReference) : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }

    sealed interface DelegateAction {
        data class OpenPassage(val reference: PassageReference) : DelegateAction
    }

    private object SessionId

    /** Cancels the running session (its cancellation handler stops the microphone and the socket). The shell uses it when the sheet is swiped away. */
    fun <A> cancelSession(): Effect<A> = runEffect(id = SessionId, cancelInFlight = true) {}

    class Dependencies(
        val sessions: RealtimeSessionClient,
        val voice: VoiceClient,
        val ask: AskScriptureClient,
        val search: SearchClient,
        val bible: BibleClient,
        val language: () -> BookLanguage = { BookLanguage.current },
    )

    fun reducer(deps: Dependencies): Reducer<State, Action> = Reducer { state, action ->
        fun start(): Pair<State, Effect<Action>> {
            val context = state.context
            val language = deps.language()
            val effect = runEffect<Action>(id = SessionId, cancelInFlight = true) { send ->
                var chapterText: String? = null
                if (context is VoiceContext.Chapter) {
                    // Cached by the reader the user came from; a miss just means the companion works from the reference.
                    chapterText = runCatching { deps.bible.chapter(context.reference.bookId, context.reference.chapter) }
                        .getOrNull()?.joinToString("\n") { "${it.verseStart} ${it.text}" }
                }
                val configuration = VoiceScript.configuration(context, chapterText, language)
                try {
                    val session = deps.sessions.create()
                    val handler = VoiceToolHandler { name, arguments ->
                        VoiceScript.run(name, arguments, deps.ask, deps.search, { send(Action.PassagesMentioned(it)) }, { send(Action.OpenRequested(it)) })
                    }
                    try {
                        val events = deps.voice.start(session, configuration, handler)
                        send(Action.Connected)
                        events.collect { send(Action.Event(it)) }
                    } catch (e: CancellationException) {
                        // Dismissing the sheet cancels this effect; the microphone and the socket must not outlive it.
                        deps.voice.stop()
                        throw e
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: VoiceException) {
                    send(Action.Failed(e))
                } catch (e: Exception) {
                    send(Action.Failed(VoiceException.Failed))
                }
            }
            return state.copy(phase = Phase.Connecting, partial = "") to effect
        }

        when (action) {
            Action.Started -> if (state.phase != Phase.Idle) state.only() else start().let { (s, e) -> s.with(e) }
            Action.RetryTapped -> start().let { (s, e) -> s.with(e) }

            Action.EndTapped -> state.copy(phase = Phase.Ended).with(merge(cancelSession(), runEffect { deps.voice.stop() }))

            Action.MuteToggled -> {
                val muted = !state.isMuted
                state.copy(isMuted = muted).with(runEffect { deps.voice.setMuted(muted) })
            }

            Action.Connected -> state.only()
            is Action.Failed -> state.copy(phase = Phase.Failed(action.error)).only()

            is Action.Event -> when (val event = action.event) {
                VoiceEvent.Listening -> state.copy(phase = Phase.Listening).only()
                is VoiceEvent.UserSpeaking -> state.copy(isUserSpeaking = event.speaking).only()
                is VoiceEvent.UserSaid -> state.append(Line.Role.USER, event.text).only()
                is VoiceEvent.AssistantDelta -> state.copy(partial = state.partial + event.delta).only()
                is VoiceEvent.AssistantSaid -> state.copy(partial = "").append(Line.Role.COMPANION, event.text).only()
                is VoiceEvent.AssistantSpeaking -> when (state.phase) {
                    is Phase.Failed, Phase.Ended -> state.only()
                    else -> state.copy(phase = if (event.speaking) Phase.Speaking else Phase.Listening).only()
                }
                is VoiceEvent.ToolCalled -> if (state.phase == Phase.Listening || state.phase == Phase.Speaking) state.copy(phase = Phase.Thinking).only() else state.only()
                VoiceEvent.Ended -> if (state.phase == Phase.Ended || state.phase is Phase.Failed) state.only() else state.copy(phase = Phase.Ended).only()
                is VoiceEvent.Failed -> state.copy(phase = Phase.Failed(event.error)).only()
            }

            is Action.PassagesMentioned -> state.copy(passages = state.passages + action.references.filter { it !in state.passages }).only()

            is Action.OpenRequested -> {
                val passages = if (action.reference in state.passages) state.passages else state.passages + action.reference
                state.copy(passages = passages).with(Effect.Send(Action.Delegate(DelegateAction.OpenPassage(action.reference))))
            }

            is Action.PassageTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenPassage(action.reference))))
            is Action.Delegate -> state.only()
        }
    }

    private fun State.append(role: Line.Role, text: String) = copy(lines = lines + Line(lines.size, role, text))
}
