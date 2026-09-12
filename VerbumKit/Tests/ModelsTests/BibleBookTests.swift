import Foundation
import Testing
@testable import Models

@Suite struct BibleBookTests {
    @Test func canonHasSixtySixBooksInOrder() {
        #expect(BibleBook.canon.count == 66)
        #expect(BibleBook.canon.map(\.order) == Array(1...66))
        #expect(BibleBook.canon.first?.id == "Gen")
        #expect(BibleBook.canon.last?.id == "Rev")
    }

    @Test func testamentsSplitThirtyNineAndTwentySeven() {
        let old = BibleBook.canon.filter { $0.testament == .old }
        let new = BibleBook.canon.filter { $0.testament == .new }
        #expect(old.count == 39)
        #expect(new.count == 27)
        #expect(old.last?.id == "Mal")
        #expect(new.first?.id == "Matt")
    }

    @Test func chapterCountsMatchStandardVersification() {
        #expect(BibleBook.canon.reduce(0) { $0 + $1.chapterCount } == 1189)
        #expect(BibleBook.book(id: "Ps")?.chapterCount == 150)
        #expect(BibleBook.book(id: "Obad")?.chapterCount == 1)
        #expect(BibleBook.book(id: "1Sam")?.chapterCount == 31)
        #expect(BibleBook.canon.allSatisfy { $0.chapterCount >= 1 })
    }

    @Test func idsAndNamesAreUnique() {
        #expect(Set(BibleBook.canon.map(\.id)).count == 66)
        #expect(Set(BibleBook.canon.map(\.name)).count == 66)
    }

    @Test func abbreviationsNeverCollideAcrossBooks() {
        var seen: [String: BookID] = [:]
        for book in BibleBook.canon {
            for abbreviation in book.abbreviations.map({ $0.lowercased() }) {
                #expect(seen[abbreviation] == nil, "\(abbreviation) used by \(seen[abbreviation] ?? "") and \(book.id)")
                seen[abbreviation] = book.id
            }
        }
    }

    @Test func lookupByID() {
        #expect(BibleBook.book(id: "John")?.name == "John")
        #expect(BibleBook.book(id: "1Sam")?.name == "1 Samuel")
        #expect(BibleBook.book(id: "Nope") == nil)
    }

    @Test func codableRoundTrip() throws {
        let book = try #require(BibleBook.book(id: "Rom"))
        let data = try JSONEncoder().encode(book)
        let decoded = try JSONDecoder().decode(BibleBook.self, from: data)
        #expect(decoded == book)
    }
}
