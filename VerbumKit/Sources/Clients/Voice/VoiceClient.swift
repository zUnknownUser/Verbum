import ComposableArchitecture
import Foundation

/// A spoken conversation with the study companion (spec §19 "realtime", brought
/// forward at the owner's request). The feature starts it with a session from
/// `RealtimeSessionClient`, a configuration (what to talk about, which tools it
/// may call) and a handler that runs those tools; it then observes events until
/// it stops the conversation. No audio or transcript ever touches our server.
@DependencyClient
public struct VoiceClient: Sendable {
    /// Connects, configures the session and starts capturing the microphone.
    /// The stream ends when the conversation does.
    public var start: @Sendable (_ session: RealtimeSession, _ configuration: VoiceConfiguration, _ tools: @escaping VoiceToolHandler) async throws -> AsyncStream<VoiceEvent>
    /// Keeps listening but sends nothing while muted.
    public var setMuted: @Sendable (_ muted: Bool) async -> Void
    /// Ends the conversation: microphone off, connection closed.
    public var stop: @Sendable () async -> Void
}

/// Runs one tool call for the model and returns its result as JSON text.
public typealias VoiceToolHandler = @Sendable (_ name: String, _ argumentsJSON: String) async -> String

public struct VoiceConfiguration: Equatable, Sendable {
    /// The system instructions: who the companion is and what it is looking at.
    public var instructions: String
    /// A short opening line the companion says first; nil for silence until spoken to.
    public var opening: String?
    public var tools: [VoiceTool]
    /// OpenAI voice name.
    public var voice: String
    /// BCP-47 hint for transcribing what the user says.
    public var language: String

    public init(instructions: String, opening: String? = nil, tools: [VoiceTool] = [], voice: String = "marin", language: String = "en") {
        self.instructions = instructions
        self.opening = opening
        self.tools = tools
        self.voice = voice
        self.language = language
    }
}

/// A function the model may call (OpenAI "function" tool). `parameters` is a
/// JSON Schema object, as text so this module carries no JSON model of its own.
public struct VoiceTool: Equatable, Sendable {
    public let name: String
    public let description: String
    public let parametersJSON: String

    public init(name: String, description: String, parametersJSON: String) {
        self.name = name
        self.description = description
        self.parametersJSON = parametersJSON
    }
}

public enum VoiceEvent: Equatable, Sendable {
    /// Connected and configured; the microphone is open.
    case listening
    case userSpeaking(Bool)
    /// What the user said, once transcribed.
    case userSaid(String)
    /// The companion's words as they are spoken.
    case assistantDelta(String)
    /// The companion's full sentence(s) for one turn.
    case assistantSaid(String)
    case assistantSpeaking(Bool)
    /// A tool is being run for the model (the page can show "looking it up").
    case toolCalled(String)
    case ended
    case failed(VoiceError)
}

/// Why a conversation could not start or continue, in the states the sheet shows (§52).
public enum VoiceError: Error, Equatable, Sendable {
    /// The server has no key configured, or does not offer voice at all.
    case unavailable
    case networkUnavailable
    case microphoneDenied
    case failed
}

extension VoiceClient: DependencyKey {
    public static let liveValue: VoiceClient = {
        let conversation = RealtimeConversation(transport: URLSessionRealtimeTransport(), audio: AVAudioEngineVoiceAudio())
        return VoiceClient(
            start: { session, configuration, tools in try await conversation.start(session: session, configuration: configuration, tools: tools) },
            setMuted: { muted in await conversation.setMuted(muted) },
            stop: { await conversation.stop() }
        )
    }()

    /// Previews: a scripted exchange, no microphone.
    public static let previewValue = VoiceClient(
        start: { _, configuration, _ in
            AsyncStream { continuation in
                continuation.yield(.listening)
                if let opening = configuration.opening { continuation.yield(.assistantSaid(opening)) }
                continuation.yield(.userSaid("Why did David refuse the armour?"))
                continuation.yield(.assistantSaid("He had not tested it — and the story wants you to see that the victory is the LORD's, not the weapon's. Shall I open 1 Samuel 17:38–40?"))
            }
        },
        setMuted: { _ in },
        stop: {}
    )
}

extension DependencyValues {
    public var voiceClient: VoiceClient {
        get { self[VoiceClient.self] }
        set { self[VoiceClient.self] = newValue }
    }
}
