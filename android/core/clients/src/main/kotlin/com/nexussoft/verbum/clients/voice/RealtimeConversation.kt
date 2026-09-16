package com.nexussoft.verbum.clients.voice

import com.nexussoft.verbum.clients.RealtimeSession
import com.nexussoft.verbum.clients.VoiceClient
import com.nexussoft.verbum.clients.VoiceConfiguration
import com.nexussoft.verbum.clients.VoiceEvent
import com.nexussoft.verbum.clients.VoiceException
import com.nexussoft.verbum.clients.VoiceToolHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/** The wire to OpenAI's Realtime API: text frames over a WebSocket. Injected so the conversation logic is tested without a network. */
interface RealtimeTransport {
    /** Opens the socket; the flow carries every incoming text frame and ends (or throws) when the socket closes. */
    suspend fun connect(url: String, headers: Map<String, String>): Flow<String>
    suspend fun send(text: String)
    suspend fun close()
}

/** The device's ears and mouth: PCM16 mono 24 kHz in both directions. */
interface VoiceAudio {
    suspend fun requestPermission(): Boolean
    /** Starts the microphone; [onChunk] receives ~100 ms of PCM16 at a time. */
    suspend fun startCapture(onChunk: (ByteArray) -> Unit)
    suspend fun stopCapture()
    /** Queues PCM16 for playback, in order. */
    suspend fun play(pcm: ByteArray)
    /** Whether queued audio is still coming out of the speaker. */
    suspend fun isPlaybackActive(): Boolean
    /** Drops whatever is queued (the user interrupted). */
    suspend fun stopPlayback()
    /** Releases the audio session. */
    suspend fun finish()
}

/**
 * One conversation over the Realtime API (GA event names, 2025-08+): connect with the ephemeral
 * secret → `session.update` (instructions, tools, PCM formats, server VAD, transcription) →
 * stream microphone chunks as `input_audio_buffer.append` → play `response.output_audio.delta`,
 * surface transcripts, run `function_call` items through the handler and answer with
 * `function_call_output` + `response.create`. The user speaking over the companion
 * (`speech_started`) drops queued playback at once. Twin of iOS `RealtimeConversation`.
 *
 * **Half-duplex on purpose.** While the companion's audio is coming out of the speaker — and for
 * a short tail after — the microphone is not sent. Otherwise the speaker leaks back into the
 * microphone (no echo cancellation on the emulator, imperfect on a speakerphone), the server's
 * VAD hears "speech", transcribes the companion's own words as the reader's, and answers itself
 * in a loop. The reader can still stop it with End.
 */
