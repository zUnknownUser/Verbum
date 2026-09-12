import AVFoundation
import CryptoKit
import Foundation
import Models

extension ScriptureAudioClient {
    /// Speak the exact cached reading translation; never substitute English.
    static let nativePortuguese = ScriptureAudioClient(chapterAudio: { bookID, chapter in
        let verses = try await BibleClient.liveValue.chapter(bookId: bookID, chapter: chapter)
        guard let first = verses.first else { return nil }
        let text = verses.map(\.text).joined(separator: "\n")
        let url = try await NativeSpeechRenderer.render(text)
        return ChapterAudio(
            translationId: first.translationId,
            translationName: "Leitura automática · Português",
            reference: PassageReference(bookId: bookID, chapter: chapter),
            narrators: [AudioNarrator(id: "native.pt-BR", name: "Leitura automática", url: url.absoluteString, timingsPath: nil)]
        )
    })
}

@MainActor
private enum NativeSpeechRenderer {
    static func render(_ text: String) async throws -> URL {
        guard let voice = AVSpeechSynthesisVoice.speechVoices()
            .filter({ $0.language == "pt-BR" })
            .sorted(by: { $0.quality.rawValue > $1.quality.rawValue }).first else {
            throw SpeechFailure.voiceUnavailable
        }
        let directory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("NativeSpeech-v1", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let key = SHA256.hash(data: Data((voice.identifier + "\n" + text).utf8)).map { String(format: "%02x", $0) }.joined()
        let output = directory.appendingPathComponent(key + ".caf")
        if FileManager.default.fileExists(atPath: output.path) {
            try? FileManager.default.setAttributes([.modificationDate: Date()], ofItemAtPath: output.path)
            return output
        }
        let temporary = directory.appendingPathComponent(UUID().uuidString + ".partial.caf")
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

    /// Only generated CAF files, at most four chapters / approximately 100 MB.
    private static func prune(_ directory: URL, keeping output: URL) {
        let files = (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.contentModificationDateKey, .fileSizeKey])) ?? []
        let ordered = files.filter { $0.pathExtension == "caf" && !$0.lastPathComponent.hasSuffix(".partial.caf") }.sorted {
            ((try? $0.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast) >
            ((try? $1.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast)
        }
        var bytes = 0
        for (index, file) in ordered.enumerated() {
            bytes += (try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
            if file != output && (index >= 4 || bytes > 100_000_000) { try? FileManager.default.removeItem(at: file) }
        }
    }
}

private enum SpeechFailure: Error { case voiceUnavailable, emptyAudio }

@MainActor
private final class SpeechSession {
    private let synthesizer = AVSpeechSynthesizer()
    func start(text: String, voiceID: String, sink: SpeechFileSink) {
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(identifier: voiceID)
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        synthesizer.write(utterance) { sink.accept($0) }
    }
    func stop() { synthesizer.stopSpeaking(at: .immediate) }
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

    func accept(_ buffer: AVAudioBuffer) {
        lock.lock(); defer { lock.unlock() }
        guard !finished else { return }
        guard let pcm = buffer as? AVAudioPCMBuffer else { complete(.failure(SpeechFailure.emptyAudio)); return }
        if pcm.frameLength == 0 {
            complete(file == nil ? .failure(SpeechFailure.emptyAudio) : .success(()))
            return
        }
        do {
            if file == nil { file = try AVAudioFile(forWriting: url, settings: pcm.format.settings, commonFormat: pcm.format.commonFormat, interleaved: pcm.format.isInterleaved) }
            try file?.write(from: pcm)
        } catch { complete(.failure(error)) }
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
