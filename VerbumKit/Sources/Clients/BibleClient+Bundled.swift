import Foundation
import Models

extension BibleClient {
    /// The World English Bible, bundled with the app. Public domain, so every
    /// chapter reads offline from day one (spec §39). Not a fixture: this is
    /// the real text of a real translation. Other translations arrive with the
    /// content service (Task 11) and layer on top by `translationId`.
    public static let bundled = BibleClient(
        passage: { reference in try BundledBible.shared.passage(reference) },
        chapter: { bookId, chapter in try BundledBible.shared.chapter(bookId: bookId, chapter: chapter) }
    )

    /// Chapters the bundled translation can serve — all 1,189 of the canon.
    public static var bundledChapters: [PassageReference] { BundledBible.shared.availableChapters }
}

/// Parses `web.tsv` (`bookId ⇥ chapter ⇥ verse ⇥ text`, one verse per line,
/// from ebible.org's verse-per-line export) once, on first use. Immutable
/// after init, hence `Sendable`.
final class BundledBible: Sendable {
    static let shared = BundledBible()

    static let translationId = "web"
    static let translationName = "World English Bible"

    private struct ChapterKey: Hashable { let bookId: BookID; let chapter: Int }

    /// Verse-level passages per chapter, in verse order.
    private let chapters: [ChapterKey: [BiblePassage]]
    let availableChapters: [PassageReference]

    private init() {
        guard let url = Bundle.module.url(forResource: "web", withExtension: "tsv"),
              let data = try? Data(contentsOf: url),
              let text = String(data: data, encoding: .utf8)
        else {
            preconditionFailure("web.tsv is missing from the Clients bundle")
        }

        var grouped: [ChapterKey: [BiblePassage]] = [:]
        for line in text.split(separator: "\n", omittingEmptySubsequences: true) {
            let fields = line.split(separator: "\t", maxSplits: 3, omittingEmptySubsequences: false)
            guard fields.count == 4, let chapter = Int(fields[1]), let verse = Int(fields[2]) else { continue }
            let bookId = String(fields[0])
            grouped[ChapterKey(bookId: bookId, chapter: chapter), default: []].append(
                BiblePassage(
                    id: Self.passageID(bookId: bookId, chapter: chapter, verses: verse...verse),
                    translationId: Self.translationId,
                    bookId: bookId,
                    chapter: chapter,
                    verseStart: verse,
                    verseEnd: verse,
                    text: String(fields[3])
                )
            )
        }
        for key in grouped.keys {
            grouped[key]!.sort { $0.verseStart < $1.verseStart }
        }
        chapters = grouped
        availableChapters = grouped.keys
            .map { PassageReference(bookId: $0.bookId, chapter: $0.chapter) }
            .sorted { lhs, rhs in
                let l = BibleBook.book(id: lhs.bookId)?.order ?? .max
                let r = BibleBook.book(id: rhs.bookId)?.order ?? .max
                return l == r ? lhs.chapter < rhs.chapter : l < r
            }
    }

    func chapter(bookId: BookID, chapter: Int) throws(BibleClientError) -> [BiblePassage] {
        guard BibleBook.book(id: bookId) != nil else { throw .unknownBook(bookId) }
        guard let verses = chapters[ChapterKey(bookId: bookId, chapter: chapter)] else {
            throw .contentUnavailable(PassageReference(bookId: bookId, chapter: chapter))
        }
        return verses
    }

    func passage(_ reference: PassageReference) throws(BibleClientError) -> BiblePassage {
        let verses = try chapter(bookId: reference.bookId, chapter: reference.chapter)
        let range = reference.verses ?? 1...verses.count
        guard range.upperBound <= verses.count else {
            throw .verseOutOfRange(reference, available: verses.count)
        }
        let selected = verses[(range.lowerBound - 1)..<range.upperBound]
        return BiblePassage(
            id: Self.passageID(bookId: reference.bookId, chapter: reference.chapter, verses: range),
            translationId: Self.translationId,
            bookId: reference.bookId,
            chapter: reference.chapter,
            verseStart: range.lowerBound,
            verseEnd: range.upperBound,
            text: selected.map(\.text).joined(separator: " ")
        )
    }

    /// `web:John.3.16` / `web:John.3.16-18`. Stable, human-readable, unique per range.
    private static func passageID(bookId: BookID, chapter: Int, verses: ClosedRange<Int>) -> String {
        let range = verses.lowerBound == verses.upperBound
            ? "\(verses.lowerBound)"
            : "\(verses.lowerBound)-\(verses.upperBound)"
        return "\(translationId):\(bookId).\(chapter).\(range)"
    }
}
