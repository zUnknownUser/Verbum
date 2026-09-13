import CryptoKit
import Foundation
import Models

extension ScriptureAudioClient {
    /// Production audio: in Portuguese, the backend's Google Cloud voice reads the translation
    /// on screen (no Portuguese recordings exist for these translations); in English, the
    /// helloao recordings. If the cloud voice cannot be reached, the English recordings are
    /// offered, labelled as such — never passed off as the reading.
    public static func live(language: BookLanguage) -> ScriptureAudioClient {
        guard language == .portuguese else { return .helloAO(language: language) }
        let recordings = ScriptureAudioClient.helloAO(language: language)
        return ScriptureAudioClient(chapterAudio: { bookId, chapter in
            do {
                return try await cloudPortuguese.chapterAudio(bookId: bookId, chapter: chapter)
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                return try await recordings.chapterAudio(bookId: bookId, chapter: chapter)
            }
        })
    }

    /// Speak the exact cached reading translation via the backend's `POST /v1/tts`; never
    /// substitute English. The next chapter is rendered in the background once this one is, so
    /// chaining does not wait.
    static let cloudPortuguese = ScriptureAudioClient(chapterAudio: { bookID, chapter in
        let reference = PassageReference(bookId: bookID, chapter: chapter)
        let audio = try await CloudSpeechRenderer.chapterAudio(reference)
        if let next = CloudSpeechRenderer.next(after: reference) {
            Task.detached(priority: .utility) { _ = try? await CloudSpeechRenderer.chapterAudio(next) }
        }
        return audio
    })
}

@MainActor
private enum CloudSpeechRenderer {
    /// Cached files are reused; rendering the same chapter twice at once is
    /// collapsed into one job (the prefetch and a tap can race).
    private static var inFlight: [PassageReference: Task<ChapterAudio, Error>] = [:]

    static func chapterAudio(_ reference: PassageReference) async throws -> ChapterAudio {
        if let running = inFlight[reference] { return try await running.value }
        let task = Task<ChapterAudio, Error> {
            let verses = try await BibleClient.liveValue.chapter(bookId: reference.bookId, chapter: reference.chapter)
            guard let first = verses.first else { throw SpeechFailure.emptyAudio }
            let url = try await render(verses.map(\.text).joined(separator: "\n"))
            return ChapterAudio(
                translationId: first.translationId,
                translationName: "Leitura automática · Português",
                reference: reference,
                narrators: [AudioNarrator(id: AudioNarrator.synthesisedPrefix + "pt-BR", name: "Leitura automática", url: url.absoluteString, timingsPath: nil)]
            )
        }
        inFlight[reference] = task
        defer { inFlight[reference] = nil }
        return try await task.value
    }

    nonisolated static func next(after reference: PassageReference) -> PassageReference? {
        guard let book = BibleBook.book(id: reference.bookId) else { return nil }
        if reference.chapter < book.chapterCount { return PassageReference(bookId: book.id, chapter: reference.chapter + 1) }
        guard let nextBook = BibleBook.canon.first(where: { $0.order == book.order + 1 }) else { return nil }
        return PassageReference(bookId: nextBook.id, chapter: 1)
    }

    /// One MP3 per exact chapter text, cached on disk. The backend also caches server-side by
    /// the same text, so a cold local cache (after reinstall, or a pruned entry) still answers
    /// without paying for a new generation — only the round trip.
    static func render(_ text: String) async throws -> URL {
        let directory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("CloudSpeech-pt-BR-v1", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let key = SHA256.hash(data: Data(text.utf8)).map { String(format: "%02x", $0) }.joined()
        let output = directory.appendingPathComponent(key + ".mp3")
        if FileManager.default.fileExists(atPath: output.path) {
            try? FileManager.default.setAttributes([.modificationDate: Date()], ofItemAtPath: output.path)
            return output
        }
        let audio = try await VerbumAPI.shared.synthesizeSpeech(text: text, language: "pt-BR")
        guard !audio.isEmpty else { throw SpeechFailure.emptyAudio }
        try Task.checkCancellation()
        let temporary = directory.appendingPathComponent(UUID().uuidString + ".partial.mp3")
        try audio.write(to: temporary, options: .atomic)
        if FileManager.default.fileExists(atPath: output.path) {
            try? FileManager.default.removeItem(at: temporary)
        } else {
            try FileManager.default.moveItem(at: temporary, to: output)
        }
        prune(directory, keeping: output)
        return output
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
            if file != output && (index >= 40 || bytes > 150_000_000) { try? FileManager.default.removeItem(at: file) }
        }
    }
}

enum SpeechFailure: Error { case emptyAudio }
