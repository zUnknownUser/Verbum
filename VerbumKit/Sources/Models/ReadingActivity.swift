import Foundation

/// Device-local activity. Opening a chapter is not a claim that it was completed.
public struct ReadingActivity: Codable, Equatable, Sendable {
    public struct Visit: Codable, Equatable, Sendable, Identifiable {
        public var reference: PassageReference
        public var lastOpened: Date
        public var id: String { ReaderCanon.key(reference) }
    }
    public private(set) var visits: [Visit] = []
    public private(set) var days: Set<String> = []
    public init() {}

    public mutating func record(_ reference: PassageReference, at date: Date, calendar: Calendar) {
        guard let book = BibleBook.book(id: reference.bookId), (1...book.chapterCount).contains(reference.chapter) else { return }
        let chapter = PassageReference(bookId: reference.bookId, chapter: reference.chapter)
        visits.removeAll { $0.reference == chapter }
        visits.insert(Visit(reference: chapter, lastOpened: date), at: 0)
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        days.insert("\(parts.year ?? 0)-\(parts.month ?? 0)-\(parts.day ?? 0)")
    }
}
