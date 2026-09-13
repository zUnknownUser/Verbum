import Clients
import ComposableArchitecture
import Foundation
import Models

/// A spoken conversation about the page the user is on (chapter, entity, or an
/// Ask answer). Presented as a sheet by the app shell; one session per
/// presentation. The transcript is state so it can be read and so passages the
/// companion draws on can be opened. Nothing here is persisted (§47).
@Reducer
public struct VoiceFeature {
    @ObservableState
    public struct State: Equatable {
        public let context: VoiceContext
        public var phase: Phase = .idle
        public var lines: [Line] = []
        /// The companion's sentence in progress.
        public var partial = ""
        public var isMuted = false
        public var isUserSpeaking = false
        /// Passages the companion has drawn on or opened, in order of mention.
        public var passages: [PassageReference] = []

        public init(context: VoiceContext) {
            self.context = context
        }
    }

    public enum Phase: Equatable, Sendable {
        case idle
        case connecting
        case listening
        case speaking
        /// A tool is running for the model.
        case thinking
        case ended
        case failed(VoiceError)
    }

    public struct Line: Equatable, Sendable, Identifiable {
        public enum Role: Equatable, Sendable { case user, companion }
        public let id: Int
        public let role: Role
        public let text: String
    }

    public enum Action: Equatable {
        case task
        case endTapped
        case muteToggled
        case retryTapped
        case started
        case failed(VoiceError)
        case event(VoiceEvent)
        case passagesMentioned([PassageReference])
        case openRequested(PassageReference)
        case passageTapped(PassageReference)
        case delegate(Delegate)

        @CasePathable
        public enum Delegate: Equatable {
            case openPassage(PassageReference)
        }
    }

    @Dependency(\.realtimeSessionClient) var realtimeSessionClient
    @Dependency(\.voiceClient) var voiceClient
    @Dependency(\.askScriptureClient) var askScriptureClient
    @Dependency(\.searchClient) var searchClient
    @Dependency(\.bibleClient) var bibleClient
    @Dependency(\.locale) var locale

    public init() {}

    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .task:
                guard state.phase == .idle else { return .none }
                return start(&state)

            case .retryTapped:
                return start(&state)

            case .endTapped:
                state.phase = .ended
                return .merge(.cancel(id: CancelID.session), .run { [voiceClient] _ in await voiceClient.stop() })

            case .muteToggled:
                state.isMuted.toggle()
                return .run { [voiceClient, muted = state.isMuted] _ in await voiceClient.setMuted(muted) }

            case .started:
                return .none

            case .failed(let error):
                state.phase = .failed(error)
                return .none

            case .event(.listening):
                state.phase = .listening
                return .none

            case .event(.userSpeaking(let speaking)):
                state.isUserSpeaking = speaking
                return .none

            case .event(.userSaid(let text)):
                append(.user, text, to: &state)
                return .none

            case .event(.assistantDelta(let delta)):
                state.partial += delta
                return .none

            case .event(.assistantSaid(let text)):
                state.partial = ""
                append(.companion, text, to: &state)
                return .none

            case .event(.assistantSpeaking(let speaking)):
                if case .failed = state.phase { return .none }
                if state.phase != .ended { state.phase = speaking ? .speaking : .listening }
                return .none

            case .event(.toolCalled):
                if state.phase == .listening || state.phase == .speaking { state.phase = .thinking }
                return .none

            case .event(.ended):
                if state.phase != .ended, !state.phase.isFailed { state.phase = .ended }
                return .none

            case .event(.failed(let error)):
                state.phase = .failed(error)
                return .none

            case .passagesMentioned(let references):
                for reference in references where !state.passages.contains(reference) { state.passages.append(reference) }
                return .none

            case .openRequested(let reference):
                if !state.passages.contains(reference) { state.passages.append(reference) }
                return .send(.delegate(.openPassage(reference)))

            case .passageTapped(let reference):
                return .send(.delegate(.openPassage(reference)))

            case .delegate:
                return .none
            }
        }
    }

    private func start(_ state: inout State) -> Effect<Action> {
        state.phase = .connecting
        state.partial = ""
        let context = state.context
        let language = BookLanguage(locale: locale)
        return .run { [realtimeSessionClient, voiceClient, askScriptureClient, searchClient, bibleClient] send in
            var chapterText: String?
            if case .chapter(let reference) = context {
                // Cached by the reader the user came from; a miss just means the companion works from the reference.
                let verses = try? await bibleClient.chapter(bookId: reference.bookId, chapter: reference.chapter)
                chapterText = verses?.map { "\($0.verseStart) \($0.text)" }.joined(separator: "\n")
            }
            let configuration = VoiceScript.configuration(for: context, chapterText: chapterText, language: language)
            do {
                let session = try await realtimeSessionClient.create()
                // Dismissing the sheet cancels this effect; the microphone and the socket must not outlive it.
                try await withTaskCancellationHandler {
                    let events = try await voiceClient.start(session, configuration) { name, arguments in
                        await VoiceScript.run(
                            name, arguments, ask: askScriptureClient, search: searchClient,
                            mentioned: { await send(.passagesMentioned($0)) },
                            open: { await send(.openRequested($0)) }
                        )
                    }
                    await send(.started)
                    for await event in events {
                        await send(.event(event))
                    }
                } onCancel: {
                    Task { await voiceClient.stop() }
                }
            } catch let error as VoiceError {
                await send(.failed(error))
            } catch is CancellationError {
                return
            } catch {
                await send(.failed(.failed))
            }
        }
        .cancellable(id: CancelID.session, cancelInFlight: true)
    }

    private func append(_ role: Line.Role, _ text: String, to state: inout State) {
        state.lines.append(Line(id: state.lines.count, role: role, text: text))
    }

    private enum CancelID { case session }
}

extension VoiceFeature.Phase {
    var isFailed: Bool {
        if case .failed = self { return true }
        return false
    }
}
