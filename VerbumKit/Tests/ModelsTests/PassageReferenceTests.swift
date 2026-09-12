import Foundation
import Testing
@testable import Models

@Suite struct PassageReferenceTests {
    // `formatted` follows the device language; these pin English.
    @Test func wholeChapter() {
        let ref = PassageReference(bookId: "John", chapter: 3)
        #expect(ref.isWholeChapter)
        #expect(ref.formatted(for: .english) == "John 3")
    }

    @Test func singleVerse() {
        let ref = PassageReference(bookId: "John", chapter: 3, verses: 16...16)
        #expect(!ref.isWholeChapter)
        #expect(ref.formatted(for: .english) == "John 3:16")
    }

    @Test func verseRange() {
        let ref = PassageReference(bookId: "John", chapter: 3, verses: 16...18)
        #expect(ref.formatted(for: .english) == "John 3:16-18")
    }

    @Test func numberedBookName() {
        let ref = PassageReference(bookId: "1Sam", chapter: 17)
        #expect(ref.formatted(for: .english) == "1 Samuel 17")
    }

    @Test func unknownBookFallsBackToID() {
        let ref = PassageReference(bookId: "Xyz", chapter: 1, verses: 2...3)
        #expect(ref.formatted(for: .english) == "Xyz 1:2-3")
    }

    @Test func codableRoundTrip() throws {
        let refs = [
            PassageReference(bookId: "Rom", chapter: 8, verses: 28...28),
            PassageReference(bookId: "Rom", chapter: 8),
        ]
        let data = try JSONEncoder().encode(refs)
        #expect(try JSONDecoder().decode([PassageReference].self, from: data) == refs)
    }
}
