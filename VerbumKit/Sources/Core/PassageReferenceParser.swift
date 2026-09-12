import Foundation
import Models

/// Why a string could not be read as a passage reference (spec §52: the UI
/// distinguishes error kinds, it never shows raw parser output).
public enum PassageReferenceParseError: Error, Equatable, Sendable {
    case empty
    /// The text does not have the shape `Book chapter[:verse[-verse]]`.
    case malformed
    /// The book part did not match any canon name or abbreviation.
    case unknownBook(String)
    /// Chapter is 0 or exceeds the book's chapter count.
    case chapterOutOfRange(chapter: Int, book: BibleBook)
    /// Verse is 0, or the range end precedes its start.
    case invalidVerseRange
}

/// Deterministic parser for human-typed references (spec §60 Task 3).
///
/// Accepted shapes, whitespace-insensitive and case-insensitive:
///
///     John 3          John 3:16       John 3:16-18
///     Jn 3:16         Jn. 3:16        1 Samuel 17
///     1Sam 17         I Samuel 17     II Sam 5:3–5
///     Psalm 23        Song of Solomon 2:1
///
/// Single-chapter books read `Jude 3` as verse 3 of chapter 1, the usual
/// convention. `:` and `.` both separate chapter from verse; `-`, `–` and `—`
/// all separate a verse range.
///
/// Verse numbers are validated for shape only (≥ 1, start ≤ end). The model
/// has no per-chapter verse counts; the `BibleClient` is the authority there.
public enum PassageReferenceParser {
    /// Parses in the device language: English names and abbreviations are always
    /// accepted; the current language's names and abbreviations are accepted too
    /// and win on conflict (`Jn` is Jonas on a Portuguese device, John elsewhere).
    public static func parse(_ input: String) throws(PassageReferenceParseError) -> PassageReference {
        try parse(input, language: .current)
    }

    public static func parse(_ input: String, language: BookLanguage) throws(PassageReferenceParseError) -> PassageReference {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw .empty }

        guard let match = trimmed.wholeMatch(of: shape) else { throw .malformed }
        let (_, bookText, chapterText, verseStartText, verseEndText) = match.output

        guard let book = book(matching: String(bookText), language: language) else {
            throw .unknownBook(String(bookText).trimmingCharacters(in: .whitespaces))
        }
        guard let chapter = Int(chapterText) else { throw .malformed }

        var verses: ClosedRange<Int>?
        if let verseStartText {
            guard let start = Int(verseStartText), start >= 1 else { throw .invalidVerseRange }
            let end: Int
            if let verseEndText {
                guard let parsedEnd = Int(verseEndText), parsedEnd >= start else { throw .invalidVerseRange }
                end = parsedEnd
            } else {
                end = start
            }
            verses = start...end
        }

        // "Jude 3" means verse 3 of the only chapter.
        if book.chapterCount == 1, verses == nil, chapter > 1 {
            return PassageReference(bookId: book.id, chapter: 1, verses: chapter...chapter)
        }

        guard (1...book.chapterCount).contains(chapter) else {
            throw .chapterOutOfRange(chapter: chapter, book: book)
        }
        return PassageReference(bookId: book.id, chapter: chapter, verses: verses)
    }

    // MARK: - Shape

    /// book, chapter, optional verse start, optional verse end.
    /// Computed because `Regex` is not `Sendable`; the literal is compiled at build time.
    private static var shape: Regex<(Substring, Substring, Substring, Substring?, Substring?)> {
        // Letters include accented ones so `Gênesis 1`, `Jó 1`, `Êxodo 3` parse.
        /^\s*([1-3]?\s*\p{L}[\p{L}. ]*?)\s*(\d+)(?:\s*[:.]\s*(\d+)(?:\s*[-–—]\s*(\d+))?)?\s*$/
    }

    // MARK: - Book lookup

    private static func book(matching text: String, language: BookLanguage) -> BibleBook? {
        booksByKey(for: language)[normalizedKey(text)]
    }

    /// Lowercased, periods and whitespace removed, leading Roman numeral (when
    /// followed by a space, so "Isaiah" is untouched) converted to a digit.
    static func normalizedKey(_ text: String) -> String {
        var s = text.trimmingCharacters(in: .whitespaces)
        if let roman = s.prefixMatch(of: romanPrefix) {
            s = "\(roman.output.1.count)" + s.dropFirst(roman.output.0.count)
        }
        return s.lowercased().filter { $0 != "." && !$0.isWhitespace }
    }

    private static var romanPrefix: Regex<(Substring, Substring)> { /^(I{1,3})\s+/ }

    /// English keys first, then the language's own on top so they win conflicts.
    private static func booksByKey(for language: BookLanguage) -> [String: BibleBook] {
        var map = englishKeys
        guard language != .english else { return map }
        for book in BibleBook.canon {
            map[normalizedKey(book.localizedName(for: language))] = book
            for abbreviation in book.localizedAbbreviations(for: language) {
                map[normalizedKey(abbreviation)] = book
            }
        }
        return map
    }

    private static let englishKeys: [String: BibleBook] = {
        var map: [String: BibleBook] = [:]
        for book in BibleBook.canon {
            map[normalizedKey(book.name)] = book
            map[normalizedKey(book.id)] = book
            for abbreviation in book.abbreviations {
                map[normalizedKey(abbreviation)] = book
            }
        }
        return map
    }()
}
