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
 */
class RealtimeConversation(
    private val transport: RealtimeTransport,
    private val audio: VoiceAudio,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : VoiceClient {
    companion object {
        const val ENDPOINT = "wss://api.openai.com/v1/realtime"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val mutex = Mutex()
    private var events: Channel<VoiceEvent>? = null
    private var receive: Job? = null
    private var muted = false
    @Volatile private var running = false
    private var responseActive = false
    private var speaking = false
    private val handledCalls = HashSet<String>()
    private val pendingOutputs = ArrayList<Pair<String, String>>()

    override suspend fun start(session: RealtimeSession, configuration: VoiceConfiguration, tools: VoiceToolHandler): Flow<VoiceEvent> {
        if (running) stop()
        if (!audio.requestPermission()) throw VoiceException.MicrophoneDenied
        val frames = try {
            transport.connect("$ENDPOINT?model=${session.model}", mapOf("Authorization" to "Bearer ${session.clientSecret}"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw VoiceException.NetworkUnavailable
        }
        val channel = Channel<VoiceEvent>(Channel.UNLIMITED)
        mutex.withLock {
            running = true
            muted = false
            responseActive = false
            speaking = false
            handledCalls.clear()
            pendingOutputs.clear()
            events = channel
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
                if (speaking) { speaking = false; emit(VoiceEvent.AssistantSpeaking(false)) }
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

            "response.done" -> {
                responseActive = false
                if (speaking) { speaking = false; emit(VoiceEvent.AssistantSpeaking(false)) }
                ((event["response"] as? JsonObject)?.get("output") as? JsonArray)?.forEach { item ->
                    (item as? JsonObject)?.let { runFunctionCall(it, tools) }
                }
                flushPendingOutputs()
            }

            // Request-level problems (a malformed event, a response asked for while one runs) do
            // not end the session; the socket closing does. Nothing is shown — nothing to do.
            "error" -> Unit
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
        if (!send) return
        send(buildJsonObject {
            put("type", "input_audio_buffer.append")
            put("audio", Base64.getEncoder().encodeToString(chunk))
        })
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
                    putJsonObject("turn_detection") { put("type", "server_vad"); put("create_response", true); put("interrupt_response", true) }
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
