import Foundation
import Testing
@testable import Clients

/// The Realtime protocol, scripted: what the conversation sends for what it
/// receives, without a socket or a microphone.
@Suite struct RealtimeConversationTests {
    final class FakeTransport: RealtimeTransport, @unchecked Sendable {
        let lock = NSLock()
        var sent: [[String: Any]] = []
        var incoming: AsyncThrowingStream<String, Error>.Continuation?
        var closed = false

        func connect(url: URL, headers: [String: String]) async throws -> AsyncThrowingStream<String, Error> {
            #expect(url.absoluteString == "wss://api.openai.com/v1/realtime?model=gpt-realtime")
            #expect(headers["Authorization"] == "Bearer ek_test")
            return AsyncThrowingStream { self.incoming = $0 }
        }
        func send(_ text: String) async throws {
            let json = try JSONSerialization.jsonObject(with: Data(text.utf8)) as! [String: Any]
            lock.withLock { sent.append(json) }
        }
        func close() async { closed = true }

        func receive(_ event: [String: Any]) {
            incoming?.yield(String(decoding: try! JSONSerialization.data(withJSONObject: event), as: UTF8.self))
        }
        func types() -> [String] { lock.withLock { sent.map { $0["type"] as! String } } }
    }

    final class FakeAudio: VoiceAudio, @unchecked Sendable {
        var permitted = true
        var capturing = false
        var played: [Data] = []
        var stoppedPlayback = 0
        var finished = false
        var onChunk: (@Sendable (Data) -> Void)?

        func requestPermission() async -> Bool { permitted }
        func startCapture(_ onChunk: @escaping @Sendable (Data) -> Void) async throws { capturing = true; self.onChunk = onChunk }
        func stopCapture() async { capturing = false }
        func play(_ pcm: Data) async { played.append(pcm) }
        func stopPlayback() async { stoppedPlayback += 1 }
        func finish() async { finished = true }
    }

    let session = RealtimeSession(clientSecret: "ek_test", expiresAt: 0, model: "gpt-realtime")
    let configuration = VoiceConfiguration(
        instructions: "You are Verbum.",
        opening: "Hi.",
        tools: [VoiceTool(name: "ask_scripture", description: "Ask", parametersJSON: #"{"type":"object","properties":{"question":{"type":"string"}},"required":["question"]}"#)],
        voice: "marin",
        language: "pt"
    )

    /// Pulls events until `count` have arrived (bounded so a broken flow fails, not hangs).
    func collect(_ iterator: inout AsyncStream<VoiceEvent>.AsyncIterator, _ count: Int) async -> [VoiceEvent] {
        var events: [VoiceEvent] = []
        for _ in 0..<count { if let e = await iterator.next() { events.append(e) } else { break } }
        return events
    }

    @Test func configuresThenListensThenOpens() async throws {
        let transport = FakeTransport(), audio = FakeAudio()
        let conversation = RealtimeConversation(transport: transport, audio: audio)
        let stream = try await conversation.start(session: session, configuration: configuration) { _, _ in "{}" }
        var iterator = stream.makeAsyncIterator()

        transport.receive(["type": "session.created"])
        try await Task.sleep(for: .milliseconds(50))
        let update = transport.lock.withLock { transport.sent.first }
        #expect(update?["type"] as? String == "session.update")
        let sessionBody = update?["session"] as? [String: Any]
        #expect(sessionBody?["instructions"] as? String == "You are Verbum.")
        #expect(sessionBody?["type"] as? String == "realtime")
        let audioBody = sessionBody?["audio"] as? [String: Any]
        let input = audioBody?["input"] as? [String: Any]
        #expect((input?["format"] as? [String: Any])?["rate"] as? Int == 24000)
        #expect((input?["turn_detection"] as? [String: Any])?["type"] as? String == "server_vad")
        #expect((input?["transcription"] as? [String: Any])?["language"] as? String == "pt")
        #expect(((audioBody?["output"] as? [String: Any])?["voice"] as? String) == "marin")
        let tools = sessionBody?["tools"] as? [[String: Any]]
        #expect(tools?.first?["name"] as? String == "ask_scripture")
        #expect((tools?.first?["parameters"] as? [String: Any])?["type"] as? String == "object")

        transport.receive(["type": "session.updated"])
        let events = await collect(&iterator, 1)
        #expect(events == [.listening])
        #expect(audio.capturing)
        try await Task.sleep(for: .milliseconds(50))
        #expect(transport.types() == ["session.update", "response.create"])

        // Microphone chunks go up as base64; muted ones do not.
        audio.onChunk?(Data([1, 2, 3]))
        try await Task.sleep(for: .milliseconds(50))
        let append = transport.lock.withLock { transport.sent.last }
        #expect(append?["type"] as? String == "input_audio_buffer.append")
        #expect(append?["audio"] as? String == Data([1, 2, 3]).base64EncodedString())
        await conversation.setMuted(true)
        audio.onChunk?(Data([4]))
        try await Task.sleep(for: .milliseconds(50))
        #expect(transport.types().count == 3)

        await conversation.stop()
        let tail = await collect(&iterator, 1)
        #expect(tail == [.ended])
        #expect(transport.closed && audio.finished && !audio.capturing)
    }

    @Test func playsAudioSurfacesTranscriptsAndYieldsToTheUser() async throws {
        let transport = FakeTransport(), audio = FakeAudio()
        let conversation = RealtimeConversation(transport: transport, audio: audio)
        let stream = try await conversation.start(session: session, configuration: VoiceConfiguration(instructions: "x")) { _, _ in "{}" }
        var iterator = stream.makeAsyncIterator()
        transport.receive(["type": "session.created"])
        transport.receive(["type": "session.updated"])
        _ = await collect(&iterator, 1)

        let pcm = Data([0, 1, 0, 2])
        transport.receive(["type": "response.created"])
        transport.receive(["type": "response.output_audio.delta", "delta": pcm.base64EncodedString()])
        transport.receive(["type": "response.output_audio_transcript.delta", "delta": "Hel"])
        transport.receive(["type": "response.output_audio_transcript.delta", "delta": "lo"])
        transport.receive(["type": "response.output_audio_transcript.done", "transcript": "Hello"])
        transport.receive(["type": "input_audio_buffer.speech_started"])
        transport.receive(["type": "input_audio_buffer.speech_stopped"])
        transport.receive(["type": "conversation.item.input_audio_transcription.completed", "transcript": " Why? "])
        transport.receive(["type": "response.done", "response": ["output": []]])
        let events = await collect(&iterator, 8)
        #expect(events == [
            .assistantSpeaking(true), .assistantDelta("Hel"), .assistantDelta("lo"), .assistantSaid("Hello"),
            .assistantSpeaking(false), .userSpeaking(true), .userSpeaking(false), .userSaid("Why?"),
        ])
        #expect(audio.played == [pcm])
        #expect(audio.stoppedPlayback == 1, "the user speaking over the companion drops queued playback")
    }

