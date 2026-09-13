package com.nexussoft.verbum.clients

import kotlinx.coroutines.flow.Flow

/**
 * A spoken conversation with the study companion (§19 "realtime", brought forward at the owner's
 * request). The feature starts it with a session from [RealtimeSessionClient], a configuration
 * (what to talk about, which tools it may call) and a handler that runs those tools; it then
 * observes events until it stops the conversation. No audio or transcript ever touches our
 * server. Twin of iOS `VoiceClient`.
 */
interface VoiceClient {
    /** Connects, configures the session and starts capturing the microphone. The flow ends when the conversation does. */
    suspend fun start(session: RealtimeSession, configuration: VoiceConfiguration, tools: VoiceToolHandler): Flow<VoiceEvent>
    /** Keeps listening but sends nothing while muted. */
    suspend fun setMuted(muted: Boolean)
    /** Ends the conversation: microphone off, connection closed. */
    suspend fun stop()
}

/** Runs one tool call for the model and returns its result as JSON text. */
fun interface VoiceToolHandler {
    suspend fun run(name: String, argumentsJson: String): String
}

data class VoiceConfiguration(
    /** The system instructions: who the companion is and what it is looking at. */
    val instructions: String,
    /** A short opening line the companion says first; null for silence until spoken to. */
    val opening: String? = null,
    val tools: List<VoiceTool> = emptyList(),
    /** OpenAI voice name. */
    val voice: String = "marin",
    /** BCP-47 hint for transcribing what the user says. */
    val language: String = "en",
)

/** A function the model may call. [parametersJson] is a JSON Schema object, as text. */
data class VoiceTool(val name: String, val description: String, val parametersJson: String)

sealed interface VoiceEvent {
    /** Connected and configured; the microphone is open. */
    data object Listening : VoiceEvent
    data class UserSpeaking(val speaking: Boolean) : VoiceEvent
    /** What the user said, once transcribed. */
    data class UserSaid(val text: String) : VoiceEvent
    /** The companion's words as they are spoken. */
    data class AssistantDelta(val delta: String) : VoiceEvent
    /** The companion's full sentence(s) for one turn. */
    data class AssistantSaid(val text: String) : VoiceEvent
    data class AssistantSpeaking(val speaking: Boolean) : VoiceEvent
    /** A tool is being run for the model. */
    data class ToolCalled(val name: String) : VoiceEvent
    data object Ended : VoiceEvent
    data class Failed(val error: VoiceException) : VoiceEvent
}

/** Why a conversation could not start or continue, in the states the sheet shows (§52). */
sealed class VoiceException : Exception() {
    /** The server has no key configured, or does not offer voice at all. */
    data object Unavailable : VoiceException() { private fun readResolve(): Any = Unavailable }
    data object NetworkUnavailable : VoiceException() { private fun readResolve(): Any = NetworkUnavailable }
    data object MicrophoneDenied : VoiceException() { private fun readResolve(): Any = MicrophoneDenied }
    data object Failed : VoiceException() { private fun readResolve(): Any = Failed }

    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int
}

/** The default when no conversation transport is wired: every start says the feature is unavailable. */
object UnavailableVoiceClient : VoiceClient {
    override suspend fun start(session: RealtimeSession, configuration: VoiceConfiguration, tools: VoiceToolHandler): Flow<VoiceEvent> = throw VoiceException.Unavailable
    override suspend fun setMuted(muted: Boolean) = Unit
    override suspend fun stop() = Unit
}
