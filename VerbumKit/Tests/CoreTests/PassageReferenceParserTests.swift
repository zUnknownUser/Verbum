import Testing
import Models
@testable import Core

@Suite struct PassageReferenceParserTests {
    // Pinned to English: the device (and CI) locale must not change what these prove.
    private enum P {
        static func parse(_ input: String) throws(PassageReferenceParseError) -> PassageReference {
            try PassageReferenceParser.parse(input, language: .english)
        }
    }

    // MARK: Inputs required by spec §60 Task 3

    @Test(arguments: [
        ("John 3:16", PassageReference(bookId: "John", chapter: 3, verses: 16...16)),
        ("John 3", PassageReference(bookId: "John", chapter: 3)),
        ("John 3:16-18", PassageReference(bookId: "John", chapter: 3, verses: 16...18)),
        ("Jn 3:16", PassageReference(bookId: "John", chapter: 3, verses: 16...16)),
        ("Romans 8:28", PassageReference(bookId: "Rom", chapter: 8, verses: 28...28)),
        ("1 Samuel 17", PassageReference(bookId: "1Sam", chapter: 17)),
    ])
    func specInputs(input: String, expected: PassageReference) throws {
        #expect(try P.parse(input) == expected)
    }

    // MARK: Variants people actually type

    @Test(arguments: [
        ("jn 3:16", "John"), ("JOHN 3:16", "John"), ("Jn. 3:16", "John"), ("John. 3:16", "John"),
        ("1Sam 17:1", "1Sam"), ("1 Sam 17:1", "1Sam"), ("I Samuel 17:1", "1Sam"), ("II Sam 5:1", "2Sam"),
        ("III John 1:1", "3John"), ("Psalm 23:1", "Ps"), ("Psalms 23:1", "Ps"), ("Ps 23:1", "Ps"),
        ("Song of Solomon 2:1", "Song"), ("Song of Songs 2:1", "Song"), ("Rev 22:1", "Rev"),
        ("Isaiah 1:1", "Isa"), ("Is 1:1", "Isa"),
    ])
    func bookSpellings(input: String, bookId: String) throws {
        #expect(try P.parse(input).bookId == bookId)
    }

    @Test func whitespaceIsIrrelevant() throws {
        let expected = PassageReference(bookId: "John", chapter: 3, verses: 16...18)
        #expect(try P.parse("  John   3 : 16 - 18  ") == expected)
        #expect(try P.parse("John3:16-18") == expected)
        #expect(try P.parse("\nJohn 3:16-18\n") == expected)
    }

    @Test func dashVariantsSeparateRanges() throws {
        let expected = PassageReference(bookId: "John", chapter: 3, verses: 16...18)
        #expect(try P.parse("John 3:16–18") == expected)
        #expect(try P.parse("John 3:16—18") == expected)
    }

    @Test func periodSeparatesChapterFromVerse() throws {
        #expect(try P.parse("John 3.16") == PassageReference(bookId: "John", chapter: 3, verses: 16...16))
    }

    @Test func singleVerseRangeCollapses() throws {
        #expect(try P.parse("John 3:16-16").verses == 16...16)
    }

    @Test func singleChapterBookReadsNumberAsVerse() throws {
        #expect(try P.parse("Jude 3") == PassageReference(bookId: "Jude", chapter: 1, verses: 3...3))
        #expect(try P.parse("Jude 1") == PassageReference(bookId: "Jude", chapter: 1))
        #expect(try P.parse("Jude 1:3") == PassageReference(bookId: "Jude", chapter: 1, verses: 3...3))
        #expect(try P.parse("Philemon 6") == PassageReference(bookId: "Phlm", chapter: 1, verses: 6...6))
    }

    @Test func boundaryChapters() throws {
        #expect(try P.parse("Psalm 150").chapter == 150)
        #expect(try P.parse("Genesis 1").chapter == 1)
    }

    // MARK: Errors

