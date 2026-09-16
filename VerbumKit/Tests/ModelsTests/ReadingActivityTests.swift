import Foundation
import Models
import Testing

@Suite struct ReadingActivityTests {
    @Test func visitsDeduplicateChaptersAndLocalDays() throws {
        var activity = ReadingActivity()
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = TimeZone(secondsFromGMT: -4 * 3600)!
        let date = ISO8601DateFormatter().date(from: "2026-09-16T03:30:00Z")!
        activity.record(.init(bookId: "John", chapter: 1, verses: 14...14), at: date, calendar: calendar)
        activity.record(.init(bookId: "John", chapter: 2), at: date, calendar: calendar)
        activity.record(.init(bookId: "John", chapter: 1), at: date.addingTimeInterval(3600), calendar: calendar)
        #expect(activity.days.count == 2)
        #expect(activity.visits.map(\.reference) == [.init(bookId: "John", chapter: 1), .init(bookId: "John", chapter: 2)])
        let restored = try JSONDecoder().decode(ReadingActivity.self, from: JSONEncoder().encode(activity))
        #expect(restored == activity)
    }
    @Test func invalidChaptersAreNotCounted() {
        var activity = ReadingActivity()
        activity.record(.init(bookId: "John", chapter: 99), at: Date(), calendar: .current)
        activity.record(.init(bookId: "missing", chapter: 1), at: Date(), calendar: .current)
        #expect(activity.visits.isEmpty && activity.days.isEmpty)
    }
    @Test func oldAnnotationsDecodeWithoutBookmarkAndKeepTheirNotes() throws {
        let note = ReaderAnnotation(reference: .init(bookId: "John", chapter: 1, verses: 14...14), highlight: .gold, note: "Minha nota")
        var json = try #require(JSONSerialization.jsonObject(with: JSONEncoder().encode(note)) as? [String: Any])
        json.removeValue(forKey: "bookmarked")
        let restored = try JSONDecoder().decode(ReaderAnnotation.self, from: JSONSerialization.data(withJSONObject: json))
        #expect(restored.note == "Minha nota")
        #expect(restored.highlight == .gold)
        #expect(restored.bookmarked != true)
    }
}
