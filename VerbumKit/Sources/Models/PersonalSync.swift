import Foundation

/// Shared wire vocabulary; platform-specific storage formats never cross the API.
public struct PersonalValue: Codable, Equatable, Sendable {
    public var book: String?
    public var chapter: Int?
    public var verse: Int?
    public var highlight: String?
    public var style: String?
    public var note: String?
    public var bookmarked: Bool?
    public var time: Int64?
    public var day: String?
    public init(
        book: String? = nil, chapter: Int? = nil, verse: Int? = nil, highlight: String? = nil, style: String? = nil,
        note: String? = nil, bookmarked: Bool? = nil, time: Int64? = nil, day: String? = nil
    ) {
        self.book = book
        self.chapter = chapter
        self.verse = verse
        self.highlight = highlight
        self.style = style
        self.note = note
        self.bookmarked = bookmarked
        self.time = time
        self.day = day
    }
    public init(_ annotation: ReaderAnnotation) {
        self.init(
            book: annotation.reference.bookId, chapter: annotation.reference.chapter,
            verse: annotation.reference.verses?.lowerBound ?? 1,
            highlight: annotation.highlight?.rawValue, style: annotation.highlightStyle?.rawValue,
            note: annotation.note.isEmpty ? nil : annotation.note,
            bookmarked: annotation.bookmarked == true ? true : nil)
    }
    public var annotation: ReaderAnnotation? {
        guard let book, let chapter, let verse else { return nil }
        return ReaderAnnotation(
            reference: .init(bookId: book, chapter: chapter, verses: verse...verse),
            highlight: highlight.flatMap(HighlightColor.init), note: note ?? "",
            highlightStyle: style.flatMap(HighlightStyle.init), bookmarked: bookmarked ?? false)
    }
}
public struct PersonalRecord: Codable, Equatable, Sendable {
    public var id: String
    public var revision: Int64
    public var value: PersonalValue?
    public init(id: String, revision: Int64, value: PersonalValue?) {
        self.id = id
        self.revision = revision
        self.value = value
    }
}
public struct PersonalChange: Codable, Equatable, Sendable {
    public var baseRevision: Int64
    public var id: String
    public var mutationId: String
    public var base: PersonalValue?
    public var value: PersonalValue?
    public init(id: String, base: PersonalValue?, value: PersonalValue?, baseRevision: Int64 = 0) {
        self.baseRevision = baseRevision
        self.id = id
        self.mutationId = UUID().uuidString
        self.base = base
        self.value = value
    }
}
public struct PersonalSyncRequest: Codable, Sendable {
    public var cursor: Int64
    public var changes: [PersonalChange]
    public init(cursor: Int64, changes: [PersonalChange]) {
        self.cursor = cursor
        self.changes = changes
    }
}
public struct PersonalSyncResponse: Codable, Sendable {
    public var cursor: Int64
    public var more: Bool
    public var records: [PersonalRecord]
    public var accepted: [PersonalRecord]
    public init(cursor: Int64, more: Bool, records: [PersonalRecord], accepted: [PersonalRecord]) {
        self.cursor = cursor
        self.more = more
        self.records = records
        self.accepted = accepted
    }
}
