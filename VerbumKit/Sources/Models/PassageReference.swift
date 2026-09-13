/// Where a passage is, independent of any translation: a book, a chapter, and
/// optionally a verse range. This is the value the reference parser (spec §60
/// Task 3) produces and the `BibleClient` (spec §38) consumes.
///
/// `verses == nil` means the whole chapter (`"John 3"`).
public struct PassageReference: Equatable, Hashable, Sendable {
    public let bookId: BookID
    public let chapter: Int
    public let verses: ClosedRange<Int>?

    public init(bookId: BookID, chapter: Int, verses: ClosedRange<Int>? = nil) {
        self.bookId = bookId
        self.chapter = chapter
        self.verses = verses
    }

    public var isWholeChapter: Bool { verses == nil }

    /// Human-readable form in the device language: `John 3`, `John 3:16-18`,
    /// `1 Samuel 17` — or `João 3:16-18` on a Portuguese device. Falls back to
    /// the raw book id if the book is not in the canon.
    public var formatted: String { formatted(for: .current) }

    public func formatted(for language: BookLanguage) -> String {
        let bookName = BibleBook.book(id: bookId)?.localizedName(for: language) ?? bookId
        guard let verses else { return "\(bookName) \(chapter)" }
        if verses.lowerBound == verses.upperBound {
            return "\(bookName) \(chapter):\(verses.lowerBound)"
        }
        return "\(bookName) \(chapter):\(verses.lowerBound)-\(verses.upperBound)"
    }
}

/// On the wire (api/openapi.yaml `PassageReference`) a reference is
/// `{bookId, chapter, verseStart?, verseEnd?}` — the same shape the backend
/// serves, the notification plan stores and `lastRead.json` persists, so there
/// is exactly one encoding of a reference in the app.
extension PassageReference: Codable {
    private enum CodingKeys: String, CodingKey {
        case bookId, chapter, verseStart, verseEnd
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let bookId = try container.decode(String.self, forKey: .bookId)
        let chapter = try container.decode(Int.self, forKey: .chapter)
        let start = try container.decodeIfPresent(Int.self, forKey: .verseStart)
        let end = try container.decodeIfPresent(Int.self, forKey: .verseEnd)
        var verses: ClosedRange<Int>?
        switch (start, end) {
        case (nil, nil):
            verses = nil
        case (let s?, let e?):
            guard s >= 1, e >= s else {
                throw DecodingError.dataCorruptedError(forKey: .verseEnd, in: container, debugDescription: "verseEnd must be ≥ verseStart ≥ 1")
            }
            verses = s...e
        case (let s?, nil):
            guard s >= 1 else { throw DecodingError.dataCorruptedError(forKey: .verseStart, in: container, debugDescription: "verseStart must be ≥ 1") }
            verses = s...s
        case (nil, _?):
            throw DecodingError.dataCorruptedError(forKey: .verseStart, in: container, debugDescription: "verseEnd without verseStart")
        }
        self.init(bookId: bookId, chapter: chapter, verses: verses)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(bookId, forKey: .bookId)
        try container.encode(chapter, forKey: .chapter)
        if let verses {
            try container.encode(verses.lowerBound, forKey: .verseStart)
            try container.encode(verses.upperBound, forKey: .verseEnd)
        }
    }
}
