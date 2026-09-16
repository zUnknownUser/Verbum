import Models
import Testing
@testable import Features

@Suite struct ChapterNavigationTests {
    @Test func withinBook() {
        #expect(ChapterNavigation.next(after: PassageReference(bookId: "John", chapter: 3)) == PassageReference(bookId: "John", chapter: 4))
        #expect(ChapterNavigation.previous(before: PassageReference(bookId: "John", chapter: 3)) == PassageReference(bookId: "John", chapter: 2))
    }

    @Test func acrossBooks() {
        #expect(ChapterNavigation.next(after: PassageReference(bookId: "Mal", chapter: 4)) == PassageReference(bookId: "Matt", chapter: 1))
        #expect(ChapterNavigation.previous(before: PassageReference(bookId: "Matt", chapter: 1)) == PassageReference(bookId: "Mal", chapter: 4))
        #expect(ChapterNavigation.next(after: PassageReference(bookId: "Obad", chapter: 1)) == PassageReference(bookId: "Jonah", chapter: 1))
    }

    @Test func canonEdges() {
        #expect(ChapterNavigation.previous(before: PassageReference(bookId: "Gen", chapter: 1)) == nil)
        #expect(ChapterNavigation.next(after: PassageReference(bookId: "Rev", chapter: 22)) == nil)
        #expect(ChapterNavigation.next(after: PassageReference(bookId: "Xyz", chapter: 1)) == nil)
    }
}
