import Testing
import Models
@testable import Core

@Suite struct BookMatcherTests {
    private func ids(_ query: String) -> [String] { BookMatcher.books(matching: query).map(\.id) }

    @Test func prefixOfName() {
        #expect(ids("gen") == ["Gen"])
        #expect(ids("Gene") == ["Gen"])
        #expect(ids("john") == ["John", "1John", "2John", "3John"])
    }

    @Test func prefixOfAnyWord() {
        #expect(ids("sam") == ["1Sam", "2Sam"])
        #expect(ids("kings") == ["1Kgs", "2Kgs"])
        #expect(ids("solomon") == ["Song"])
    }

    @Test func prefixOfAbbreviation() {
        #expect(ids("jn") == ["John", "Jonah"]) // exact abbreviation first, then prefix of "Jnh"
        #expect(ids("ps") == ["Ps"])
        #expect(ids("1 sam") == ["1Sam"])
        #expect(ids("1sam") == ["1Sam"])
    }

    @Test func leadingNumberListsTheNumberedBooks() {
        // Useful mid-typing: "1" → every First book, "2 c" → 2 Chronicles, 2 Corinthians.
        #expect(ids("1") == ["1Sam", "1Kgs", "1Chr", "1Cor", "1Thess", "1Tim", "1Pet", "1John"])
        #expect(ids("2 c") == ["2Chr", "2Cor"])
    }

    @Test func caseAndPunctuationAreIgnored() {
        #expect(ids("JN.") == ["John", "Jonah"])
        #expect(ids("  Rom ") == ["Rom"])
    }

    @Test func emptyAndNonsense() {
        #expect(ids("").isEmpty)
        #expect(ids("   ").isEmpty)
        #expect(ids("xyz").isEmpty)
    }

    @Test func resultsFollowCanonOrder() {
        let orders = BookMatcher.books(matching: "j").map(\.order)
        #expect(orders == orders.sorted())
        #expect(ids("j").first == "Josh")
    }

    @Test func exactNameOutranksPrefixMatches() {
        #expect(ids("john").first == "John")
        #expect(ids("ps") == ["Ps"])
        #expect(ids("job") == ["Job"])
    }
}
