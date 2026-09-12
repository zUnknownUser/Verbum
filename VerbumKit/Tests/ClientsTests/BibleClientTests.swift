import ComposableArchitecture
import Testing
import Models
@testable import Clients

@Suite struct BundledBibleTests {
    let client = BibleClient.bundled

    @Test func chapterReturnsOnePassagePerVerseInOrder() async throws {
        let verses = try await client.chapter(bookId: "1Sam", chapter: 17)
        #expect(verses.count == 58)
        #expect(verses.map(\.verseStart) == Array(1...58))
        #expect(verses.allSatisfy { $0.verseStart == $0.verseEnd })
        #expect(verses.allSatisfy { $0.bookId == "1Sam" && $0.chapter == 17 && $0.translationId == "web" })
        #expect(verses.first?.id == "web:1Sam.17.1")
    }

    @Test func textIsRealScripture() async throws {
        let john316 = try await client.passage(reference: PassageReference(bookId: "John", chapter: 3, verses: 16...16))
        #expect(john316.text == "For God so loved the world, that he gave his only born Son, that whoever believes in him should not perish, but have eternal life.")
        #expect(john316.id == "web:John.3.16")
    }

    @Test func passageRangeJoinsVerses() async throws {
        let passage = try await client.passage(reference: PassageReference(bookId: "John", chapter: 3, verses: 16...18))
        let verses = try await client.chapter(bookId: "John", chapter: 3)
        #expect(passage.verseStart == 16 && passage.verseEnd == 18)
        #expect(passage.text == verses[15...17].map(\.text).joined(separator: " "))
        #expect(passage.id == "web:John.3.16-18")
        #expect(passage.reference == PassageReference(bookId: "John", chapter: 3, verses: 16...18))
    }

    @Test func wholeChapterPassageCoversEveryVerse() async throws {
        let passage = try await client.passage(reference: PassageReference(bookId: "Ps", chapter: 23))
        #expect(passage.verseStart == 1 && passage.verseEnd == 6)
        #expect(passage.id == "web:Ps.23.1-6")
    }

    @Test func theWholeCanonIsAvailable() {
        let chapters = BibleClient.bundledChapters
        #expect(chapters.count == 1189)
        #expect(chapters.first == PassageReference(bookId: "Gen", chapter: 1))
        #expect(chapters.last == PassageReference(bookId: "Rev", chapter: 22))
        // Every chapter the canon declares has text, and no book has extra chapters.
        var perBook: [BookID: Int] = [:]
        for chapter in chapters { perBook[chapter.bookId, default: 0] += 1 }
        for book in BibleBook.canon {
            #expect(perBook[book.id] == book.chapterCount, "\(book.id)")
        }
    }

    @Test func knownChaptersHaveStandardVerseCounts() async throws {
        let expected: [PassageReference: Int] = [
            .init(bookId: "Gen", chapter: 1): 31, .init(bookId: "1Sam", chapter: 16): 23,
            .init(bookId: "1Sam", chapter: 17): 58, .init(bookId: "2Sam", chapter: 5): 25,
            .init(bookId: "Ps", chapter: 23): 6, .init(bookId: "Ps", chapter: 51): 19,
            .init(bookId: "Matt", chapter: 6): 34, .init(bookId: "John", chapter: 3): 36,
            .init(bookId: "Rom", chapter: 8): 39,
        ]
        for (reference, count) in expected {
            #expect(try await client.chapter(bookId: reference.bookId, chapter: reference.chapter).count == count, "\(reference.formatted)")
        }
    }

    // MARK: Errors

    @Test func unknownBook() async {
        await #expect(throws: BibleClientError.unknownBook("Xyz")) {
            try await client.chapter(bookId: "Xyz", chapter: 1)
        }
    }

    @Test func chapterBeyondTheBook() async {
        let reference = PassageReference(bookId: "John", chapter: 22)
        await #expect(throws: BibleClientError.contentUnavailable(reference)) {
            try await client.chapter(bookId: "John", chapter: 22)
        }
        await #expect(throws: BibleClientError.contentUnavailable(reference)) {
            try await client.passage(reference: PassageReference(bookId: "John", chapter: 22, verses: 1...2))
        }
    }

    @Test func verseBeyondChapter() async {
        let reference = PassageReference(bookId: "Ps", chapter: 23, verses: 5...7)
        await #expect(throws: BibleClientError.verseOutOfRange(reference, available: 6)) {
            try await client.passage(reference: reference)
        }
    }
}

@Suite struct BibleClientDependencyTests {
    @Test func previewsNeverTouchTheNetwork() async throws {
        // Live reads bible.helloao.org (Task 11 for Scripture); previews stay on the bundled WEB.
        let preview = try await BibleClient.previewValue.chapter(bookId: "John", chapter: 3)
        #expect(preview.count == 36)
        #expect(preview.allSatisfy { $0.translationId == "web" })
    }

    @Test func overridingASingleEndpointInTests() async throws {
        // What features will do: swap only the closure under test.
        let stub = BiblePassage(id: "stub", translationId: "stub", bookId: "John", chapter: 3, verseStart: 16, verseEnd: 16, text: "stub")
        let result = try await withDependencies {
            $0.bibleClient.passage = { @Sendable _ in stub }
        } operation: {
            @Dependency(\.bibleClient) var bibleClient
            return try await bibleClient.passage(reference: PassageReference(bookId: "John", chapter: 3, verses: 16...16))
        }
        #expect(result == stub)
    }
}
