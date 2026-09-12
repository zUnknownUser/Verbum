import Testing
@testable import Models

@Suite struct DailyVersesTests {
    @Test func everyVerseIsASingleVerseInTheCanon() {
        for reference in DailyVerses.pool {
            let book = BibleBook.book(id: reference.bookId)
            #expect(book != nil, "\(reference.bookId) is not in the canon")
            #expect(reference.chapter >= 1 && reference.chapter <= (book?.chapterCount ?? 0), "\(reference.formatted)")
            #expect(reference.verses?.count == 1, "\(reference.formatted) is not one verse")
        }
        #expect(Set(DailyVerses.pool).count == DailyVerses.pool.count)
    }

    @Test func epochDayMatchesJavaTime() {
        #expect(DailyVerses.epochDay(year: 1970, month: 1, day: 1) == 0)
        #expect(DailyVerses.epochDay(year: 1969, month: 12, day: 31) == -1)
        #expect(DailyVerses.epochDay(year: 2026, month: 9, day: 12) == 20708)
        #expect(DailyVerses.epochDay(year: 2027, month: 1, day: 1) == 20819)
    }

    /// The same dates are asserted in the Android `DailyVersesTest`.
    @Test func sameVerseOnBothPlatforms() {
        #expect(DailyVerses.verse(year: 2026, month: 9, day: 12) == PassageReference(bookId: "2Tim", chapter: 1, verses: 7...7))
        #expect(DailyVerses.verse(year: 2026, month: 9, day: 13) == PassageReference(bookId: "Ps", chapter: 121, verses: 1...1))
        #expect(DailyVerses.verse(year: 2027, month: 1, day: 1) == PassageReference(bookId: "Mark", chapter: 10, verses: 27...27))
        #expect(DailyVerses.verse(epochDay: -1) == PassageReference(bookId: "Eph", chapter: 2, verses: 8...8))
    }

    @Test func noRepeatWithinACycle() {
        let count = DailyVerses.pool.count
        let start = 205 * count
        let verses = (start..<(start + count)).map { DailyVerses.verse(epochDay: $0) }
        #expect(Set(verses).count == count)
        // …and the next cycle is a different order, not a replay.
        let next = (start + count..<(start + 2 * count)).map { DailyVerses.verse(epochDay: $0) }
        #expect(next != verses)
    }
}
