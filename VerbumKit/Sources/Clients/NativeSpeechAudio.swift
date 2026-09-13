import AVFoundation
import CryptoKit
import Foundation
import Models

extension ScriptureAudioClient {
    /// Production audio: in Portuguese, the device reads the translation on
    /// screen aloud (no Portuguese recordings exist for these translations);
    /// in English, the helloao recordings. Without a Portuguese voice the
    /// English recordings are offered, labelled as such — never passed off as
    /// the reading.
    public static func live(language: BookLanguage) -> ScriptureAudioClient {
        guard language == .portuguese else { return .helloAO(language: language) }
        let recordings = ScriptureAudioClient.helloAO(language: language)
        return ScriptureAudioClient(chapterAudio: { bookId, chapter in
            do {
                return try await nativePortuguese.chapterAudio(bookId: bookId, chapter: chapter)
            } catch SpeechFailure.voiceUnavailable {
                return try await recordings.chapterAudio(bookId: bookId, chapter: chapter)
            }
        })
    }

    /// Speak the exact cached reading translation; never substitute English.
    /// The next chapter is rendered in the background once this one is, so
    /// chaining does not wait.
    static let nativePortuguese = ScriptureAudioClient(chapterAudio: { bookID, chapter in
        let reference = PassageReference(bookId: bookID, chapter: chapter)
        let audio = try await NativeSpeechRenderer.chapterAudio(reference)
        if let next = NativeSpeechRenderer.next(after: reference) {
            Task.detached(priority: .utility) { _ = try? await NativeSpeechRenderer.chapterAudio(next) }
        }
        return audio
    })
}

@MainActor
private enum NativeSpeechRenderer {
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

    static func render(_ text: String) async throws -> URL {
        guard let voice = AVSpeechSynthesisVoice.speechVoices()
            .filter({ $0.language == "pt-BR" })
            .sorted(by: { $0.quality.rawValue > $1.quality.rawValue }).first else {
            throw SpeechFailure.voiceUnavailable
        }
        let directory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("NativeSpeech-v2", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let key = SHA256.hash(data: Data((voice.identifier + "\n" + text).utf8)).map { String(format: "%02x", $0) }.joined()
        let output = directory.appendingPathComponent(key + ".m4a")
        if FileManager.default.fileExists(atPath: output.path) {
            try? FileManager.default.setAttributes([.modificationDate: Date()], ofItemAtPath: output.path)
            return output
        }
        let temporary = directory.appendingPathComponent(UUID().uuidString + ".partial.m4a")
        let session = SpeechSession()
        let sink = SpeechFileSink(url: temporary)
        let voiceID = voice.identifier
        let timeout = Task {
            try await Task.sleep(for: .seconds(90))
            sink.cancel()
            session.stop()
        }
        defer { timeout.cancel() }
        do {
            try await withTaskCancellationHandler { @MainActor in
                try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                    if sink.begin(continuation) {
                        session.start(text: text, voiceID: voiceID, sink: sink)
                    }
                }
            } onCancel: {
                sink.cancel()
                Task { @MainActor in session.stop() }
            }
            try Task.checkCancellation()
            if FileManager.default.fileExists(atPath: output.path) {
                try? FileManager.default.removeItem(at: temporary)
            } else {
                try FileManager.default.moveItem(at: temporary, to: output)
            }
            prune(directory, keeping: output)
            return output
        } catch {
            session.stop()
            try? FileManager.default.removeItem(at: temporary)
            throw error
        }
    }

    /// Only generated files, newest first: at most 40 chapters / ~150 MB (AAC
    /// at 64 kbit/s is ~0.5 MB per minute; Psalm 119 is ~7 MB).
    private static func prune(_ directory: URL, keeping output: URL) {
        let files = (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.contentModificationDateKey, .fileSizeKey])) ?? []
        let ordered = files.filter { $0.pathExtension == "m4a" && !$0.lastPathComponent.hasSuffix(".partial.m4a") }.sorted {
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

enum SpeechFailure: Error { case voiceUnavailable, emptyAudio, formatChanged }

/// Drives one `AVSpeechSynthesizer.write` and reports its end through the
/// delegate. Two things learned the hard way:
/// - the buffer callback runs off the main thread, so it must be `@Sendable`
///   (a closure born in a `@MainActor` context traps there in Swift 6);
/// - the synthesizer hands out **empty buffers between paragraphs**, not only
///   at the very end — treating the first one as "done" truncated chapters to
///   their first verses. The end is `didFinish`, nothing else.
@MainActor
private final class SpeechSession: NSObject, AVSpeechSynthesizerDelegate {
    private let synthesizer = AVSpeechSynthesizer()
    private var sink: SpeechFileSink?

    func start(text: String, voiceID: String, sink: SpeechFileSink) {
        self.sink = sink
        synthesizer.delegate = self
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(identifier: voiceID)
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        synthesizer.write(utterance) { @Sendable buffer in sink.accept(buffer) }
    }

    func stop() { synthesizer.stopSpeaking(at: .immediate) }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in self.sink?.finish() }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in self.sink?.cancel() }
    }
}

/// AVSpeechSynthesizer's callback may run off-main. Serialize file writes and
/// completion/cancellation without sending non-Sendable audio buffers to tasks.
private final class SpeechFileSink: @unchecked Sendable {
    private let lock = NSLock()
    private let url: URL
    private var file: AVAudioFile?
    private var continuation: CheckedContinuation<Void, Error>?
    private var finished = false
    init(url: URL) { self.url = url }

    func begin(_ continuation: CheckedContinuation<Void, Error>) -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard !finished else { continuation.resume(throwing: CancellationError()); return false }
        self.continuation = continuation
        return true
    }

    /// Appends one buffer. Empty buffers are paragraph marks, not the end.
    func accept(_ buffer: AVAudioBuffer) {
        lock.lock(); defer { lock.unlock() }
        guard !finished, let pcm = buffer as? AVAudioPCMBuffer, pcm.frameLength > 0 else { return }
        do {
            if file == nil {
                // AAC on disk (a Float32 CAF of Psalm 119 was 81 MB); the file encodes as it is written.
                let settings: [String: Any] = [
                    AVFormatIDKey: kAudioFormatMPEG4AAC,
                    AVSampleRateKey: pcm.format.sampleRate,
                    AVNumberOfChannelsKey: Int(pcm.format.channelCount),
                    AVEncoderBitRateKey: 64_000,
                ]
                file = try AVAudioFile(forWriting: url, settings: settings, commonFormat: pcm.format.commonFormat, interleaved: pcm.format.isInterleaved)
            }
            guard let file, file.processingFormat == pcm.format else {
                complete(.failure(SpeechFailure.formatChanged))
                return
            }
            try file.write(from: pcm)
        } catch { complete(.failure(error)) }
    }

    /// The synthesizer's `didFinish`: the file is whole.
    func finish() {
        lock.lock(); defer { lock.unlock() }
        complete(file == nil ? .failure(SpeechFailure.emptyAudio) : .success(()))
    }

    func cancel() {
        lock.lock(); defer { lock.unlock() }
        complete(.failure(CancellationError()))
    }

    private func complete(_ result: Result<Void, Error>) {
        guard !finished else { return }
        finished = true
        file = nil
        continuation?.resume(with: result)
        continuation = nil
    }
}
