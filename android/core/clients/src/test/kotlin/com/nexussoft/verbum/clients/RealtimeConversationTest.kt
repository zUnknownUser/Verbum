package com.nexussoft.verbum.clients

import com.nexussoft.verbum.clients.voice.RealtimeConversation
import com.nexussoft.verbum.clients.voice.RealtimeTransport
import com.nexussoft.verbum.clients.voice.VoiceAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Realtime protocol, scripted: what the conversation sends for what it receives, without a socket or a microphone. Twin of iOS `RealtimeConversationTests`. */
class RealtimeConversationTest {
    private class FakeTransport : RealtimeTransport {
        val sent = mutableListOf<JsonObject>()
        val incoming = Channel<String>(Channel.UNLIMITED)
        var connected: String? = null
        var closed = false
        override suspend fun connect(url: String, headers: Map<String, String>): Flow<String> {
            connected = url
            assertEquals("Bearer ek_test", headers["Authorization"])
            return incoming.receiveAsFlow()
        }
        override suspend fun send(text: String) { sent += Json.parseToJsonElement(text).jsonObject }
        override suspend fun close() { closed = true }
        fun receive(json: String) { incoming.trySend(json) }
        fun types() = sent.map { it["type"]!!.jsonPrimitive.content }
    }

    private class FakeAudio : VoiceAudio {
        var permitted = true
        var capturing = false
        val played = mutableListOf<ByteArray>()
        var stoppedPlayback = 0
        var finished = false
        var playbackActive = false
        var onChunk: ((ByteArray) -> Unit)? = null
        override suspend fun requestPermission() = permitted
        override suspend fun startCapture(onChunk: (ByteArray) -> Unit) { capturing = true; this.onChunk = onChunk }
        override suspend fun stopCapture() { capturing = false }
        override suspend fun play(pcm: ByteArray) { played += pcm }
        override suspend fun isPlaybackActive() = playbackActive
        override suspend fun stopPlayback() { stoppedPlayback++; playbackActive = false }
        override suspend fun finish() { finished = true }
    }

    private val session = RealtimeSession("ek_test", 0, "gpt-realtime")
    private val configuration = VoiceConfiguration(
        "You are Verbum.", "Hi.",
        listOf(VoiceTool("ask_scripture", "Ask", """{"type":"object","properties":{"question":{"type":"string"}},"required":["question"]}""")),
        "marin", "pt",
    )

    private suspend fun Channel<VoiceEvent>.next(): VoiceEvent = withTimeout(2_000) { receive() }

    private var clockMs = 10_000L

    private fun runConversation(block: suspend TestScope.(FakeTransport, FakeAudio, RealtimeConversation) -> Unit) = runTest {
        val transport = FakeTransport()
        val audio = FakeAudio()
        val conversation = RealtimeConversation(transport, audio, CoroutineScope(StandardTestDispatcher(testScheduler))) { clockMs }
        block(transport, audio, conversation)
    }

    /** The speaker must not reach the server through the microphone: while the companion is heard (and for a short tail after) captured chunks are dropped. */
    @Test
    fun microphoneIsClosedWhileTheCompanionIsHeard() = runConversation { transport, audio, conversation ->
        clockMs = 10_000
        val events = Channel<VoiceEvent>(Channel.UNLIMITED)
        val flow = conversation.start(session, VoiceConfiguration("x")) { _, _ -> "{}" }
        val collector = launch(kotlinx.coroutines.Dispatchers.Unconfined) { flow.collect { events.send(it) } }
        transport.receive("""{"type":"session.created"}""")
        transport.receive("""{"type":"session.updated"}""")
        advanceUntilIdle()
        assertEquals(VoiceEvent.Listening, events.next())
        val before = transport.sent.size

        transport.receive("""{"type":"response.created"}""")
        transport.receive("""{"type":"response.output_audio.delta","delta":"${Base64.getEncoder().encodeToString(byteArrayOf(0, 1))}"}""")
        advanceUntilIdle()
        audio.onChunk!!(byteArrayOf(9)); advanceUntilIdle()
        assertEquals(before, transport.sent.size, "companion speaking: dropped")

        audio.playbackActive = true
        transport.receive("""{"type":"response.done","response":{"output":[]}}""")
        advanceUntilIdle()
        audio.onChunk!!(byteArrayOf(9)); advanceUntilIdle()
        assertEquals(before, transport.sent.size, "still draining locally: dropped")

        audio.playbackActive = false
        clockMs += 100
        audio.onChunk!!(byteArrayOf(9)); advanceUntilIdle()
        assertEquals(before, transport.sent.size, "inside the tail: dropped")
        clockMs += RealtimeConversation.PLAYBACK_TAIL_MS
        audio.onChunk!!(byteArrayOf(9)); advanceUntilIdle()
        assertEquals("input_audio_buffer.append", transport.types().last())
        conversation.stop(); advanceUntilIdle(); collector.cancel()
    }

