import Foundation
import Models
import Testing

@Suite struct ReaderStudyTests {
    @Test func canonLookupIgnoresVerseSelectionAndHandlesInvalidChapters() {
        for (index, reference) in ReaderCanon.chapters.enumerated() {
            #expect(ReaderCanon.index(reference) == index)
            #expect(ReaderCanon.index(.init(bookId: reference.bookId, chapter: reference.chapter, verses: 2...3)) == index)
        }
        #expect(ReaderCanon.index(.init(bookId: "Rev", chapter: 23)) == 0)
        #expect(ReaderCanon.index(.init(bookId: "missing", chapter: 1)) == 0)
    }
    @Test func exactNamesKeepUnicodeAndHomonymCandidates() {
        let entities = [BibleEntity(id: "m1", type: .person, name: "Moisés", summary: nil), BibleEntity(id: "m2", type: .person, name: "Moisés", summary: nil)]
        let text = "Moisés, Moisésa e MOISÉS; λόγος."
        let segments = ReaderEntityLinker.segments(text, entities: entities)
        #expect(segments.map(\.text).joined() == text)
        #expect(segments.filter { !$0.entityIDs.isEmpty }.count == 2)
        #expect(segments.first?.entityIDs == ["m1", "m2"])
    }
    @Test func annotationIdentityDoesNotDependOnTranslation() throws {
        let note = ReaderAnnotation(reference: .init(bookId: "John", chapter: 1, verses: 14...14), highlight: .gold, note: "Minha nota — λόγος")
        let restored = try JSONDecoder().decode(ReaderAnnotation.self, from: JSONEncoder().encode(note))
        #expect(restored == note)
        #expect(restored.id == "John.1.14")
        #expect(ReaderCanon.chapters.count == 1189)
    }
}
