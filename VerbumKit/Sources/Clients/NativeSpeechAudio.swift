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
        let audio = try await CloudSpeechRenderer.chapterAudio(reference)
        return audio
    })
}

@MainActor
private enum CloudSpeechRenderer {
    /// Cached files are reused; rendering the same chapter twice at once is
    /// collapsed into one job (multiple taps can race).
    private static var inFlight: [PassageReference: Task<ChapterAudio, Error>] = [:]

    static func chapterAudio(_ reference: PassageReference) async throws -> ChapterAudio {
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
    static func render(_ verses: [BiblePassage]) async throws -> (URL, [AudioCue]) {
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
        let audio: Data
        let cues: [AudioCue]
        // A failed paid request is not retried through a second synthesis endpoint.
        (audio, cues) = try await VerbumAPI.shared.synthesizeChapterSpeech(verses: verses, language: "pt-BR", revision: version)
        guard !audio.isEmpty else { throw SpeechFailure.emptyAudio }
        try Task.checkCancellation()
        let temporary = directory.appendingPathComponent(UUID().uuidString + ".partial.mp3")
        try audio.write(to: temporary, options: .atomic)
        if FileManager.default.fileExists(atPath: output.path) {
            try? FileManager.default.removeItem(at: temporary)
        } else {
            try FileManager.default.moveItem(at: temporary, to: output)
        }
        try? JSONEncoder().encode(cues).write(to: output.appendingPathExtension("json"), options: .atomic)
        prune(directory, keeping: output)
        return (output, cues)
    }

    /// Only generated files, newest first: at most 40 chapters (MP3 at 128 kbit/s is ~1 MB per
    /// minute; Psalm 119 is ~13 MB — well under the server's per-chapter cap).
    private static func prune(_ directory: URL, keeping output: URL) {
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