    /** A refused `session.update` (an `error` before `session.updated`) fails the session instead of "Connecting…" forever; the threshold goes on the wire as exactly 0.65. */
    @Test
    fun aRefusedUpdateFailsInsteadOfHanging() = runConversation { transport, _, conversation ->
        assertTrue(conversation.sessionUpdate(VoiceConfiguration("x")).toString().contains("\"threshold\":0.65"))
        val events = Channel<VoiceEvent>(Channel.UNLIMITED)
        val flow = conversation.start(session, VoiceConfiguration("x")) { _, _ -> "{}" }
        val collector = launch(kotlinx.coroutines.Dispatchers.Unconfined) { flow.collect { events.send(it) } }
        transport.receive("""{"type":"session.created"}""")
        transport.receive("""{"type":"error","error":{"type":"invalid_request_error","message":"Invalid 'session.audio.input.turn_detection.threshold'"}}""")
        advanceUntilIdle()
        assertEquals(VoiceEvent.Failed(VoiceException.Failed), events.next())
        assertTrue(transport.closed)
        collector.cancel()
    }

    @Test
    fun sessionAsksForNoInterruptionsAndAStricterVad() = runConversation { _, _, conversation ->
        val input = conversation.sessionUpdate(VoiceConfiguration("x"))["session"]!!.jsonObject["audio"]!!.jsonObject["input"]!!.jsonObject
        val vad = input["turn_detection"]!!.jsonObject
        assertEquals("false", vad["interrupt_response"]!!.jsonPrimitive.content)
        assertTrue(vad["threshold"]!!.jsonPrimitive.content.toDouble() >= 0.6)
        assertTrue(vad["silence_duration_ms"]!!.jsonPrimitive.content.toInt() >= 700)
    }

