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

    /// The encoding is the contract's (`api/openapi.yaml` PassageReference).
    @Test func wireShape() throws {
        let verse = try JSONDecoder().decode(PassageReference.self, from: Data(#"{"bookId":"John","chapter":3,"verseStart":16,"verseEnd":18}"#.utf8))
        #expect(verse == PassageReference(bookId: "John", chapter: 3, verses: 16...18))
        let chapter = try JSONDecoder().decode(PassageReference.self, from: Data(#"{"bookId":"1Sam","chapter":17}"#.utf8))
        #expect(chapter == PassageReference(bookId: "1Sam", chapter: 17))
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        #expect(String(decoding: try encoder.encode(verse), as: UTF8.self) == #"{"bookId":"John","chapter":3,"verseEnd":18,"verseStart":16}"#)
        #expect(String(decoding: try encoder.encode(chapter), as: UTF8.self) == #"{"bookId":"1Sam","chapter":17}"#)
        #expect(throws: DecodingError.self) {
            try JSONDecoder().decode(PassageReference.self, from: Data(#"{"bookId":"John","chapter":3,"verseStart":18,"verseEnd":16}"#.utf8))
        }
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
