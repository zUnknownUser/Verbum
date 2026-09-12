import ComposableArchitecture
import Foundation

/// What the player reports back. Remote commands (lock screen, AirPods,
/// CarPlay) arrive here too, so the feature — not the player — decides.
@CasePathable
public enum AudioPlayerEvent: Equatable, Sendable {
    case ready(duration: TimeInterval)
    case time(TimeInterval)
    case playing(Bool)
    case ended
    case failed
    case remote(RemoteCommand)

    @CasePathable
    public enum RemoteCommand: Equatable, Sendable {
        case play, pause, togglePlayPause, skipForward, skipBackward
        case seek(TimeInterval)
    }
}

/// What the lock screen and Control Center show.
public struct NowPlayingInfo: Equatable, Sendable {
    public var title: String
    public var subtitle: String
    public var elapsed: TimeInterval
    public var duration: TimeInterval
    public var rate: Float

    public init(title: String, subtitle: String, elapsed: TimeInterval, duration: TimeInterval, rate: Float) {
        self.title = title
        self.subtitle = subtitle
        self.elapsed = elapsed
        self.duration = duration
        self.rate = rate
    }
}

/// Streams one recording at a time. AVPlayer lives behind this; features and
/// views never see it (spec §35 "isolated side effects").
@DependencyClient
public struct AudioPlayerClient: Sendable {
    public var load: @Sendable (_ url: URL) async -> Void
    public var play: @Sendable () async -> Void
    public var pause: @Sendable () async -> Void
    public var seek: @Sendable (_ to: TimeInterval) async -> Void
    public var setRate: @Sendable (_ rate: Float) async -> Void
    public var stop: @Sendable () async -> Void
    public var events: @Sendable () -> AsyncStream<AudioPlayerEvent> = { .finished }
    public var updateNowPlaying: @Sendable (_ info: NowPlayingInfo) async -> Void
}

extension AudioPlayerClient: DependencyKey {
    public static let liveValue = AudioPlayerClient.avPlayer
    public static let previewValue = AudioPlayerClient(
        load: { _ in }, play: {}, pause: {}, seek: { _ in }, setRate: { _ in }, stop: {}, events: { .finished }, updateNowPlaying: { _ in }
    )
}

extension DependencyValues {
    public var audioPlayer: AudioPlayerClient {
        get { self[AudioPlayerClient.self] }
        set { self[AudioPlayerClient.self] = newValue }
    }
}
