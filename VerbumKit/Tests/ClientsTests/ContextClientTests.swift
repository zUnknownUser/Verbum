import Clients
import Models
import Testing

@Suite struct ContextClientTests {
    @Test func goldenPathHasTraceableConnections() async throws {
        let reference = PassageReference(bookId: "1Sam", chapter: 17)
        let page = try #require(await ContextClient.fixtures.chapter(reference))
        #expect(page.reference == reference)
        #expect(page.isFixture)
        #expect(page.entities.contains { $0.id == "fixture.person.david" })
        #expect(page.entities.contains { $0.id == "fixture.place.elah" })
        #expect(!page.sources.isEmpty)
        #expect(!page.relatedPassages.isEmpty)
        #expect(!page.relatedPassages.contains(reference))
        #expect(Set(page.relatedPassages).count == page.relatedPassages.count)
    }

    @Test func unknownCoverageDoesNotInventContext() async throws {
        let page = try await ContextClient.fixtures.chapter(.init(bookId: "Neh", chapter: 9))
        #expect(page == nil)
    }
}
