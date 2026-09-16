import ComposableArchitecture
import Foundation
import Models

/// Which recordings exist for a chapter. Audio follows the translation being
/// read when it has any; otherwise the English BSB recordings are offered,
/// labelled as such — the mismatch is shown, never hidden.
@DependencyClient
public struct ScriptureAudioClient: Sendable {
    public var chapterAudio: @Sendable (_ bookId: BookID, _ chapter: Int) async throws -> ChapterAudio?
}

extension ScriptureAudioClient: DependencyKey {
    /// Portuguese: the device reads the translation on screen; English: helloao recordings.
    public static let liveValue: ScriptureAudioClient = .live(language: .current)
    public static let previewValue = ScriptureAudioClient(chapterAudio: { _, _ in nil })
}

extension DependencyValues {
    public var scriptureAudio: ScriptureAudioClient {
        get { self[ScriptureAudioClient.self] }
        set { self[ScriptureAudioClient.self] = newValue }
    }
}

extension ScriptureAudioClient {
    /// Translations known to carry recordings on helloao, in preference order after the reading one.
    static let recordedFallbacks = ["BSB"]

    public static func helloAO(language: BookLanguage, transport: @escaping HelloAOBibleClient.Transport = HelloAOBibleClient.urlSession) -> ScriptureAudioClient {
        let reading = HelloAOTranslation.id(for: language)
        let candidates = [reading] + recordedFallbacks.filter { $0 != reading }
        return ScriptureAudioClient(
            chapterAudio: { bookId, chapter in
                guard let usfm = HelloAOBooks.usfmByOSIS[bookId] else { return nil }
                for translation in candidates {
                    let url = HelloAOBibleClient.baseURL.appendingPathComponent("\(translation)/\(usfm)/\(chapter).json")
                    let data: Data
                    do { data = try await transport(url) } catch { continue }
                    if let audio = try? HelloAOChapterAudio.parse(data, bookId: bookId, chapter: chapter), !audio.narrators.isEmpty {
                        var narrators: [AudioNarrator] = []
                        for narrator in audio.narrators {
                            var cues: [AudioCue] = []
                            if let path = narrator.timingsPath, let url = URL(string: path, relativeTo: HelloAOBibleClient.baseURL)?.absoluteURL,
                               url.scheme == "https", url.host == HelloAOBibleClient.baseURL.host {
                                do { cues = try HelloAOChapterAudio.timings(try await transport(url), narrator: narrator, translation: audio.translationId, book: usfm, chapter: chapter) }
                                catch is CancellationError { throw CancellationError() }
                                catch { /* Audio stays playable without timings. */ }
                            }
                            narrators.append(AudioNarrator(id: narrator.id, name: narrator.name, url: narrator.url, timingsPath: narrator.timingsPath, cues: cues))
                        }
                        return ChapterAudio(translationId: audio.translationId, translationName: audio.translationName, reference: audio.reference, narrators: narrators)
                    }
                }
                return nil
            }
        )
    }
}

/// Reads `thisChapterAudioLinks` / `thisChapterAudioTimings` from a chapter response.
enum HelloAOChapterAudio {
    private struct Response: Decodable {
        let translation: Translation
        let thisChapterAudioLinks: [String: String]?
        let thisChapterAudioTimings: [String: String]?

        struct Translation: Decodable { let id: String; let name: String }
    }

    static func timings(_ data: Data, narrator: AudioNarrator, translation: String, book: String, chapter: Int) throws -> [AudioCue] {
        struct Wire: Decodable { let translationId: String; let bookId: String; let chapterNumber: Int; let reader: String; let audioLink: String; let verses: [Double] }
        let wire = try JSONDecoder().decode(Wire.self, from: data)
        guard wire.translationId == translation, wire.bookId == book, wire.chapterNumber == chapter, wire.reader == narrator.id, wire.audioLink == narrator.url else { return [] }
        return AudioCue.validated(wire.verses.enumerated().map { index, start in
            AudioCue(verseStart: index + 1, verseEnd: index + 1, start: start, end: index + 1 < wire.verses.count ? wire.verses[index + 1] : nil)
        })
    }

    static func parse(_ data: Data, bookId: BookID, chapter: Int) throws -> ChapterAudio {
        let response = try JSONDecoder().decode(Response.self, from: data)
        let links = response.thisChapterAudioLinks ?? [:]
        let narrators = links.keys.sorted().map { id in
            AudioNarrator(id: id, name: id.prefix(1).uppercased() + id.dropFirst(), url: links[id]!, timingsPath: response.thisChapterAudioTimings?[id])
        }
        return ChapterAudio(
            translationId: response.translation.id,
            translationName: response.translation.name,
            reference: PassageReference(bookId: bookId, chapter: chapter),
            narrators: narrators
        )
    }
}