    @Test func emptyInput() {
        #expect(throws: PassageReferenceParseError.empty) { try P.parse("") }
        #expect(throws: PassageReferenceParseError.empty) { try P.parse("   ") }
    }

    @Test(arguments: ["John", "3:16", "John three", "John 3:", "John 3:16-", "John 3:16-18-20", "3 John 3 3", "David", "why did Job suffer"])
    func malformedInputs(input: String) {
        #expect(throws: PassageReferenceParseError.malformed) { try P.parse(input) }
    }

    @Test func unknownBookIsReportedWithWhatWasTyped() {
        #expect(throws: PassageReferenceParseError.unknownBook("Hezekiah")) {
            try P.parse("Hezekiah 3:16")
        }
    }

    @Test func chapterOutOfRange() throws {
        let john = try #require(BibleBook.book(id: "John"))
        #expect(throws: PassageReferenceParseError.chapterOutOfRange(chapter: 22, book: john)) {
            try P.parse("John 22:1")
        }
        #expect(throws: PassageReferenceParseError.chapterOutOfRange(chapter: 0, book: john)) {
            try P.parse("John 0:1")
        }
    }

    @Test func invalidVerseRanges() {
        #expect(throws: PassageReferenceParseError.invalidVerseRange) { try P.parse("John 3:0") }
        #expect(throws: PassageReferenceParseError.invalidVerseRange) { try P.parse("John 3:18-16") }
    }

    // MARK: Determinism

    @Test func sameInputAlwaysSameOutput() throws {
        let results = try (0..<50).map { _ in try P.parse("Rom 8:28-30") }
        #expect(Set(results).count == 1)
    }

    @Test func formattedOutputRoundTrips() throws {
        for input in ["John 3", "John 3:16", "John 3:16-18", "1 Samuel 17", "Song of Solomon 2:1-4"] {
            let ref = try P.parse(input)
            #expect(try P.parse(ref.formatted(for: .english)) == ref)
        }
    }
}

@Suite struct PassageReferenceParserPortugueseTests {
    private func pt(_ input: String) throws -> PassageReference {
        try PassageReferenceParser.parse(input, language: .portuguese)
    }

    @Test(arguments: [
        ("Jo 3:16", "John"), ("João 3:16", "John"), ("Joao 3:16", "John"), ("Jn 1:1", "Jonah"),
        ("Jó 1", "Job"), ("Gn 1", "Gen"), ("Gênesis 1", "Gen"), ("Êx 3", "Exod"), ("Sl 23", "Ps"), ("Salmos 23", "Ps"),
        ("1 Sm 17", "1Sam"), ("1Sm 17", "1Sam"), ("1 Reis 8", "1Kgs"), ("Ct 2", "Song"), ("Cantares 2", "Song"),
        ("Ap 21", "Rev"), ("Apocalipse 21", "Rev"), ("Tg 1", "Jas"), ("1 Jo 4", "1John"), ("Fm 1", "Phlm"),
    ])
    func portugueseNames(input: String, bookId: String) throws {
        #expect(try pt(input).bookId == bookId)
    }

    @Test func englishStillWorksOnAPortugueseDevice() throws {
        #expect(try pt("John 3:16").bookId == "John")
        #expect(try pt("1 Samuel 17").bookId == "1Sam")
        #expect(try pt("Rev 22").bookId == "Rev")
    }

    @Test func conflictsResolveToTheDeviceLanguage() throws {
        // "Jn" is John in English but Jonas in Portuguese.
        #expect(try PassageReferenceParser.parse("Jn 1:1", language: .english).bookId == "John")
        #expect(try pt("Jn 1:1").bookId == "Jonah")
    }

    @Test func portugueseRoundTrips() throws {
        for input in ["João 3", "João 3:16", "1 Samuel 17", "Cântico dos Cânticos 2:1-4", "Jó 42"] {
            let ref = try pt(input)
            #expect(try pt(ref.formatted(for: .portuguese)) == ref)
        }
    }
}
