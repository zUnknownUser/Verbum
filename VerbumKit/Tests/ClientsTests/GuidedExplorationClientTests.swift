import Clients
import Models
import Testing

@Suite struct GuidedExplorationClientTests {
    @Test func everyFeelingHasDeterministicSourcedReadings() async throws {
        for feeling in ArrivalFeeling.allCases {
            let request = ExplorationRequest(feeling: feeling, language: .portuguese)
            let plan = try await GuidedExplorationClient.editorialPreview.explore(request)
            #expect(plan == (try await GuidedExplorationClient.editorialPreview.explore(request)))
            #expect(plan.isEditorialPreview)
            #expect(plan.passages.count == 2)
            #expect(!plan.guidingQuestion.isEmpty)
            for passage in plan.passages {
                let book = try #require(BibleBook.book(id: passage.bookId))
                #expect((1...book.chapterCount).contains(passage.chapter))
                #expect(passage.verses != nil)
            }
        }
    }
}
