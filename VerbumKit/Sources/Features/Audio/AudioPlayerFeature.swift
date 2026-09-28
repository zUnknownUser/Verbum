import Clients
import ComposableArchitecture
import Foundation
import Models

/// The one player in the app. Lives on `AppFeature`, not on a chapter, so
/// listening survives navigation; the mini player renders it anywhere.
/// Chapters chain: when one ends, the next begins.
@Reducer
public struct AudioPlayerFeature {
    @ObservableState
    public struct State: Equatable {
        public var reference: PassageReference?
        public var audio: ChapterAudio?
        public var narrator: AudioNarrator?
        public var isPlaying = false
        public var isLoading = false
        public var currentTime: TimeInterval = 0
        public var duration: TimeInterval = 0
        public var rate: Float = 1
        public var failed = false
        public var failure: Failure?
        public var isBuffering = false
        public var revision = 0
        public var resumeTime: TimeInterval = 0

        public init() {}

        public var readingPosition: AudioReadingPosition? {
            guard !failed, !isLoading, let audio, let cue = AudioCue.active(in: narrator?.cues ?? [], at: currentTime) else { return nil }
            return .init(reference: audio.reference, translationID: audio.translationId, cue: cue, isPlaying: isPlaying)
        }
        public var isActive: Bool { reference != nil }
        public var progress: Double { duration > 0 ? min(1, currentTime / duration) : 0 }

        public func isPlaying(_ reference: PassageReference) -> Bool {
            isPlaying && self.reference?.bookId == reference.bookId && self.reference?.chapter == reference.chapter
        }
    }

    public enum Failure: Equatable, Sendable { case unavailable, preparation, restricted, playback }

    public enum Action: Equatable {
        /// Start listening to a chapter (from the reader, or the next one after the last ended).
        case play(PassageReference)
        case audioResponse(ChapterAudio?)
        case loaded(Int, ChapterAudio?)
        case preparationFailed(Int, Failure)
        case sessionEvent(Int, AudioPlayerEvent)
        case stalled(Int)
        case retryTapped
        case togglePlayPause
        case skipForward
        case skipBackward
        case seek(TimeInterval)
        case rateTapped
        case narratorSelected(AudioNarrator)
        case stopTapped
        case chapterTapped
        case event(AudioPlayerEvent)
        case delegate(Delegate)

        @CasePathable
        public enum Delegate: Equatable {
            case openChapter(PassageReference)
        }
    }

    @Dependency(\.scriptureAudio) var scriptureAudio
    @Dependency(\.audioPlayer) var player
    @Dependency(\.continuousClock) var clock

    public init() {}

    static let skip: TimeInterval = 15
    static let rates: [Float] = [1, 1.25, 1.5, 0.8]

    public var body: some ReducerOf<Self> {
        Reduce { state, incoming in
            let action: Action
            switch incoming {
            case .loaded(let revision, let audio):
                guard revision == state.revision, state.reference != nil else { return .none }
                action = .audioResponse(audio)
            case .sessionEvent(let revision, let event):
                guard revision == state.revision, state.audio != nil else { return .none }
                action = .event(event)
            default: action = incoming
            }
            switch action {
            case .play(let reference):
                return prepare(reference, resume: 0, state: &state)

            case .retryTapped:
                guard state.failed, let reference = state.reference else { return .none }
                return prepare(reference, resume: state.currentTime, state: &state)

            case .preparationFailed(let revision, let reason):
                guard revision == state.revision, state.reference != nil else { return .none }
                state.isLoading = false; state.failed = true; state.failure = reason
                return .none

            case .loaded, .sessionEvent: return .none

            case .stalled(let revision):
                guard revision == state.revision, state.audio != nil, state.isBuffering || state.isLoading else { return .none }
                return .send(.sessionEvent(revision, .failed))

            case .audioResponse(let audio):
                guard let reference = state.reference else { return .none }
                if let audio, audio.reference != reference { return .none }
                guard let audio, !audio.narrators.isEmpty else {
                    state.isLoading = false
                    state.failed = true
                    state.failure = .unavailable
                    return .none
                }
                state.audio = audio
                // Keep the narrator the listener chose if this chapter has them too.
                let narrator = audio.narrators.first { $0.id == state.narrator?.id } ?? audio.narrators[0]
                state.narrator = narrator
                return start(narrator, state: state)

            case .narratorSelected(let narrator):
                guard narrator != state.narrator else { return .none }
                state.narrator = narrator
                state.currentTime = 0; state.resumeTime = 0
                state.isLoading = true; state.isBuffering = false; state.isPlaying = false
                state.failed = false; state.failure = nil; state.revision += 1
                return start(narrator, state: state)

            case .togglePlayPause:
                guard state.audio != nil, !state.isLoading, !state.failed else { return .none }
                let playing = state.isPlaying || state.isBuffering
                return .run { [player] _ in playing ? await player.pause() : await player.play() }

            case .skipForward:
                return .send(.seek(min(state.duration, state.currentTime + Self.skip)))

            case .skipBackward:
                return .send(.seek(max(0, state.currentTime - Self.skip)))

            case .seek(let time):
                guard state.audio != nil, !state.failed, !state.isLoading, time.isFinite, state.duration > 0 else { return .none }
                let time = max(0, min(time, state.duration))
                state.currentTime = time
                return .merge(
                    .run { [player] _ in await player.seek(to: time) },
                    nowPlaying(state)
                )

            case .rateTapped:
                let index = Self.rates.firstIndex(of: state.rate) ?? 0
                state.rate = Self.rates[(index + 1) % Self.rates.count]
                let rate = state.rate
                return .merge(
                    .run { [player] _ in await player.setRate(rate) },
                    nowPlaying(state)
                )

            case .stopTapped:
                let revision = state.revision + 1
                state = State(); state.revision = revision
                return .merge(
                    .cancel(id: CancelID.player),
                    .cancel(id: CancelID.load),
                    .cancel(id: CancelID.stall),
                    .run { [player] _ in await player.stop() }
                )

            case .chapterTapped:
                guard let reference = state.reference else { return .none }
                return .send(.delegate(.openChapter(reference)))

            case .event(.ready(let duration)):
                guard state.audio != nil else { return .none }
                guard !state.failed, duration.isFinite, duration >= 0 else { return .none }
                state.duration = duration
                state.isLoading = false
                return .merge(nowPlaying(state), state.isBuffering ? .none : .cancel(id: CancelID.stall))

            case .event(.time(let time)):
                guard state.audio != nil else { return .none }
                guard !state.failed, time.isFinite, time >= 0 else { return .none }
                let previous = state.currentTime
                state.currentTime = time
                if Int(previous / 10) != Int(time / 10) { return nowPlaying(state) }
                return .none

            case .event(.playing(let playing)):
                guard state.audio != nil else { return .none }
                guard !state.failed else { return .none }
                state.isPlaying = playing
                if playing { state.isBuffering = false; state.isLoading = false }
                return .merge(nowPlaying(state), playing ? .cancel(id: CancelID.stall) : .none)

            case .event(.buffering(let buffering)):
                guard state.audio != nil, !state.failed, buffering != state.isBuffering else { return .none }
                state.isBuffering = buffering
                return buffering ? stallTimer(state.revision) : .cancel(id: CancelID.stall)

            case .event(.ended):
                guard state.audio != nil else { return .none }
                guard !state.failed else { return .none }
                state.isPlaying = false; state.isBuffering = false
                guard let reference = state.reference, let next = ChapterNavigation.next(after: reference) else {
                    return .cancel(id: CancelID.stall)
                }
                return .send(.play(next))

            case .event(.failed):
                guard state.audio != nil else { return .none }
                state.isLoading = false
                state.isPlaying = false
                state.failed = true; state.failure = .playback; state.isBuffering = false
                return .merge(.cancel(id: CancelID.stall), nowPlaying(state), .run { [player] _ in await player.pause() })

            case .event(.remote(let command)):
                switch command {
                case .play: return state.failed ? .send(.retryTapped) : .run { [player] _ in await player.play() }
                case .pause: return .run { [player] _ in await player.pause() }
                case .togglePlayPause: return .send(.togglePlayPause)
                case .skipForward: return .send(.skipForward)
                case .skipBackward: return .send(.skipBackward)
                case .seek(let time): return .send(.seek(time))
                }

            case .delegate:
                return .none
            }
        }
    }

