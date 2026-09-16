import CryptoKit
import Foundation
import Models

extension ScriptureAudioClient {
    /// Production audio: in Portuguese, the backend's Google Cloud voice reads the translation
    /// on screen (no Portuguese recordings exist for these translations); in English, the
    /// helloao recordings. A Portuguese TTS failure is surfaced to the listener.
    public static func live(language: BookLanguage) -> ScriptureAudioClient {
        guard language == .portuguese else { return .helloAO(language: language) }
        return cloudPortuguese
    }

    /// Speak the exact cached reading translation via the backend's `POST /v1/tts`; never
    /// substitute English. New generation is requested only for the chapter the listener opens.
    static let cloudPortuguese = ScriptureAudioClient(chapterAudio: { bookID, chapter in
        let reference = PassageReference(bookId: bookID, chapter: chapter)
        let audio = try await CloudSpeechRenderer.shared.chapterAudio(reference)
        return audio
    })
}

private actor CloudSpeechRenderer {
    static let shared = CloudSpeechRenderer()

    // Disk access and MP3 processing stay on this actor, away from UI rendering.
    /// Cached files are reused; rendering the same chapter twice at once is
    /// collapsed into one job (multiple taps can race).
    private var downloads: [String: Task<Void, Never>] = [:]
    private var inFlight: [PassageReference: Task<ChapterAudio, Error>] = [:]

    func chapterAudio(_ reference: PassageReference) async throws -> ChapterAudio {
        if let running = inFlight[reference] { return try await running.value }
        let task = Task<ChapterAudio, Error> {
            let verses = try await BibleClient.liveValue.chapter(bookId: reference.bookId, chapter: reference.chapter)
            guard let first = verses.first else { throw SpeechFailure.emptyAudio }
            let (url, cues) = try await render(verses)
            return ChapterAudio(
                translationId: first.translationId,
                translationName: "Leitura automática · Português",
                reference: reference,
                narrators: [AudioNarrator(id: AudioNarrator.synthesisedPrefix + "pt-BR", name: "Leitura automática", url: url.absoluteString, timingsPath: nil, cues: cues)]
            )
        }
        inFlight[reference] = task
        defer { inFlight[reference] = nil }
        return try await task.value
    }

    /// One MP3 per exact chapter text and server voice version, cached on disk. The backend also caches server-side by
    /// the same text, so a cold local cache (after reinstall, or a pruned entry) still answers
    /// without paying for a new generation — only the round trip.
    func render(_ verses: [BiblePassage]) async throws -> (URL, [AudioCue]) {
        let text = verses.map(\.text).joined(separator: "\n")
        let directory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("CloudSpeech-pt-BR-v1", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        // Cached manifest: one check/hour, last known version while offline. A backend
        // without the new route keeps using legacy files until it is upgraded.
        let version: String?
        do { version = try await VerbumAPI.shared.speechVersion(language: "pt-BR") }
        catch is CancellationError { throw CancellationError() }
        catch { version = nil }
        let identity = "sync-v1\n" + verses.map { String($0.verseStart) }.joined(separator: ",") + "\n" + (version.map { "\($0)\npt-BR\n\(text)" } ?? text)
        let key = SHA256.hash(data: Data(identity.utf8)).map { String(format: "%02x", $0) }.joined()
        let output = directory.appendingPathComponent(key + ".mp3")
        if FileManager.default.fileExists(atPath: output.path) {
            try? FileManager.default.setAttributes([.modificationDate: Date()], ofItemAtPath: output.path)
            let metadata = output.appendingPathExtension("json")
            let cues = (try? JSONDecoder().decode([AudioCue].self, from: Data(contentsOf: metadata))) ?? []
            return (output, AudioCue.validated(cues))
        }
        let statusPath = try await VerbumAPI.shared.startSpeechPlayback(verses: verses, revision: version)
        for _ in 0..<315 {
            try Task.checkCancellation()
            let status = try await VerbumAPI.shared.speechPlaybackStatus(statusPath)
            if status.complete {
                try await save(status, output: output, directory: directory)
                return (output, status.cues ?? [])
            }
            if status.ready {
                if downloads[key] == nil {
                    downloads[key] = Task {
                        defer { downloads[key] = nil }
                        do {
                            for _ in 0..<210 {
                                try await Task.sleep(for: .seconds(3))
                                let status = try await VerbumAPI.shared.speechPlaybackStatus(statusPath)
                                if status.complete { try await save(status, output: output, directory: directory); return }
                            }
                        } catch { /* Playback reports transport errors; incomplete audio is never cached. */ }
                    }
                }
                return (try VerbumAPI.shared.playbackURL(status.playlistPath), [])
            }
            try await Task.sleep(for: .seconds(2))
        }
        throw SpeechFailure.emptyAudio
    }

    private func save(_ status: SpeechPlaybackStatus, output: URL, directory: URL) async throws {
        let audio = try await VerbumAPI.shared.speechPlaybackData(status.audioPath)
        guard !audio.isEmpty, audio.count <= 64 * 1024 * 1024 else { throw SpeechFailure.emptyAudio }
        try audio.write(to: output, options: .atomic)
        try JSONEncoder().encode(status.cues ?? []).write(to: output.appendingPathExtension("json"), options: .atomic)
        prune(directory, keeping: output)
    }

    /// Only generated files, newest first: at most 40 chapters (MP3 at 128 kbit/s is ~1 MB per
    /// minute; Psalm 119 is ~13 MB — well under the server's per-chapter cap).
    private func prune(_ directory: URL, keeping output: URL) {
        let files = (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.contentModificationDateKey, .fileSizeKey])) ?? []
        let ordered = files.filter { $0.pathExtension == "mp3" && !$0.lastPathComponent.hasSuffix(".partial.mp3") }.sorted {
            ((try? $0.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast) >
            ((try? $1.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast)
        }
        var bytes = 0
        for (index, file) in ordered.enumerated() {
            bytes += (try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
            if file != output && (index >= 40 || bytes > 150_000_000) { try? FileManager.default.removeItem(at: file); try? FileManager.default.removeItem(at: file.appendingPathExtension("json")) }
        }
    }
}

enum SpeechFailure: Error { case emptyAudio }
