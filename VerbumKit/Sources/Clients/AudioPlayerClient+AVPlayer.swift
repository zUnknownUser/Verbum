import AVFoundation
import Foundation
import MediaPlayer
import UIKit

extension AudioPlayerClient {
    /// AVPlayer with a spoken-audio session, Now Playing info and remote
    /// commands — so playback continues in the background and can be driven
    /// from the lock screen, Control Center and headphones.
    public static let avPlayer: AudioPlayerClient = {
        let engine = AVPlayerEngine()
        return AudioPlayerClient(
            load: { url in await engine.load(url) },
            play: { await engine.play() },
            pause: { await engine.pause() },
            seek: { time in await engine.seek(to: time) },
            setRate: { rate in await engine.setRate(rate) },
            stop: { await engine.stop() },
            events: { engine.events() },
            updateNowPlaying: { info in await engine.updateNowPlaying(info) }
        )
    }()
}

@MainActor
final class AVPlayerEngine {
    private let player = AVPlayer()
    private var timeObserver: Any?
    private var statusObservation: NSKeyValueObservation?
    private var rateObservation: NSKeyValueObservation?
    private var endObserver: NSObjectProtocol?
    private var continuation: AsyncStream<AudioPlayerEvent>.Continuation?
    private var subscriptionID: UUID?
    private var preferredRate: Float = 1
    private var commandsInstalled = false
    private var artwork: MPMediaItemArtwork?
    private var reportedDuration: Double = -1

    nonisolated init() {}

    nonisolated func events() -> AsyncStream<AudioPlayerEvent> {
        let id = UUID()
        return AsyncStream { continuation in
            Task { @MainActor in
                self.continuation?.finish()
                self.subscriptionID = id
                self.continuation = continuation
                self.installRemoteCommands()
                // Subscribing after playback starts must still report the
                // current state; KVO only reports subsequent changes.
                if let item = self.player.currentItem {
                    if item.status == .readyToPlay {
                        let duration = self.playbackDuration(item)
                        continuation.yield(.ready(duration: duration.isFinite ? duration : 0))
                    } else if item.status == .failed {
                        continuation.yield(.failed)
                    }
                }
                continuation.yield(.playing(self.player.timeControlStatus == .playing))
            }
            continuation.onTermination = { _ in
                Task { @MainActor in
                    // A cancelled chapter's stream must not detach the next one.
                    guard self.subscriptionID == id else { return }
                    self.continuation = nil
                    self.subscriptionID = nil
                }
            }
        }
    }