    private func prepare(_ reference: PassageReference, resume: TimeInterval, state: inout State) -> Effect<Action> {
        let chapter = PassageReference(bookId: reference.bookId, chapter: reference.chapter)
        state.reference = chapter; state.audio = nil
        state.isLoading = true; state.failed = false; state.failure = nil
        state.isPlaying = false; state.isBuffering = false
        state.currentTime = max(0, resume); state.resumeTime = max(0, resume); state.duration = 0
        state.revision += 1
        let revision = state.revision
        return .merge(.cancel(id: CancelID.player), .cancel(id: CancelID.stall),
            .run { [scriptureAudio, player] send in
                await player.stop()
                do {
                    let audio = try await scriptureAudio.chapterAudio(bookId: chapter.bookId, chapter: chapter.chapter)
                    try Task.checkCancellation()
                    await send(.loaded(revision, audio))
                } catch is CancellationError {} catch {
                    let reason: Failure
                    if case VerbumAPIError.restricted = error { reason = .restricted } else { reason = .preparation }
                    await send(.preparationFailed(revision, reason))
                }
            }.cancellable(id: CancelID.load, cancelInFlight: true))
    }

    /// Retry fetches a fresh media URL, but seeks only after the player is ready.
    private func start(_ narrator: AudioNarrator, state: State) -> Effect<Action> {
        guard let url = URL(string: narrator.url) else { return .send(.event(.failed)) }
        let rate = state.rate, revision = state.revision, resume = state.resumeTime
        return .merge(stallTimer(revision), .run { [player] send in
            await player.load(url)
            try Task.checkCancellation()
            await player.setRate(rate)
            let events = player.events()
            var pendingResume = resume > 0
            if !pendingResume { await player.play() }
            for await event in events {
                try Task.checkCancellation()
                if case .ready(let duration) = event, pendingResume {
                    pendingResume = false
                    await player.seek(to: duration > 0 ? min(resume, max(0, duration - 0.1)) : resume)
                    try Task.checkCancellation()
                    await player.play()
                }
                await send(.sessionEvent(revision, event))
            }
        }.cancellable(id: CancelID.player, cancelInFlight: true))
    }

    private func stallTimer(_ revision: Int) -> Effect<Action> {
        .run { [clock] send in
            try await clock.sleep(for: .seconds(45))
            await send(.stalled(revision))
        }.cancellable(id: CancelID.stall, cancelInFlight: true)
    }

    private func nowPlaying(_ state: State) -> Effect<Action> {
        guard let reference = state.reference, let audio = state.audio, let narrator = state.narrator else { return .none }
        let info = NowPlayingInfo(
            title: reference.formatted,
            subtitle: "\(audio.translationName) · \(narrator.name)",
            elapsed: state.currentTime,
            duration: state.duration,
            rate: state.isPlaying ? state.rate : 0
        )
        return .run { [player] _ in await player.updateNowPlaying(info) }
    }

    private enum CancelID { case load, player, stall }
}
