import Foundation

public enum ReadingMode: String, Codable, CaseIterable, Sendable {
    case pages, continuous
}

public enum HighlightStyle: String, Codable, CaseIterable, Sendable { case background, underline, margin }

public enum HighlightColor: String, Codable, CaseIterable, Sendable {
    case gold, sage, rose
}

/// Personal annotations refer to Scripture identity, independently of its translation.
public struct ReaderAnnotation: Codable, Equatable, Sendable, Identifiable {
    public var reference: PassageReference
    public var highlight: HighlightColor?
    public var highlightStyle: HighlightStyle?
    public var note: String
    /// Optional for compatibility with annotations saved before bookmarks existed.
    public var bookmarked: Bool?
    public var id: String { "\(reference.bookId).\(reference.chapter).\(reference.verses?.lowerBound ?? 1)" }
    public init(reference: PassageReference, highlight: HighlightColor? = nil, note: String = "", highlightStyle: HighlightStyle? = nil, bookmarked: Bool? = nil) {
        self.reference = reference; self.highlight = highlight; self.note = note; self.highlightStyle = highlightStyle
        self.bookmarked = bookmarked
    }
}

/// The canon is a cheap index, never a request to fetch every chapter.
public enum ReaderCanon {
    public static let chapters: [PassageReference] = BibleBook.canon.flatMap { book in
        (1...book.chapterCount).map { PassageReference(bookId: book.id, chapter: $0) }
    }
    private static let indices = Dictionary(uniqueKeysWithValues: chapters.enumerated().map { (key($0.element), $0.offset) })
    public static func index(_ reference: PassageReference) -> Int {
        indices[key(reference)] ?? 0
    }
    public static func key(_ reference: PassageReference) -> String { "\(reference.bookId).\(reference.chapter)" }
}

public struct StudyTextSegment: Equatable, Sendable {
    public let text: String
    public let entityIDs: [String]
}

/// Exact, word-boundary matches among entities already sourced for this chapter.
/// Homonyms retain all candidates; never guess which identity a name refers to.
public enum ReaderEntityLinker {
    public static func segments(_ text: String, entities: [BibleEntity]) -> [StudyTextSegment] {
        let candidates = entities.filter { [.person, .place].contains($0.type) && $0.name.count > 1 }
        let names = Dictionary(grouping: candidates, by: { $0.name.lowercased() })
        guard !names.isEmpty else { return [StudyTextSegment(text: text, entityIDs: [])] }
        let pattern = "(?<![\\p{L}\\p{N}])(" + names.keys.sorted { $0.count > $1.count }
            .map(NSRegularExpression.escapedPattern(for:)).joined(separator: "|") + ")(?![\\p{L}\\p{N}])"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else {
            return [StudyTextSegment(text: text, entityIDs: [])]
        }
        let source = text as NSString
        var result: [StudyTextSegment] = []; var end = 0
        for match in regex.matches(in: text, range: NSRange(location: 0, length: source.length)) {
            if match.range.location > end { result.append(.init(text: source.substring(with: NSRange(location: end, length: match.range.location-end)), entityIDs: [])) }
            let word = source.substring(with: match.range)
            result.append(.init(text: word, entityIDs: names[word.lowercased()]?.map(\.id).sorted() ?? []))
            end = NSMaxRange(match.range)
        }
        if end < source.length { result.append(.init(text: source.substring(from: end), entityIDs: [])) }
        return result
    }
}
