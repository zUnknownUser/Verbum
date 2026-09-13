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
                        return audio
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
