import Clients
import ComposableArchitecture
import Foundation
import Models
import Testing
@testable import Features

@MainActor
@Suite struct AudioPlayerFeatureTests {
    nonisolated static let john3 = PassageReference(bookId: "John", chapter: 3)
    nonisolated static let david = AudioNarrator(id: "david", name: "David", url: "https://example.test/JHN/3/david.mp3", timingsPath: nil)
    nonisolated static let hays = AudioNarrator(id: "hays", name: "Hays", url: "https://example.test/JHN/3/hays.mp3", timingsPath: nil)
    nonisolated static func audio(_ reference: PassageReference) -> ChapterAudio {
        ChapterAudio(translationId: "BSB", translationName: "Berean Standard Bible", reference: reference, narrators: [david, hays])
    }

    /// A scripted player: records calls, lets the test feed events.
    final class FakePlayer: Sendable {
        let calls = LockIsolated<[String]>([])
        let nowPlaying = LockIsolated<[NowPlayingInfo]>([])
        let (stream, continuation) = AsyncStream<AudioPlayerEvent>.makeStream()

        var client: AudioPlayerClient {
            AudioPlayerClient(
                load: { [calls] url in calls.withValue { $0.append("load \(url.lastPathComponent)") } },
                play: { [calls] in calls.withValue { $0.append("play") } },
                pause: { [calls] in calls.withValue { $0.append("pause") } },
                seek: { [calls] t in calls.withValue { $0.append("seek \(Int(t))") } },
                setRate: { [calls] r in calls.withValue { $0.append("rate \(r)") } },
                stop: { [calls] in calls.withValue { $0.append("stop") } },
                events: { [stream] in stream },
                updateNowPlaying: { [nowPlaying] info in nowPlaying.withValue { $0.append(info) } }
            )
        }
    }

    @Test func playLoadsTheChapterAndStartsTheFirstNarrator() async {
        let player = FakePlayer()
        let store = TestStore(initialState: AudioPlayerFeature.State()) {
            AudioPlayerFeature()
        } withDependencies: {
            $0.scriptureAudio.chapterAudio = { bookId, chapter in Self.audio(PassageReference(bookId: bookId, chapter: chapter)) }
            $0.audioPlayer = player.client
        }

        await store.send(.play(PassageReference(bookId: "John", chapter: 3, verses: 16...16))) {
            $0.reference = Self.john3
            $0.isLoading = true
        }
        await store.receive(\.audioResponse) {
            $0.audio = Self.audio(Self.john3)
            $0.narrator = Self.david
        }
        player.continuation.yield(.ready(duration: 1337))
        await store.receive(\.event.ready) {
            $0.duration = 1337
            $0.isLoading = false
        }
        player.continuation.yield(.playing(true))
        await store.receive(\.event.playing) { $0.isPlaying = true }
        player.continuation.yield(.time(12))
        await store.receive(\.event.time) { $0.currentTime = 12 }

        #expect(player.calls.value == ["stop", "load david.mp3", "rate 1.0", "play"])
        #expect(player.nowPlaying.value.last?.title == Self.john3.formatted) // device language
        #expect(player.nowPlaying.value.last?.subtitle == "Berean Standard Bible · David")
        #expect(store.state.isPlaying(PassageReference(bookId: "John", chapter: 3, verses: 1...2)))

        await store.send(.stopTapped) { $0 = AudioPlayerFeature.State() }
        #expect(player.calls.value.last == "stop")
    }

    @Test func noRecordingIsAStateNotACrash() async {
        let store = TestStore(initialState: AudioPlayerFeature.State()) {
            AudioPlayerFeature()
        } withDependencies: {
            $0.scriptureAudio.chapterAudio = { _, _ in nil }
            $0.audioPlayer.stop = {}
        }
        await store.send(.play(Self.john3)) {
            $0.reference = Self.john3
            $0.isLoading = true
        }
        await store.receive(\.audioResponse) {
            $0.isLoading = false
            $0.failed = true
        }
        await store.send(.togglePlayPause) // nothing to toggle
    }

    @Test func controlsDriveThePlayerAndNowPlaying() async {
        let player = FakePlayer()
        var state = AudioPlayerFeature.State()
        state.reference = Self.john3
        state.audio = Self.audio(Self.john3)
        state.narrator = Self.david
        state.duration = 100
        state.currentTime = 50
        state.isPlaying = true
        let store = TestStore(initialState: state) {
            AudioPlayerFeature()
        } withDependencies: {
            $0.audioPlayer = player.client
        }

        await store.send(.togglePlayPause)
        await store.send(.skipForward)
        await store.receive(\.seek) { $0.currentTime = 65 }
        await store.send(.skipBackward)
        await store.receive(\.seek) { $0.currentTime = 50 }
        await store.send(.seek(98)) { $0.currentTime = 98 }
        await store.send(.skipForward) // clamps at the end
        await store.receive(\.seek) { $0.currentTime = 100 }
        await store.send(.rateTapped) { $0.rate = 1.25 }
        await store.send(.rateTapped) { $0.rate = 1.5 }
        await store.send(.rateTapped) { $0.rate = 0.8 }
        await store.send(.rateTapped) { $0.rate = 1 }
        #expect(player.calls.value == ["pause", "seek 65", "seek 50", "seek 98", "seek 100", "rate 1.25", "rate 1.5", "rate 0.8", "rate 1.0"])
        #expect(player.nowPlaying.value.last?.elapsed == 100)
        #expect(player.nowPlaying.value.last?.rate == 1)
    }

