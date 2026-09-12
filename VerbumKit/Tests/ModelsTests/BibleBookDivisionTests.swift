import Testing
@testable import Models

@Suite struct BibleBookDivisionTests {
    @Test func everyBookHasExactlyOneDivisionAndCountsAddUp() {
        let counts = Dictionary(grouping: BibleBook.canon, by: \.division).mapValues(\.count)
        #expect(counts[.law] == 5)
        #expect(counts[.history] == 12)
        #expect(counts[.poetry] == 5)
        #expect(counts[.prophets] == 17)
        #expect(counts[.gospelsAndActs] == 5)
        #expect(counts[.lettersOfPaul] == 13)
        #expect(counts[.generalLetters] == 8)
        #expect(counts[.revelation] == 1)
        #expect(counts.values.reduce(0, +) == 66)
    }

    @Test func boundariesAreRight() {
        #expect(BibleBook.book(id: "Deut")?.division == .law)
        #expect(BibleBook.book(id: "Josh")?.division == .history)
        #expect(BibleBook.book(id: "Esth")?.division == .history)
        #expect(BibleBook.book(id: "Job")?.division == .poetry)
        #expect(BibleBook.book(id: "Song")?.division == .poetry)
        #expect(BibleBook.book(id: "Isa")?.division == .prophets)
        #expect(BibleBook.book(id: "Mal")?.division == .prophets)
        #expect(BibleBook.book(id: "Matt")?.division == .gospelsAndActs)
        #expect(BibleBook.book(id: "Acts")?.division == .gospelsAndActs)
        #expect(BibleBook.book(id: "Rom")?.division == .lettersOfPaul)
        #expect(BibleBook.book(id: "Phlm")?.division == .lettersOfPaul)
        #expect(BibleBook.book(id: "Heb")?.division == .generalLetters)
        #expect(BibleBook.book(id: "Jude")?.division == .generalLetters)
        #expect(BibleBook.book(id: "Rev")?.division == .revelation)
    }

    @Test func divisionsFollowCanonOrderAndTestament() {
        let orderOfFirstBooks = BibleBook.Division.allCases.compactMap { $0.books.first?.order }
        #expect(orderOfFirstBooks == orderOfFirstBooks.sorted())
        for division in BibleBook.Division.allCases {
            #expect(division.books.allSatisfy { $0.testament == division.testament })
        }
    }
}
