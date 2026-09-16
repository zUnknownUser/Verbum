import Foundation
import Models

/// Reads Scripture from bible.helloao.org — free, keyless, open-licensed
/// translations served as static JSON — and keeps every chapter it has seen on
/// disk so it reads again offline (spec §39).
public struct HelloAOBibleClient: Sendable {
    /// Fetches the bytes at a URL. Injected so tests never touch the network.
    public typealias Transport = @Sendable (URL) async throws -> Data

    public let translationId: String
    let transport: Transport
    let cache: ChapterCache

    public init(translationId: String, transport: @escaping Transport = Self.urlSession, cache: ChapterCache = .onDisk) {
        self.translationId = translationId
        self.transport = transport
        self.cache = cache
    }

    public static let baseURL = URL(string: "https://bible.helloao.org/api")!

    /// Plain `URLSession`: a GET of a static file is all this API needs.
    public static let urlSession: Transport = { url in
        let (data, response) = try await URLSession.shared.data(from: url)
        if let http = response as? HTTPURLResponse, http.statusCode == 404 {
            throw BibleClientError.contentUnavailable(PassageReference(bookId: url.lastPathComponent, chapter: 0))
        }
        return data
    }

    public func chapter(bookId: BookID, chapter: Int) async throws -> [BiblePassage] {
        guard let book = BibleBook.book(id: bookId) else { throw BibleClientError.unknownBook(bookId) }
        let reference = PassageReference(bookId: bookId, chapter: chapter)
        guard (1...book.chapterCount).contains(chapter), let usfm = HelloAOBooks.usfmByOSIS[bookId] else {
            throw BibleClientError.contentUnavailable(reference)
        }
        if let cached = await cache.read(translationId: translationId, reference: reference),
           !cached.isEmpty, cached.allSatisfy({ $0.translationId == translationId }) {
            return cached
        }
        let url = Self.baseURL.appendingPathComponent("\(translationId)/\(usfm)/\(chapter).json")
        let data: Data
        do {
            data = try await transport(url)
        } catch let error as BibleClientError {
            throw error
        } catch {
            throw BibleClientError.networkUnavailable
        }
        let passages = try HelloAOChapter.passages(from: data, bookId: bookId)
        guard !passages.isEmpty, passages.allSatisfy({ $0.translationId == translationId }) else { throw BibleClientError.contentUnavailable(reference) }
        await cache.write(passages, translationId: translationId, reference: reference)
        return passages
    }

    public func passage(_ reference: PassageReference) async throws -> BiblePassage {
        let verses = try await chapter(bookId: reference.bookId, chapter: reference.chapter)
        let range = reference.verses ?? 1...verses.count
        guard range.upperBound <= verses.count else {
            throw BibleClientError.verseOutOfRange(reference, available: verses.count)
        }
        let selected = verses[(range.lowerBound - 1)..<range.upperBound]
        let rangeText = range.lowerBound == range.upperBound ? "\(range.lowerBound)" : "\(range.lowerBound)-\(range.upperBound)"
        return BiblePassage(
            id: "\(translationId):\(reference.bookId).\(reference.chapter).\(rangeText)",
            translationId: translationId,
            bookId: reference.bookId,
            chapter: reference.chapter,
            verseStart: range.lowerBound,
            verseEnd: range.upperBound,
            text: selected.map(\.text).joined(separator: " ")
        )
    }
}

/// Chapters already read, as JSON files in Caches. Small, simple, enough for
/// offline re-reading until a real local store (SQLite/SwiftData) is needed.
public actor ChapterCache {
    private let directory: URL?
    private var memory: [String: [BiblePassage]] = [:]
    private var recency: [String] = []
    private let memoryLimit = 24

    public init(directory: URL?) {
        self.directory = directory
    }

    public static let onDisk = ChapterCache(
        directory: FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?.appendingPathComponent("Scripture", isDirectory: true)
    )
    /// A fresh, empty cache — one per test.
    public static var inMemory: ChapterCache { ChapterCache(directory: nil) }

    func read(translationId: String, reference: PassageReference) -> [BiblePassage]? {
        let key = key(translationId, reference)
        if let hit = memory[key] { remember(hit, for: key); return hit }
        guard let url = fileURL(key), let data = try? Data(contentsOf: url),
              let passages = try? JSONDecoder().decode([BiblePassage].self, from: data) else { return nil }
        remember(passages, for: key)
        return passages
    }

    func write(_ passages: [BiblePassage], translationId: String, reference: PassageReference) {
        let key = key(translationId, reference)
        remember(passages, for: key)
        guard let url = fileURL(key), let data = try? JSONEncoder().encode(passages) else { return }
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? data.write(to: url, options: .atomic)
    }

    private func key(_ translationId: String, _ reference: PassageReference) -> String {
        "\(translationId)_\(reference.bookId)_\(reference.chapter)"
    }

    private func remember(_ passages: [BiblePassage], for key: String) {
        recency.removeAll { $0 == key }
        recency.append(key)
        memory[key] = passages
        if recency.count > memoryLimit { memory.removeValue(forKey: recency.removeFirst()) }
    }

    private func fileURL(_ key: String) -> URL? {
        directory?.appendingPathComponent("\(key).json")
    }
}

extension BibleClient {
    /// Production client: helloao in the device language, with two safety
    /// nets — chapters read before (disk cache) and, for English, the bundled
    /// WEB — so the reader never dead-ends without a network.
    public static func live(language: BookLanguage, remote: HelloAOBibleClient? = nil) -> BibleClient {
        let remote = remote ?? HelloAOBibleClient(translationId: HelloAOTranslation.id(for: language))
        let offline: BibleClient? = language == .english ? .bundled : nil

        @Sendable func chapterWithFallback(_ bookId: BookID, _ chapter: Int) async throws -> [BiblePassage] {
            do {
                return try await remote.chapter(bookId: bookId, chapter: chapter)
            } catch BibleClientError.networkUnavailable {
                guard let offline else { throw BibleClientError.networkUnavailable }
                return try await offline.chapter(bookId: bookId, chapter: chapter)
            }
        }

        return BibleClient(
            passage: { reference in
                do {
                    return try await remote.passage(reference)
                } catch BibleClientError.networkUnavailable {
                    guard let offline else { throw BibleClientError.networkUnavailable }
                    return try await offline.passage(reference: reference)
                }
            },
            chapter: { bookId, chapter in try await chapterWithFallback(bookId, chapter) }
        )
    }
}
