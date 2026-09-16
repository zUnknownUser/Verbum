import Foundation
import Models

public struct SpeechPlaybackStatus: Decodable, Sendable {
    public let ready: Bool
    public let complete: Bool
    public let playlistPath: String
    public let audioPath: String
    public let cues: [AudioCue]?
}

extension VerbumAPI {
    public func startSpeechPlayback(verses: [BiblePassage], revision: String?) async throws -> String {
        struct Verse: Encodable { let number: Int; let text: String }
        struct Body: Encodable { let bookId: String; let chapter: Int; let translation: String; let text: String; let language: String; let revision: String?; let verses: [Verse] }
        struct Reply: Decodable { let statusPath: String }
        guard let first = verses.first else { throw VerbumAPIError.malformedResponse }
        let response: Reply = try await post("/v1/tts/playback", body: Body(bookId: first.bookId, chapter: first.chapter, translation: first.translationId, text: verses.map(\.text).joined(separator: "\n"), language: "pt-BR", revision: revision, verses: verses.map { Verse(number: $0.verseStart, text: $0.text) }))
        _ = try playbackURL(response.statusPath)
        return response.statusPath
    }
    public func playbackURL(_ path: String) throws -> URL {
        let pieces = path.split(separator: "/")
        guard pieces.count == 5, pieces[0] == "v1", pieces[1] == "tts", pieces[2] == "playback",
              pieces[3].count == 64, pieces[3].allSatisfy({ $0.isHexDigit }),
              ["status", "index.m3u8", "chapter.mp3"].contains(String(pieces[4])) else { throw VerbumAPIError.malformedResponse }
        return baseURL.appendingPathComponent(path)
    }
    public func speechPlaybackStatus(_ path: String) async throws -> SpeechPlaybackStatus {
        let data = try await speechPlaybackData(path)
        return try JSONDecoder().decode(SpeechPlaybackStatus.self, from: data)
    }
    public func speechPlaybackData(_ path: String) async throws -> Data {
        let request = URLRequest(url: try playbackURL(path), cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30)
        return try await send(request)
    }
}
