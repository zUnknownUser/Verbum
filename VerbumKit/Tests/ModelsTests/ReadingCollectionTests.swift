import Models
import Testing

@Suite struct ReadingCollectionTests {
    @Test func searchesBothLanguagesAccentsReferencesAndPersonalNotes() {
        let item = ReaderAnnotation(reference: .init(bookId: "John", chapter: 3, verses: 16...16), note: "Graça e amor", bookmarked: true)
        for query in ["João 3:16", "joao", "JOHN", "graca amor"] {
            #expect(ReadingCollection.entries([item], filter: .all, query: query) == [item])
        }
        #expect(ReadingCollection.entries([item], filter: .all, query: "Jó 38").isEmpty)
    }

    @Test func filtersOverlapWithoutDuplicatingAndSortsCanonically() {
        let first = ReaderAnnotation(reference: .init(bookId: "Gen", chapter: 1, verses: 1...1), highlight: .gold, note: "Luz", bookmarked: true)
        let last = ReaderAnnotation(reference: .init(bookId: "Rev", chapter: 22, verses: 21...21), bookmarked: true)
        let removed = ReaderAnnotation(reference: .init(bookId: "John", chapter: 3, verses: 16...16))
        let values = [last, removed, first]
        #expect(ReadingCollection.entries(values, filter: .all, query: "") == [first, last])
        #expect(ReadingCollection.entries(values, filter: .saved, query: "") == [first, last])
        #expect(ReadingCollection.entries(values, filter: .highlights, query: "") == [first])
        #expect(ReadingCollection.entries(values, filter: .notes, query: "") == [first])
    }
}
