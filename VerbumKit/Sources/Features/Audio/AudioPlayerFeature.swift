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

    public enum Action: Equatable {
        /// Start listening to a chapter (from the reader, or the next one after the last ended).
        case play(PassageReference)
        case audioResponse(ChapterAudio?)
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

    public init() {}

    static let skip: TimeInterval = 15
    static let rates: [Float] = [1, 1.25, 1.5, 0.8]

    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .play(let reference):
                let chapter = PassageReference(bookId: reference.bookId, chapter: reference.chapter)
                state.reference = chapter
                state.audio = nil
                state.isLoading = true
                state.failed = false
                state.isPlaying = false
                state.currentTime = 0
                state.duration = 0
                return .merge(
                    .cancel(id: CancelID.player),
                    .run { [scriptureAudio, player] send in
                        await player.stop()
                        let audio = try? await scriptureAudio.chapterAudio(bookId: chapter.bookId, chapter: chapter.chapter)
                        try Task.checkCancellation()
                        await send(.audioResponse(audio))
                    }
                    .cancellable(id: CancelID.load, cancelInFlight: true)
                )

            case .audioResponse(let audio):
                guard let reference = state.reference else { return .none }
                if let audio, audio.reference != reference { return .none }
                guard let audio, !audio.narrators.isEmpty else {
                    state.isLoading = false
                    state.failed = true
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
                state.currentTime = 0
                state.isLoading = true
                return start(narrator, state: state)

            case .togglePlayPause:
                guard state.audio != nil, !state.isLoading, !state.failed else { return .none }
                let playing = state.isPlaying
                return .run { [player] _ in playing ? await player.pause() : await player.play() }

            case .skipForward:
                return .send(.seek(min(state.duration, state.currentTime + Self.skip)))

            case .skipBackward:
                return .send(.seek(max(0, state.currentTime - Self.skip)))

            case .seek(let time):
                guard state.isActive else { return .none }
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
                state = State()
                return .merge(
                    .cancel(id: CancelID.player),
                    .cancel(id: CancelID.load),
                    .run { [player] _ in await player.stop() }
                )

            case .chapterTapped:
                guard let reference = state.reference else { return .none }
                return .send(.delegate(.openChapter(reference)))

            case .event(.ready(let duration)):
                guard state.audio != nil else { return .none }
                state.duration = duration
                state.isLoading = false
                return nowPlaying(state)

            case .event(.time(let time)):
                guard state.audio != nil else { return .none }
                let previous = state.currentTime
                state.currentTime = time
                if Int(previous / 10) != Int(time / 10) { return nowPlaying(state) }
                return .none

            case .event(.playing(let playing)):
                guard state.audio != nil else { return .none }
                guard playing != state.isPlaying else { return .none }
                state.isPlaying = playing
                return nowPlaying(state)

            case .event(.ended):
                guard state.audio != nil else { return .none }
                state.isPlaying = false
                guard let reference = state.reference, let next = ChapterNavigation.next(after: reference) else {
                    return .none
                }
                return .send(.play(next))

            case .event(.failed):
                guard state.audio != nil else { return .none }
                state.isLoading = false
                state.isPlaying = false
                state.failed = true
                return .none

            case .event(.remote(let command)):
                switch command {
                case .play: return .run { [player] _ in await player.play() }
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

    /// Load the recording, start it, and keep listening to the player until stopped or replaced.
    private func start(_ narrator: AudioNarrator, state: State) -> Effect<Action> {
        guard let url = URL(string: narrator.url) else { return .send(.event(.failed)) }
        let rate = state.rate
        return .run { [player] send in
            await player.load(url)
            await player.setRate(rate)
            let events = player.events()
            await player.play()
            for await event in events {
                await send(.event(event))
            }
        }
        .cancellable(id: CancelID.player, cancelInFlight: true)
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

    private enum CancelID { case load, player }
}
