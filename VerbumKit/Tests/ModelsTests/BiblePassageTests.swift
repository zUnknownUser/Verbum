import Foundation
import Testing
@testable import Models

@Suite struct BiblePassageTests {
    // Fixture: text is placeholder, not Scripture.
    let passage = BiblePassage(
        id: "fixture-passage",
        translationId: "fixture",
        bookId: "1Sam",
        chapter: 17,
        verseStart: 45,
        verseEnd: 47,
        text: "fixture text"
    )

    @Test func referenceDerivedFromLocation() {
        #expect(passage.reference == PassageReference(bookId: "1Sam", chapter: 17, verses: 45...47))
        #expect(passage.reference.formatted(for: .english) == "1 Samuel 17:45-47")
    }

    @Test func codableRoundTrip() throws {
        let data = try JSONEncoder().encode(passage)
        #expect(try JSONDecoder().decode(BiblePassage.self, from: data) == passage)
    }
}

@Suite struct AudioNarratorTests {
    @Test func synthesisedReadingsAreToldFromRecordings() {
        #expect(AudioNarrator(id: "native.pt-BR", name: "Leitura automática", url: "file:///x.m4a", timingsPath: nil).isSynthesised)
        #expect(!AudioNarrator(id: "david", name: "David", url: "https://x/david.mp3", timingsPath: nil).isSynthesised)
    }
}
