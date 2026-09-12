/// Where a passage is, independent of any translation: a book, a chapter, and
/// optionally a verse range. This is the value the reference parser (spec §60
/// Task 3) produces and the `BibleClient` (spec §38) consumes.
///
/// `verses == nil` means the whole chapter (`"John 3"`).
public struct PassageReference: Codable, Equatable, Hashable, Sendable {
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
