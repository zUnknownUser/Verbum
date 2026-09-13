import Foundation

/// The wire to OpenAI's Realtime API: text frames over a WebSocket. Injected so
/// the conversation logic is tested without a network.
public protocol RealtimeTransport: Sendable {
    /// Opens the socket; the stream carries every incoming text frame and ends
    /// (or throws) when the socket closes.
    func connect(url: URL, headers: [String: String]) async throws -> AsyncThrowingStream<String, Error>
    func send(_ text: String) async throws
    func close() async
}

/// The device's ears and mouth: PCM16 mono 24 kHz in both directions.
public protocol VoiceAudio: Sendable {
    func requestPermission() async -> Bool
    /// Starts the microphone; `onChunk` receives ~100 ms of PCM16 at a time.
    func startCapture(_ onChunk: @escaping @Sendable (Data) -> Void) async throws
    func stopCapture() async
    /// Queues PCM16 for playback, in order.
    func play(_ pcm: Data) async
    /// Whether queued audio is still coming out of the speaker.
    func isPlaybackActive() async -> Bool
    /// Drops whatever is queued (the user interrupted).
    func stopPlayback() async
    /// Releases the audio session.
    func finish() async
}

/// One conversation over the Realtime API (GA event names, 2025-08+):
/// connect with the ephemeral secret → `session.update` (instructions, tools,
/// PCM formats, server VAD, transcription) → stream microphone chunks as
/// `input_audio_buffer.append` → play `response.output_audio.delta`, surface
/// transcripts, run `function_call` items through the handler and answer with
/// `function_call_output` + `response.create`. The user speaking over the
/// companion (`speech_started`) drops queued playback at once.
///
/// **Half-duplex on purpose.** While the companion's audio is coming out of
/// the speaker — and for a short tail after — the microphone is not sent.
/// Otherwise the speaker leaks back into the microphone (no echo cancellation
/// on the simulator, imperfect on a speakerphone), the server's VAD hears
/// "speech", transcribes the companion's own words as the reader's, and
/// answers itself in a loop. The reader can still stop it with End.
public actor RealtimeConversation {
    /// How long after the last audio frame the microphone stays closed.
    static let playbackTail: Duration = .milliseconds(600)
    public static let endpoint = URL(string: "wss://api.openai.com/v1/realtime")!

    private let transport: RealtimeTransport
    private let audio: VoiceAudio
    private var continuation: AsyncStream<VoiceEvent>.Continuation?
    private var receiveTask: Task<Void, Never>?
    private var muted = false
    private var running = false
    private var responseActive = false
    private var handledCalls = Set<String>()
    private var pendingOutputs: [(callID: String, output: String)] = []
    private var speaking = false
    /// When the companion's audio last ended locally; the microphone reopens after `playbackTail`.
    private var playbackEndedAt: ContinuousClock.Instant?

    public init(transport: RealtimeTransport, audio: VoiceAudio) {
        self.transport = transport
        self.audio = audio
    }

    public func start(session: RealtimeSession, configuration: VoiceConfiguration, tools: @escaping VoiceToolHandler) async throws -> AsyncStream<VoiceEvent> {
        if running { await stop() }
        guard await audio.requestPermission() else { throw VoiceError.microphoneDenied }

        var components = URLComponents(url: Self.endpoint, resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "model", value: session.model)]
        let frames: AsyncThrowingStream<String, Error>
        do {
            frames = try await transport.connect(url: components.url!, headers: ["Authorization": "Bearer \(session.clientSecret)"])
        } catch {
            throw VoiceError.networkUnavailable
        }

        running = true
        muted = false
        responseActive = false
        handledCalls = []
        pendingOutputs = []
        let (stream, continuation) = AsyncStream.makeStream(of: VoiceEvent.self)
        self.continuation = continuation

        receiveTask = Task { [weak self] in
            do {
                for try await frame in frames {
                    guard let self else { return }
                    await self.handle(frame, configuration: configuration, tools: tools)
                }
                await self?.finish(with: .ended)
            } catch {
                await self?.finish(with: .failed(.networkUnavailable))
            }
        }
        return stream
    }

    public func setMuted(_ muted: Bool) {
        self.muted = muted
    }

    public func stop() async {
        guard running else { return }
        await finish(with: .ended)
    }

    // MARK: Incoming

    private func handle(_ frame: String, configuration: VoiceConfiguration, tools: @escaping VoiceToolHandler) async {
        guard let data = frame.data(using: .utf8),
              let event = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = event["type"] as? String else { return }
        switch type {
        case "session.created":
            await send(Self.sessionUpdate(configuration))

        case "session.updated":
            // Configured: open the microphone, then let the companion open if asked to.
            do {
                try await audio.startCapture { [weak self] chunk in
                    Task { await self?.capture(chunk) }
                }
            } catch {
                await finish(with: .failed(.microphoneDenied))
                return
            }
            continuation?.yield(.listening)
            if let opening = configuration.opening, !responseActive {
                responseActive = true
                await send(["type": "response.create", "response": ["instructions": "Say exactly this, then wait: \(opening)"]])
            }

        case "input_audio_buffer.speech_started":
            await audio.stopPlayback()
            if speaking { speaking = false; playbackEndedAt = .now; continuation?.yield(.assistantSpeaking(false)) }
            continuation?.yield(.userSpeaking(true))

        case "input_audio_buffer.speech_stopped":
            continuation?.yield(.userSpeaking(false))

        case "conversation.item.input_audio_transcription.completed":
            if let transcript = (event["transcript"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines), !transcript.isEmpty {
                continuation?.yield(.userSaid(transcript))
            }

        case "response.created":
            responseActive = true

        case "response.output_audio.delta":
            if let delta = event["delta"] as? String, let pcm = Data(base64Encoded: delta) {
                if !speaking { speaking = true; continuation?.yield(.assistantSpeaking(true)) }
                await audio.play(pcm)
            }

        case "response.output_audio_transcript.delta":
            if let delta = event["delta"] as? String { continuation?.yield(.assistantDelta(delta)) }

        case "response.output_audio_transcript.done":
            if let transcript = (event["transcript"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines), !transcript.isEmpty {
                continuation?.yield(.assistantSaid(transcript))
            }

        case "response.output_item.done":
            if let item = event["item"] as? [String: Any] { await runFunctionCall(item, tools: tools) }

        case "response.done":
            responseActive = false
            if speaking {
                speaking = false
                playbackEndedAt = .now
                continuation?.yield(.assistantSpeaking(false))
            }
            if let response = event["response"] as? [String: Any], let output = response["output"] as? [[String: Any]] {
                for item in output { await runFunctionCall(item, tools: tools) }
            }
            await flushPendingOutputs()

        case "error":
            // The API reports request-level problems here (a malformed event, a
            // response asked for while one runs). They do not end the session;
            // the socket closing does. Nothing is shown — there is nothing the user can do.
            break

        default:
            break
        }
    }

    private func runFunctionCall(_ item: [String: Any], tools: VoiceToolHandler) async {
        guard item["type"] as? String == "function_call",
              let callID = item["call_id"] as? String, let name = item["name"] as? String,
              !handledCalls.contains(callID) else { return }
        handledCalls.insert(callID)
        continuation?.yield(.toolCalled(name))
        let output = await tools(name, item["arguments"] as? String ?? "{}")
        pendingOutputs.append((callID, output))
        await flushPendingOutputs()
    }

    /// Tool results wait for the current response to end: the API refuses a
    /// `response.create` while one is active.
    private func flushPendingOutputs() async {
        guard !responseActive, !pendingOutputs.isEmpty else { return }
        for pending in pendingOutputs {
            await send(["type": "conversation.item.create", "item": ["type": "function_call_output", "call_id": pending.callID, "output": pending.output]])
        }
        pendingOutputs = []
        responseActive = true
        await send(["type": "response.create"])
    }

    // MARK: Outgoing

    private func capture(_ chunk: Data) async {
        guard running, !muted, await microphoneIsOpen() else { return }
        await send(["type": "input_audio_buffer.append", "audio": chunk.base64EncodedString()])
    }

    /// Closed while the companion is heard, and for `playbackTail` after.
    private func microphoneIsOpen() async -> Bool {
        if speaking { return false }
        if await audio.isPlaybackActive() {
            playbackEndedAt = .now
            return false
        }
        if let ended = playbackEndedAt, ended.duration(to: .now) < Self.playbackTail { return false }
        return true
    }

    private func send(_ event: [String: Any]) async {
        guard running, let data = try? JSONSerialization.data(withJSONObject: event), let text = String(data: data, encoding: .utf8) else { return }
        try? await transport.send(text)
    }

    static func sessionUpdate(_ configuration: VoiceConfiguration) -> [String: Any] {
        [
            "type": "session.update",
            "session": [
                "type": "realtime",
                "instructions": configuration.instructions,
                "output_modalities": ["audio"],
                "audio": [
                    "input": [
                        "format": ["type": "audio/pcm", "rate": 24000],
                        // Half-duplex: the microphone is closed while the companion speaks, so
                        // interruptions cannot be heard anyway; a higher threshold and a longer
                        // silence keep room noise from becoming a "question".
                        "turn_detection": ["type": "server_vad", "threshold": 0.65, "prefix_padding_ms": 300, "silence_duration_ms": 800, "create_response": true, "interrupt_response": false],
                        "transcription": ["model": "gpt-4o-mini-transcribe", "language": configuration.language],
                    ],
                    "output": [
                        "format": ["type": "audio/pcm", "rate": 24000],
                        "voice": configuration.voice,
                    ],
                ],
                "tools": configuration.tools.map { tool -> [String: Any] in
                    let parameters = (try? JSONSerialization.jsonObject(with: Data(tool.parametersJSON.utf8))) as? [String: Any] ?? ["type": "object", "properties": [:]]
                    return ["type": "function", "name": tool.name, "description": tool.description, "parameters": parameters]
                },
                "tool_choice": configuration.tools.isEmpty ? "none" : "auto",
            ],
        ]
    }

    private func finish(with event: VoiceEvent) async {
        guard running else { return }
        running = false
        receiveTask?.cancel()
        receiveTask = nil
        await audio.stopCapture()
        await audio.stopPlayback()
        await audio.finish()
        await transport.close()
        continuation?.yield(event)
        continuation?.finish()
        continuation = nil
    }
}