class RealtimeConversation(
    private val transport: RealtimeTransport,
    private val audio: VoiceAudio,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** Monotonic milliseconds; injected so the tail is testable. */
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) : VoiceClient {
    companion object {
        const val ENDPOINT = "wss://api.openai.com/v1/realtime"
        /** How long after the last audio frame the microphone stays closed. */
        const val PLAYBACK_TAIL_MS = 600L
        /** How long the server may take to accept `session.update`. */
        const val CONFIGURATION_TIMEOUT_MS = 20_000L
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val mutex = Mutex()
    private var events: Channel<VoiceEvent>? = null
    private var receive: Job? = null
    private var muted = false
    @Volatile private var running = false
    private var responseActive = false
    private var speaking = false
    /** `session.updated` arrived and the microphone is open. */
    @Volatile private var listening = false
    /** When the companion's audio last ended locally; the microphone reopens after [PLAYBACK_TAIL_MS]. */
    @Volatile private var playbackEndedAt = 0L
    private val handledCalls = HashSet<String>()
    private val pendingOutputs = ArrayList<Pair<String, String>>()

    override suspend fun start(session: RealtimeSession, configuration: VoiceConfiguration, tools: VoiceToolHandler): Flow<VoiceEvent> {
        if (running) stop()
        if (!audio.requestPermission()) throw VoiceException.MicrophoneDenied
        val frames = try {
            transport.connect(session.relayUrl ?: "$ENDPOINT?model=${session.model}", mapOf("Authorization" to "Bearer ${session.clientSecret}"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw VoiceException.NetworkUnavailable
        }
        val channel = Channel<VoiceEvent>(Channel.UNLIMITED)
        mutex.withLock {
            running = true
            muted = false
            listening = false
            responseActive = false
            speaking = false
            handledCalls.clear()
            pendingOutputs.clear()
            events = channel
        }
        // A session that is not configured within a reasonable time is a dead one (a refused
        // update we did not recognise, a stalled socket): never "Connecting…" forever.
        scope.launch {
            delay(CONFIGURATION_TIMEOUT_MS)
            if (running && !listening) finish(VoiceEvent.Failed(VoiceException.NetworkUnavailable))
        }
        receive = scope.launch {
            try {
                frames.collect { frame -> handle(frame, configuration, tools) }
                finish(VoiceEvent.Ended)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                finish(VoiceEvent.Failed(VoiceException.NetworkUnavailable))
            }
        }
        return channel.receiveAsFlow()
    }

    override suspend fun setMuted(muted: Boolean) {
        mutex.withLock { this.muted = muted }
    }

    override suspend fun stop() {
        if (!running) return
        finish(VoiceEvent.Ended)
    }

    // ---- incoming

    private suspend fun handle(frame: String, configuration: VoiceConfiguration, tools: VoiceToolHandler) {
        val event = runCatching { json.parseToJsonElement(frame).jsonObject }.getOrNull() ?: return
        when (event.string("type")) {
            "session.created" -> send(sessionUpdate(configuration))

            "session.updated" -> {
                // Configured: open the microphone, then let the companion open if asked to.
                try {
                    audio.startCapture { chunk -> scope.launch { capture(chunk) } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    finish(VoiceEvent.Failed(VoiceException.MicrophoneDenied))
                    return
                }
                listening = true
                emit(VoiceEvent.Listening)
                val opening = configuration.opening
                if (opening != null && !responseActive) {
                    responseActive = true
                    send(buildJsonObject {
                        put("type", "response.create")
                        putJsonObject("response") { put("instructions", "Say exactly this, then wait: $opening") }
                    })
                }
            }

            "input_audio_buffer.speech_started" -> {
                audio.stopPlayback()
                if (speaking) { speaking = false; playbackEndedAt = now(); emit(VoiceEvent.AssistantSpeaking(false)) }
                emit(VoiceEvent.UserSpeaking(true))
            }

            "input_audio_buffer.speech_stopped" -> emit(VoiceEvent.UserSpeaking(false))

            "conversation.item.input_audio_transcription.completed" ->
                event.string("transcript")?.trim()?.takeIf { it.isNotEmpty() }?.let { emit(VoiceEvent.UserSaid(it)) }

            "response.created" -> responseActive = true

            "response.output_audio.delta" -> event.string("delta")?.let { delta ->
                val pcm = runCatching { Base64.getDecoder().decode(delta) }.getOrNull() ?: return
                if (!speaking) { speaking = true; emit(VoiceEvent.AssistantSpeaking(true)) }
                audio.play(pcm)
            }

            "response.output_audio_transcript.delta" -> event.string("delta")?.let { emit(VoiceEvent.AssistantDelta(it)) }

            "response.output_audio_transcript.done" ->
                event.string("transcript")?.trim()?.takeIf { it.isNotEmpty() }?.let { emit(VoiceEvent.AssistantSaid(it)) }

            "response.output_item.done" -> (event["item"] as? JsonObject)?.let { runFunctionCall(it, tools) }

            "verbum.limit" -> finish(VoiceEvent.Failed(VoiceException.Limited(com.nexussoft.verbum.models.UsageRestriction(event.string("code") ?: "quota_exceeded", event.string("retryAt")))))
            "response.done" -> {
                responseActive = false
                if (speaking) { speaking = false; playbackEndedAt = now(); emit(VoiceEvent.AssistantSpeaking(false)) }
                ((event["response"] as? JsonObject)?.get("output") as? JsonArray)?.forEach { item ->
                    (item as? JsonObject)?.let { runFunctionCall(it, tools) }
                }
                flushPendingOutputs()
            }

            // Request-level problems (a malformed event, a response asked for while one runs) do not
            // end a running session. Before the session is configured, though, an error means our
            // `session.update` was refused and `session.updated` will never come: fail, don't hang.
            "error" -> if (!listening) finish(VoiceEvent.Failed(VoiceException.Failed))
            else -> Unit
        }
    }

    private suspend fun runFunctionCall(item: JsonObject, tools: VoiceToolHandler) {
        if (item.string("type") != "function_call") return
        val callId = item.string("call_id") ?: return
        val name = item.string("name") ?: return
        if (!handledCalls.add(callId)) return
        emit(VoiceEvent.ToolCalled(name))
        val output = tools.run(name, item.string("arguments") ?: "{}")
        pendingOutputs += callId to output
        flushPendingOutputs()
    }

    /** Tool results wait for the current response to end: the API refuses a `response.create` while one is active. */
    private suspend fun flushPendingOutputs() {
        if (responseActive || pendingOutputs.isEmpty()) return
        for ((callId, output) in pendingOutputs) {
            send(buildJsonObject {
                put("type", "conversation.item.create")
                putJsonObject("item") {
                    put("type", "function_call_output")
                    put("call_id", callId)
                    put("output", output)
                }
            })
        }
        pendingOutputs.clear()
        responseActive = true
        send(buildJsonObject { put("type", "response.create") })
    }

    // ---- outgoing

    private suspend fun capture(chunk: ByteArray) {
        val send = mutex.withLock { running && !muted }
        if (!send || !microphoneIsOpen()) return
        send(buildJsonObject {
            put("type", "input_audio_buffer.append")
            put("audio", Base64.getEncoder().encodeToString(chunk))
        })
    }

    /** Closed while the companion is heard, and for [PLAYBACK_TAIL_MS] after. */
    private suspend fun microphoneIsOpen(): Boolean {
        if (speaking) return false
        if (audio.isPlaybackActive()) { playbackEndedAt = now(); return false }
        return now() - playbackEndedAt >= PLAYBACK_TAIL_MS
    }

    private suspend fun send(event: JsonObject) {
        if (!running) return
        runCatching { transport.send(event.toString()) }
    }

    private suspend fun emit(event: VoiceEvent) {
        events?.send(event)
    }

    private suspend fun finish(event: VoiceEvent) {
        val channel = mutex.withLock {
            if (!running) return
            running = false
            events.also { events = null }
        }
        audio.stopCapture()
        audio.stopPlayback()
        audio.finish()
        transport.close()
        channel?.send(event)
        channel?.close()
        val job = receive
        receive = null
        if (job != null && job != kotlin.coroutines.coroutineContext[Job]) job.cancel()
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    internal fun sessionUpdate(configuration: VoiceConfiguration): JsonObject = buildJsonObject {
        put("type", "session.update")
        putJsonObject("session") {
            put("type", "realtime")
            put("instructions", configuration.instructions)
            put("output_modalities", buildJsonArray { add("audio") })
            putJsonObject("audio") {
                putJsonObject("input") {
                    putJsonObject("format") { put("type", "audio/pcm"); put("rate", 24000) }
                    // Half-duplex: the microphone is closed while the companion speaks, so interruptions
                    // cannot be heard anyway; a higher threshold and a longer silence keep room noise
                    // from becoming a "question".
                    putJsonObject("turn_detection") {
                        put("type", "server_vad"); put("threshold", 0.65); put("prefix_padding_ms", 300); put("silence_duration_ms", 800)
                        put("create_response", true); put("interrupt_response", false)
                    }
                    putJsonObject("transcription") { put("model", "gpt-4o-mini-transcribe"); put("language", configuration.language) }
                }
                putJsonObject("output") {
                    putJsonObject("format") { put("type", "audio/pcm"); put("rate", 24000) }
                    put("voice", configuration.voice)
                }
            }
            put("tools", buildJsonArray {
                for (tool in configuration.tools) {
                    add(buildJsonObject {
                        put("type", "function")
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", runCatching { json.parseToJsonElement(tool.parametersJson).jsonObject }.getOrElse { buildJsonObject { put("type", "object"); putJsonObject("properties") {} } })
                    })
                }
            })
            put("tool_choice", if (configuration.tools.isEmpty()) "none" else "auto")
        }
    }
}
