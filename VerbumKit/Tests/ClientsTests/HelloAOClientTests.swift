import ComposableArchitecture
import Foundation
import Testing
import Models
@testable import Clients

// Samples are real responses saved from bible.helloao.org; tests never touch the network.
private func sample(_ name: String) throws -> Data {
    let url = try #require(Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Samples"))
    return try Data(contentsOf: url)
}

@Suite struct HelloAOBooksTests {
    @Test func everyCanonBookHasAUSFMCodeAndBack() {
        for book in BibleBook.canon {
            let usfm = HelloAOBooks.usfmByOSIS[book.id]
            #expect(usfm != nil, "\(book.id)")
            #expect(usfm.flatMap { HelloAOBooks.osisByUSFM[$0] } == book.id)
        }
        #expect(HelloAOBooks.usfmByOSIS.count == 66)
        #expect(HelloAOBooks.usfmByOSIS["John"] == "JHN")
        #expect(HelloAOBooks.usfmByOSIS["Song"] == "SNG")
    }

    @Test func translationFollowsLanguage() {
        #expect(HelloAOTranslation.id(for: .english) == "BSB")
        #expect(HelloAOTranslation.id(for: .portuguese) == "por_blj")
    }
}

@Suite struct HelloAOChapterTests {
    @Test func poetryKeepsItsLines() throws {
        let verses = try HelloAOChapter.passages(from: try sample("BSB_PSA_23"), bookId: "Ps")
        #expect(verses.count == 6)
        #expect(verses[0].id == "BSB:Ps.23.1")
        #expect(verses[0].translationId == "BSB")
        #expect(verses[0].text == "The LORD is my shepherd;\nI shall not want.")
        #expect(verses[1].text == "He makes me lie down in green pastures;\nHe leads me beside quiet waters.")
        #expect(verses.allSatisfy { !$0.text.contains("noteId") })
    }

    @Test func proseInPortuguese() throws {
        let verses = try HelloAOChapter.passages(from: try sample("por_blj_JHN_3"), bookId: "John")
        #expect(verses.count == 36)
        #expect(verses[15].verseStart == 16)
        #expect(verses[15].text.hasPrefix("Porque Deus amou ao mundo de tal maneira"))
        #expect(verses[15].translationId == "por_blj")
    }

    @Test func malformedJSONThrows() {
        #expect(throws: (any Error).self) { try HelloAOChapter.passages(from: Data("{}".utf8), bookId: "John") }
    }
}

@Suite struct HelloAOBibleClientTests {
    private func client(translation: String = "BSB", cache: ChapterCache = .inMemory, transport: @escaping HelloAOBibleClient.Transport) -> HelloAOBibleClient {
        HelloAOBibleClient(translationId: translation, transport: transport, cache: cache)
    }

    @Test func fetchesTheRightURLAndCaches() async throws {
        let requested = LockIsolated<[String]>([])
        let data = try sample("BSB_PSA_23")
        let client = client { url in
            requested.withValue { $0.append(url.absoluteString) }
            return data
        }
        let first = try await client.chapter(bookId: "Ps", chapter: 23)
        let second = try await client.chapter(bookId: "Ps", chapter: 23)
        #expect(first.count == 6 && first == second)
        #expect(requested.value == ["https://bible.helloao.org/api/BSB/PSA/23.json"]) // second read came from cache
    }

    @Test func networkFailureWithoutCacheIsOffline() async {
        struct Down: Error {}
        let client = client { _ in throw Down() }
        await #expect(throws: BibleClientError.networkUnavailable) { try await client.chapter(bookId: "John", chapter: 3) }
    }

    @Test func cachedChapterReadsWhileOffline() async throws {
        struct Down: Error {}
        let cache = ChapterCache.inMemory
        let data = try sample("BSB_PSA_23")
        let online = client(cache: cache) { _ in data }
        _ = try await online.chapter(bookId: "Ps", chapter: 23)
        let offline = client(cache: cache) { _ in throw Down() }
        #expect(try await offline.chapter(bookId: "Ps", chapter: 23).count == 6)
    }

    @Test func unknownBookAndChapterOutOfRangeNeverHitTheNetwork() async {
        let client = client { _ in Issue.record("network used"); return Data() }
        await #expect(throws: BibleClientError.unknownBook("Xyz")) { try await client.chapter(bookId: "Xyz", chapter: 1) }
        await #expect(throws: BibleClientError.contentUnavailable(PassageReference(bookId: "John", chapter: 22))) {
            try await client.chapter(bookId: "John", chapter: 22)
        }
    }

    @Test func passageJoinsARange() async throws {
        let data = try sample("BSB_PSA_23")
        let client = client { _ in data }
        let passage = try await client.passage(PassageReference(bookId: "Ps", chapter: 23, verses: 1...2))
        #expect(passage.id == "BSB:Ps.23.1-2")
        #expect(passage.text.hasPrefix("The LORD is my shepherd;"))
        #expect(passage.text.contains("quiet waters."))
    }

    @Test func liveFallsBackToBundledWEBInEnglishOnly() async throws {
        struct Down: Error {}
        let remote = HelloAOBibleClient(translationId: "BSB", transport: { _ in throw Down() }, cache: .inMemory)
        let english = BibleClient.live(language: .english, remote: remote)
        let verses = try await english.chapter(bookId: "John", chapter: 3)
        #expect(verses.count == 36 && verses[0].translationId == "web")

        let remotePT = HelloAOBibleClient(translationId: "por_blj", transport: { _ in throw Down() }, cache: .inMemory)
        let portuguese = BibleClient.live(language: .portuguese, remote: remotePT)
        await #expect(throws: BibleClientError.networkUnavailable) { try await portuguese.chapter(bookId: "John", chapter: 3) }
    }
}