    @Test
    fun configuresThenListensThenOpens() = runConversation { transport, audio, conversation ->
        val events = Channel<VoiceEvent>(Channel.UNLIMITED)
        val flow = conversation.start(session, configuration) { _, _ -> "{}" }
        val collector = launch(kotlinx.coroutines.Dispatchers.Unconfined) { flow.collect { events.send(it) } }
        assertEquals("wss://api.openai.com/v1/realtime?model=gpt-realtime", transport.connected)

        transport.receive("""{"type":"session.created"}""")
        testScheduler.runCurrent() // not advanceUntilIdle: that would also run the 20 s configuration watchdog
        val update = transport.sent.first()
        assertEquals("session.update", update["type"]!!.jsonPrimitive.content)
        val sessionBody = update["session"]!!.jsonObject
        assertEquals("You are Verbum.", sessionBody["instructions"]!!.jsonPrimitive.content)
        assertEquals("realtime", sessionBody["type"]!!.jsonPrimitive.content)
        val input = sessionBody["audio"]!!.jsonObject["input"]!!.jsonObject
        assertEquals("24000", input["format"]!!.jsonObject["rate"]!!.jsonPrimitive.content)
        assertEquals("server_vad", input["turn_detection"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("pt", input["transcription"]!!.jsonObject["language"]!!.jsonPrimitive.content)
        assertEquals("marin", sessionBody["audio"]!!.jsonObject["output"]!!.jsonObject["voice"]!!.jsonPrimitive.content)
        val tools = sessionBody["tools"] as JsonArray
        assertEquals("ask_scripture", tools[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("object", tools[0].jsonObject["parameters"]!!.jsonObject["type"]!!.jsonPrimitive.content)

        transport.receive("""{"type":"session.updated"}""")
        advanceUntilIdle()
        assertEquals(VoiceEvent.Listening, events.next())
        assertTrue(audio.capturing)
        assertEquals(listOf("session.update", "response.create"), transport.types())

        // Microphone chunks go up as base64; muted ones do not.
        audio.onChunk!!(byteArrayOf(1, 2, 3))
        advanceUntilIdle()
        assertEquals("input_audio_buffer.append", transport.sent.last()["type"]!!.jsonPrimitive.content)
        assertEquals(Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)), transport.sent.last()["audio"]!!.jsonPrimitive.content)
        conversation.setMuted(true)
        audio.onChunk!!(byteArrayOf(4))
        advanceUntilIdle()
        assertEquals(3, transport.sent.size)

        conversation.stop()
        advanceUntilIdle()
        assertEquals(VoiceEvent.Ended, events.next())
        assertTrue(transport.closed && audio.finished && !audio.capturing)
        collector.cancel()
    }

    @Test
    fun playsAudioSurfacesTranscriptsAndYieldsToTheUser() = runConversation { transport, audio, conversation ->
        val events = Channel<VoiceEvent>(Channel.UNLIMITED)
        val flow = conversation.start(session, VoiceConfiguration("x")) { _, _ -> "{}" }
        val collector = launch(kotlinx.coroutines.Dispatchers.Unconfined) { flow.collect { events.send(it) } }
        transport.receive("""{"type":"session.created"}""")
        transport.receive("""{"type":"session.updated"}""")
        advanceUntilIdle()
        assertEquals(VoiceEvent.Listening, events.next())

        val pcm = byteArrayOf(0, 1, 0, 2)
        transport.receive("""{"type":"response.created"}""")
        transport.receive("""{"type":"response.output_audio.delta","delta":"${Base64.getEncoder().encodeToString(pcm)}"}""")
        transport.receive("""{"type":"response.output_audio_transcript.delta","delta":"Hel"}""")
        transport.receive("""{"type":"response.output_audio_transcript.delta","delta":"lo"}""")
        transport.receive("""{"type":"response.output_audio_transcript.done","transcript":"Hello"}""")
        transport.receive("""{"type":"input_audio_buffer.speech_started"}""")
        transport.receive("""{"type":"input_audio_buffer.speech_stopped"}""")
        transport.receive("""{"type":"conversation.item.input_audio_transcription.completed","transcript":" Why? "}""")
        transport.receive("""{"type":"response.done","response":{"output":[]}}""")
        advanceUntilIdle()
        val got = List(8) { events.next() }
        assertEquals(
            listOf(
                VoiceEvent.AssistantSpeaking(true), VoiceEvent.AssistantDelta("Hel"), VoiceEvent.AssistantDelta("lo"), VoiceEvent.AssistantSaid("Hello"),
                VoiceEvent.AssistantSpeaking(false), VoiceEvent.UserSpeaking(true), VoiceEvent.UserSpeaking(false), VoiceEvent.UserSaid("Why?"),
            ),
            got,
        )
        assertEquals(1, audio.played.size)
        assertContentEquals(pcm, audio.played[0])
        assertEquals(1, audio.stoppedPlayback, "the user speaking over the companion drops queued playback")
        conversation.stop(); advanceUntilIdle(); collector.cancel()
    }

    @Test
    fun runsToolCallsAndAnswersAfterTheResponseEnds() = runConversation { transport, _, conversation ->
        val events = Channel<VoiceEvent>(Channel.UNLIMITED)
        val calls = mutableListOf<Pair<String, String>>()
        val flow = conversation.start(session, VoiceConfiguration("x")) { name, args -> calls += name to args; """{"answer":"A sling."}""" }
        val collector = launch(kotlinx.coroutines.Dispatchers.Unconfined) { flow.collect { events.send(it) } }
        transport.receive("""{"type":"session.created"}""")
        transport.receive("""{"type":"session.updated"}""")
        advanceUntilIdle()
        assertEquals(VoiceEvent.Listening, events.next())

        transport.receive("""{"type":"response.created"}""")
        val call = """{"type":"function_call","call_id":"call_1","name":"ask_scripture","arguments":"{\"question\":\"how\"}"}"""
        transport.receive("""{"type":"response.output_item.done","item":$call}""")
        advanceUntilIdle()
        assertEquals(VoiceEvent.ToolCalled("ask_scripture"), events.next())
        assertEquals(listOf("session.update"), transport.types(), "the output waits for the active response to end")

        // The same call reported again in response.done is not run twice.
        transport.receive("""{"type":"response.done","response":{"output":[$call]}}""")
        advanceUntilIdle()
        assertEquals(listOf("ask_scripture"), calls.map { it.first })
        assertEquals(listOf("session.update", "conversation.item.create", "response.create"), transport.types())
        val output = transport.sent[1]["item"]!!.jsonObject
        assertEquals("function_call_output", output["type"]!!.jsonPrimitive.content)
        assertEquals("call_1", output["call_id"]!!.jsonPrimitive.content)
        assertEquals("""{"answer":"A sling."}""", output["output"]!!.jsonPrimitive.content)
        conversation.stop(); advanceUntilIdle(); collector.cancel()
    }

    @Test
    fun socketFailureEndsTheConversationAsNetworkUnavailable() = runConversation { transport, audio, conversation ->
        val events = Channel<VoiceEvent>(Channel.UNLIMITED)
        val flow = conversation.start(session, VoiceConfiguration("x")) { _, _ -> "{}" }
        val collector = launch(kotlinx.coroutines.Dispatchers.Unconfined) { flow.collect { events.send(it) } }
        transport.incoming.close(IOException("lost"))
        advanceUntilIdle()
        assertEquals(VoiceEvent.Failed(VoiceException.NetworkUnavailable), events.next())
        assertTrue(audio.finished)
        collector.cancel()
    }

    @Test
    fun noMicrophonePermissionNeverConnects() = runConversation { transport, audio, conversation ->
        audio.permitted = false
        assertFailsWith<VoiceException.MicrophoneDenied> { conversation.start(session, VoiceConfiguration("x")) { _, _ -> "{}" } }
        assertNull(transport.connected)
    }
}
