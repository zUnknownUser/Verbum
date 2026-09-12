import Models
import Testing
@testable import Clients

@Suite struct ChapterMemoryBudgetTests {
    @Test func memoryKeepsOnlyRecentChapters() async {
        let cache = ChapterCache.inMemory
        for chapter in 1...24 { await cache.write([], translationId: "test", reference: .init(bookId: "Ps", chapter: chapter)) }
        _ = await cache.read(translationId: "test", reference: .init(bookId: "Ps", chapter: 1))
        await cache.write([], translationId: "test", reference: .init(bookId: "Ps", chapter: 25))
        #expect(await cache.read(translationId: "test", reference: .init(bookId: "Ps", chapter: 1)) != nil)
        #expect(await cache.read(translationId: "test", reference: .init(bookId: "Ps", chapter: 2)) == nil)
        #expect(await cache.read(translationId: "test", reference: .init(bookId: "Ps", chapter: 25)) != nil)
    }
}