    @Test func remoteCommandsFromTheLockScreenAreHonoured() async {
        let player = FakePlayer()
        var state = AudioPlayerFeature.State()
        state.reference = Self.john3
        state.audio = Self.audio(Self.john3)
        state.narrator = Self.david
        state.duration = 100
        state.currentTime = 30
        let store = TestStore(initialState: state) {
            AudioPlayerFeature()
        } withDependencies: {
            $0.audioPlayer = player.client
        }
        await store.send(.event(.remote(.play)))
        await store.send(.event(.remote(.pause)))
        await store.send(.event(.remote(.skipForward)))
        await store.receive(\.skipForward)
        await store.receive(\.seek) { $0.currentTime = 45 }
        await store.send(.event(.remote(.seek(10))))
        await store.receive(\.seek) { $0.currentTime = 10 }
        #expect(player.calls.value == ["play", "pause", "seek 45", "seek 10"])
    }

    @Test func endOfChapterContinuesIntoTheNext() async {
        var initial = AudioPlayerFeature.State()
        initial.reference = Self.john3
        initial.audio = Self.audio(Self.john3)
        initial.narrator = Self.david
        let store = TestStore(initialState: initial) {
            AudioPlayerFeature()
        } withDependencies: {
            $0.scriptureAudio.chapterAudio = { _, _ in nil }
            $0.audioPlayer.stop = {}
        }

        await store.send(.event(.ended))
        await store.receive(\.play, PassageReference(bookId: "John", chapter: 4)) {
            $0.reference = PassageReference(bookId: "John", chapter: 4)
            $0.audio = nil
            $0.isLoading = true
            $0.failed = false
        }
        await store.receive(\.audioResponse) {
            $0.isLoading = false
            $0.failed = true
        }
    }

    @Test func switchingNarratorRestartsWithTheSameChapter() async {
        let player = FakePlayer()
        var state = AudioPlayerFeature.State()
        state.reference = Self.john3
        state.audio = Self.audio(Self.john3)
        state.narrator = Self.david
        state.currentTime = 40
        let store = TestStore(initialState: state) {
            AudioPlayerFeature()
        } withDependencies: {
            $0.audioPlayer = player.client
        }
        await store.send(.narratorSelected(Self.hays)) {
            $0.narrator = Self.hays
            $0.currentTime = 0
            $0.isLoading = true
        }
        await store.send(.narratorSelected(Self.hays)) // no-op
        #expect(player.calls.value == ["load hays.mp3", "rate 1.0", "play"])
        await store.send(.stopTapped) { $0 = AudioPlayerFeature.State() }
    }

    @Test func miniPlayerTapAsksTheShellToOpenTheChapter() async {
        var state = AudioPlayerFeature.State()
        state.reference = Self.john3
        let store = TestStore(initialState: state) { AudioPlayerFeature() }
        await store.send(.chapterTapped)
        await store.receive(\.delegate.openChapter, Self.john3)
    }
}

@MainActor
@Suite struct ListenIntegrationTests {
    @Test func readerListenStartsTheGlobalPlayerAndTogglesWhenAlreadyOn() async {
        let store = TestStore(initialState: AppFeature.State()) {
            AppFeature()
        } withDependencies: {
            $0.scriptureAudio.chapterAudio = { _, _ in nil }
            $0.audioPlayer.stop = {}
        }
        let john3 = PassageReference(bookId: "John", chapter: 3)
        let verse = store.state.home.dailyVerse.reference
        await store.send(.home(.dailyVerse(.openTapped)))
        await store.receive(\.home.dailyVerse.delegate.openPassage)
        await store.receive(\.home.delegate.openPassage) {
            $0.homePath[id: 0] = .reader(ScriptureFeature.State(reference: verse))
        }
        await store.send(.homePath(.element(id: 0, action: .reader(.reader(.listenTapped)))))
        await store.receive(\.homePath[id: 0].reader.reader.delegate.listen)
        await store.receive(\.homePath[id: 0].reader.delegate.listen)
        await store.receive(\.audio.play) {
            $0.audio.reference = PassageReference(bookId: verse.bookId, chapter: verse.chapter)
            $0.audio.isLoading = true
            $0.isListening = true
        }
        await store.receive(\.audio.audioResponse) {
            $0.audio.isLoading = false
            $0.audio.failed = true
        }
        // Same chapter again: toggles instead of reloading.
        await store.send(.homePath(.element(id: 0, action: .reader(.reader(.listenTapped)))))
        await store.receive(\.homePath[id: 0].reader.reader.delegate.listen)
        await store.receive(\.homePath[id: 0].reader.delegate.listen)
        await store.receive(\.audio.togglePlayPause)
        _ = john3
    }
}