    @Test func runsToolCallsAndAnswersAfterTheResponseEnds() async throws {
        let transport = FakeTransport(), audio = FakeAudio()
        let conversation = RealtimeConversation(transport: transport, audio: audio)
        let calls = LockIsolatedBox<[(String, String)]>([])
        let stream = try await conversation.start(session: session, configuration: VoiceConfiguration(instructions: "x")) { name, args in
            calls.withValue { $0.append((name, args)) }
            return #"{"answer":"A sling."}"#
        }
        var iterator = stream.makeAsyncIterator()
        transport.receive(["type": "session.created"])
        transport.receive(["type": "session.updated"])
        _ = await collect(&iterator, 1)

        transport.receive(["type": "response.created"])
        let call: [String: Any] = ["type": "function_call", "call_id": "call_1", "name": "ask_scripture", "arguments": #"{"question":"how"}"#]
        transport.receive(["type": "response.output_item.done", "item": call])
        let events = await collect(&iterator, 1)
        #expect(events == [.toolCalled("ask_scripture")])
        try await Task.sleep(for: .milliseconds(50))
        #expect(transport.types() == ["session.update"], "the output waits for the active response to end")

        // The same call reported again in response.done is not run twice.
        transport.receive(["type": "response.done", "response": ["output": [call]]])
        try await Task.sleep(for: .milliseconds(50))
        #expect(calls.value.map(\.0) == ["ask_scripture"])
        #expect(transport.types() == ["session.update", "conversation.item.create", "response.create"])
        let output = transport.lock.withLock { transport.sent[1] }["item"] as? [String: Any]
        #expect(output?["type"] as? String == "function_call_output")
        #expect(output?["call_id"] as? String == "call_1")
        #expect(output?["output"] as? String == #"{"answer":"A sling."}"#)
    }

    @Test func socketFailureEndsTheConversationAsNetworkUnavailable() async throws {
        let transport = FakeTransport(), audio = FakeAudio()
        let conversation = RealtimeConversation(transport: transport, audio: audio)
        let stream = try await conversation.start(session: session, configuration: VoiceConfiguration(instructions: "x")) { _, _ in "{}" }
        var iterator = stream.makeAsyncIterator()
        transport.incoming?.finish(throwing: URLError(.networkConnectionLost))
        let events = await collect(&iterator, 1)
        #expect(events == [.failed(.networkUnavailable)])
        #expect(audio.finished)
    }

    @Test func noMicrophonePermissionNeverConnects() async {
        let transport = FakeTransport(), audio = FakeAudio()
        audio.permitted = false
        let conversation = RealtimeConversation(transport: transport, audio: audio)
        await #expect(throws: VoiceError.microphoneDenied) {
            _ = try await conversation.start(session: session, configuration: VoiceConfiguration(instructions: "x")) { _, _ in "{}" }
        }
        #expect(transport.incoming == nil)
    }

    final class LockIsolatedBox<T>: @unchecked Sendable {
        private let lock = NSLock()
        private var _value: T
        init(_ value: T) { _value = value }
        var value: T { lock.withLock { _value } }
        func withValue(_ body: (inout T) -> Void) { lock.withLock { body(&_value) } }
    }
}
