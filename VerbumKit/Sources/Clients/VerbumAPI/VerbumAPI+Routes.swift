import Foundation
import Models

/// The routes of `api/openapi.yaml`, one function each, returning the app's
/// models. Where the wire shape is exactly the model (`EntityDetail`,
/// `GraphSnapshot`, `TimelineEvent`, `BibleEntity`) the model decodes itself;
/// the few envelopes and the shapes that differ have a private wire struct here.
extension VerbumAPI {
    // MARK: entities

    /// `GET /v1/entities/{id}` (§9).
    public func entityDetail(_ id: EntityID, language: BookLanguage = .current) async throws -> EntityDetail {
        try await get("/v1/entities/\(id)", query: [Self.lang(language)])
    }

    /// `GET /v1/entities?type=` (§7). Passage nodes are never listed.
    public func entities(of type: BibleEntityType, language: BookLanguage = .current) async throws -> [BibleEntity] {
        struct Envelope: Decodable { let entities: [BibleEntity] }
        return try await get("/v1/entities", query: [.init(name: "type", value: type.rawValue), Self.lang(language)], as: Envelope.self).entities
    }

    // MARK: graph

    /// `GET /v1/entities/{id}/graph?limit=` (§8, §44). One hop, never the whole graph.
    public func graph(_ id: EntityID, limit: Int, language: BookLanguage = .current) async throws -> GraphSnapshot {
        try await get("/v1/entities/\(id)/graph", query: [.init(name: "limit", value: String(min(max(limit, 1), 48))), Self.lang(language)])
    }

    // MARK: context

    /// `GET /v1/passages/{Book.Chapter}/context` (§10). An optional verse narrows related occurrences:
    /// context is per chapter. Missing coverage is `nil`, never invented (§3.5).
    public func context(_ reference: PassageReference, language: BookLanguage = .current) async throws -> PassageContext? {
        struct Wire: Decodable {
            let reference: PassageReference
            let entities: [BibleEntity]
            let relatedPassages: [PassageReference]
            let sources: [SourceReference]
        }
        do {
            let wire: Wire = try await get("/v1/passages/\(reference.bookId).\(reference.chapter)/context", query: [Self.lang(language)] + (reference.verses.map { [URLQueryItem(name: "verse", value: String($0.lowerBound))] } ?? []))
            return PassageContext(reference: wire.reference, entities: wire.entities, relatedPassages: wire.relatedPassages, sources: wire.sources, isFixture: false)
        } catch VerbumAPIError.problem(.contentUnavailable, _) {
            return nil
        }
    }

    // MARK: timeline

    public struct Timeline: Decodable, Equatable, Sendable {
        public let events: [TimelineEvent]
        /// Names for every id that appears in any `entityIds` — saves a round-trip per chip.
        public let entityNames: [EntityID: String]
    }

    /// `GET /v1/timeline?entity=` (§4.2). Chronological, unknown dates last.
    public func timeline(entity: EntityID? = nil, language: BookLanguage = .current) async throws -> Timeline {
        try await get("/v1/timeline", query: (entity.map { [.init(name: "entity", value: $0)] } ?? []) + [Self.lang(language)])
    }

    // MARK: search

    /// `GET /v1/search?q=` (§27–28). The server serves the entity groups and
    /// Scripture hits; books arrive as OSIS ids and are resolved from the canon.
    public func search(_ query: String, language: BookLanguage = .current) async throws -> SearchResponse {
        struct Wire: Decodable {
            struct Book: Decodable { let id: String }
            let query: String
            let passages: [PassageReference]
            let books: [Book]
            let entities: [BibleEntity]
        }
        let wire: Wire = try await get("/v1/search", query: [.init(name: "q", value: String(query.prefix(200))), Self.lang(language)])
        return SearchResponse(
            query: wire.query,
            passages: wire.passages,
            books: wire.books.compactMap { BibleBook.book(id: $0.id) },
            entities: wire.entities
        )
    }

    // MARK: daily

    public struct DailyVerse: Decodable, Equatable, Sendable {
        /// `YYYY-MM-DD`.
        public let date: String
        public let reference: PassageReference
    }

    /// `GET /v1/daily-verse?from=&days=` — references only; the text comes from
    /// the reader's translation (§14). `from` is `YYYY-MM-DD`.
    public func dailyVerses(from: String, days: Int) async throws -> [DailyVerse] {
        struct Envelope: Decodable { let verses: [DailyVerse] }
        return try await get("/v1/daily-verse", query: [.init(name: "from", value: from), .init(name: "days", value: String(min(max(days, 1), 31)))], as: Envelope.self).verses
    }

    private static func lang(_ language: BookLanguage) -> URLQueryItem {
        URLQueryItem(name: "lang", value: language.rawValue)
    }

    // MARK: speech

    private struct SpeechRequest: Encodable { let text: String; let language: String; let revision: String? }
    private struct SpeechVerse: Encodable { let number: Int; let text: String }
    private struct TimedSpeechRequest: Encodable { let bookId: String; let chapter: Int; let translation: String; let text: String; let language: String; let revision: String?; let verses: [SpeechVerse] }
    public func synthesizeChapterSpeech(verses: [BiblePassage], language: String, revision: String?) async throws -> (Data, [AudioCue]) {
        try await postTimedAudio(TimedSpeechRequest(bookId: verses.first?.bookId ?? "", chapter: verses.first?.chapter ?? 0, translation: verses.first?.translationId ?? "", text: verses.map(\.text).joined(separator: "\n"), language: language, revision: revision, verses: verses.map { SpeechVerse(number: $0.verseStart, text: $0.text) }))
    }
    private struct SpeechConfiguration: Decodable { let version: String }

    public func speechVersion(language: String) async throws -> String {
        let config: SpeechConfiguration = try await get("/v1/tts/config", query: [.init(name: "language", value: language)])
        guard config.version.count == 64, config.version.allSatisfy({ $0.isHexDigit }) else { throw VerbumAPIError.malformedResponse }
        return config.version
    }

    /// `POST /v1/tts`: one complete chapter MP3 (Google Cloud Chirp 3 HD by default),
    /// server-cached by exact text and settings. Generation can take minutes.
    public func synthesizeSpeech(text: String, language: String, revision: String? = nil) async throws -> Data {
        try await postForData("/v1/tts", body: SpeechRequest(text: text, language: language, revision: revision), timeout: Self.speechTimeout)
    }
}
