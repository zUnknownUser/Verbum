/// Spec §22.3. A contiguous run of verses in one chapter of one translation.
public struct BiblePassage: Identifiable, Codable, Equatable, Hashable, Sendable {
    public let id: String
    public let translationId: String
    public let bookId: String
    public let chapter: Int
    public let verseStart: Int
    public let verseEnd: Int
    public let text: String

    public init(
        id: String,
        translationId: String,
        bookId: String,
        chapter: Int,
        verseStart: Int,
        verseEnd: Int,
        text: String
    ) {
        self.id = id
        self.translationId = translationId
        self.bookId = bookId
        self.chapter = chapter
        self.verseStart = verseStart
        self.verseEnd = verseEnd
        self.text = text
    }

    /// The location of this passage, independent of translation.
    public var reference: PassageReference {
        PassageReference(bookId: bookId, chapter: chapter, verses: verseStart...verseEnd)
    }
}
