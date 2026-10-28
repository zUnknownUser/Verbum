import Foundation
import Models
import Testing
@testable import Clients

@Suite struct BookOrientationTests {
    private static let examples = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .appendingPathComponent("api/examples/context")

    @Test(arguments: ["en", "pt-BR"])
    func liveDecodingPreservesWholeBookScopeAndNavigation(language: String) async throws {
        let data = try Data(contentsOf: Self.examples.appendingPathComponent("book-orientation.\(language).json"))
        let api = VerbumAPI(baseURL: URL(string: "https://contract.test")!, transport: { request in
            (data, HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!)
        }, cache: .inMemory)
        let page = try #require(await api.context(.init(bookId: "Heb", chapter: 1), language: language == "en" ? .english : .portuguese))
        let book = try #require(page.bookOrientation)
        #expect(page.study == nil)
        #expect(!page.isFixture)
        #expect(book.outline.first?.chapterStart == 1)
        #expect(book.outline.last?.chapterEnd == 13)
        #expect(book.readings.map(\.chapter) == [4,10,11])
        #expect(book.readings.allSatisfy { $0.bookId == "Heb" })
        #expect(Set(book.sourceReferenceIds).isSubset(of: Set(page.sources.map(\.id))))
        let roundTrip = try JSONDecoder().decode(PassageContext.self, from: JSONEncoder().encode(page))
        #expect(roundTrip == page)
    }

    @Test func legacyContextStillLoadsWithoutBookOrientation() async throws {
        let data = try Data(contentsOf: Self.examples.appendingPathComponent("1Sam.17.json"))
        let api = VerbumAPI(baseURL: URL(string:"https://contract.test")!, transport: { request in
            (data, HTTPURLResponse(url:request.url!,statusCode:200,httpVersion:nil,headerFields:nil)!)
        }, cache:.inMemory)
        let page = try #require(await api.context(.init(bookId:"1Sam",chapter:17)))
        #expect(page.bookOrientation == nil)
        #expect(!page.entities.isEmpty)
    }
}