    func load(_ url: URL) async {
        // Activating the session can block for a while (route changes, other apps'
        // audio); never on the main thread.
        await AudioSessionActivation.activate()

        teardownItemObservers()
        reportedDuration = -1
        let item = AVPlayerItem(url: url)
        if url.pathExtension == "m3u8" { item.preferredForwardBufferDuration = 3 }
        player.replaceCurrentItem(with: item)

        statusObservation = item.observe(\.status, options: [.initial, .new]) { [weak self] item, _ in
            Task { @MainActor [weak self] in
                guard let self, self.player.currentItem === item else { return }
                switch item.status {
                case .readyToPlay:
                    let seconds = self.playbackDuration(item)
                    self.continuation?.yield(.ready(duration: seconds.isFinite ? seconds : 0))
                case .failed:
                    self.continuation?.yield(.failed)
                default:
                    break
                }
            }
        }
        rateObservation = player.observe(\.timeControlStatus, options: [.initial, .new]) { [weak self] player, _ in
            Task { @MainActor [weak self] in self?.continuation?.yield(.playing(player.timeControlStatus == .playing)) }
        }
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            Task { @MainActor [weak self] in self?.continuation?.yield(.ended) }
        }
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 1, preferredTimescale: 600), queue: .main) { [weak self] time in
            Task { @MainActor [weak self] in
                guard let self, self.player.timeControlStatus == .playing, time.seconds.isFinite else { return }
                if let item = self.player.currentItem {
                    let duration = self.playbackDuration(item)
                    if duration.isFinite, duration > 0, abs(duration - self.reportedDuration) > 0.2 {
                        self.reportedDuration = duration
                        self.continuation?.yield(.ready(duration: duration))
                    }
                }
                self.continuation?.yield(.time(time.seconds))
            }
        }
    }

    private func playbackDuration(_ item: AVPlayerItem) -> Double {
        let seconds = item.duration.seconds
        if seconds.isFinite { return seconds }
        return item.seekableTimeRanges.last.map { CMTimeRangeGetEnd($0.timeRangeValue).seconds } ?? 0
    }

    func play() {
        player.playImmediately(atRate: preferredRate)
    }

    func pause() {
        player.pause()
    }

    func seek(to time: TimeInterval) async {
        await player.seek(to: CMTime(seconds: time, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
    }

    func setRate(_ rate: Float) {
        preferredRate = rate
        if player.rate > 0 { player.rate = rate }
    }

    func stop() {
        player.pause()
        player.replaceCurrentItem(with: nil)
        teardownItemObservers()
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        MPNowPlayingInfoCenter.default().playbackState = .stopped
        AudioSessionActivation.deactivate()
    }

    func updateNowPlaying(_ info: NowPlayingInfo) {
        installRemoteCommands()
        if artwork == nil {
            let size = CGSize(width: 512, height: 512)
            let image = UIGraphicsImageRenderer(size: size).image { context in
                UIColor.systemBackground.setFill()
                context.fill(CGRect(origin: .zero, size: size))
                UIImage(systemName: "book.closed", withConfiguration: UIImage.SymbolConfiguration(pointSize: 230))?
                    .withTintColor(.label, renderingMode: .alwaysOriginal)
                    .draw(in: CGRect(x: 128, y: 128, width: 256, height: 256))
            }
            // MediaPlayer calls this on its own queue; a closure born inside a
            // @MainActor method would be main-isolated and trap there.
            artwork = MPMediaItemArtwork(boundsSize: size) { @Sendable _ in image }
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: info.title,
            MPMediaItemPropertyArtist: info.subtitle,
            MPMediaItemPropertyAlbumTitle: "Verbum",
            MPMediaItemPropertyArtwork: artwork as Any,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: info.elapsed,
            MPMediaItemPropertyPlaybackDuration: info.duration,
            MPNowPlayingInfoPropertyPlaybackRate: info.rate,
            MPNowPlayingInfoPropertyDefaultPlaybackRate: preferredRate,
            MPNowPlayingInfoPropertyIsLiveStream: false,
            MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.audio.rawValue,
        ]
        MPNowPlayingInfoCenter.default().playbackState = info.rate > 0 ? .playing : .paused
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.isEnabled = true
        center.pauseCommand.isEnabled = true
        center.togglePlayPauseCommand.isEnabled = true
        center.skipForwardCommand.isEnabled = info.duration > 0
        center.skipBackwardCommand.isEnabled = info.duration > 0
        center.changePlaybackPositionCommand.isEnabled = info.duration > 0
    }

    private func installRemoteCommands() {
        guard !commandsInstalled else { return }
        commandsInstalled = true
        UIApplication.shared.beginReceivingRemoteControlEvents()
        let center = MPRemoteCommandCenter.shared()
        // Same rule as the artwork: MediaRemote may call these off the main
        // thread, so the handlers are @Sendable and hop to the actor themselves.
        func remote(_ command: AudioPlayerEvent.RemoteCommand) {
            Task { @MainActor [weak self] in self?.continuation?.yield(.remote(command)) }
        }
        center.playCommand.addTarget { @Sendable _ in remote(.play); return .success }
        center.pauseCommand.addTarget { @Sendable _ in remote(.pause); return .success }
        center.togglePlayPauseCommand.addTarget { @Sendable _ in remote(.togglePlayPause); return .success }
        center.skipForwardCommand.preferredIntervals = [15]
        center.skipForwardCommand.addTarget { @Sendable _ in remote(.skipForward); return .success }
        center.skipBackwardCommand.preferredIntervals = [15]
        center.skipBackwardCommand.addTarget { @Sendable _ in remote(.skipBackward); return .success }
        center.changePlaybackPositionCommand.addTarget { @Sendable event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            remote(.seek(event.positionTime))
            return .success
        }
    }

    private func teardownItemObservers() {
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        timeObserver = nil
        statusObservation = nil
        rateObservation = nil
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = nil
    }
}

/// The shared audio session, touched off the main thread: `setActive` is
/// synchronous and can stall the UI (AVAudioSession warns about exactly this).
enum AudioSessionActivation {
    static func activate() async {
        await Task.detached(priority: .userInitiated) {
            let session = AVAudioSession.sharedInstance()
            try? session.setCategory(.playback, mode: .spokenAudio, policy: .longFormAudio)
            try? session.setActive(true)
        }.value
    }

    static func deactivate() {
        Task.detached(priority: .utility) {
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        }
    }
}
