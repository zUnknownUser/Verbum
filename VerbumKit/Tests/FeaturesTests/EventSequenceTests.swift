import Foundation
import Models
import Testing

@Suite struct EventSequenceTests {
    @Test func optionalSequenceDecodesAdjacentCanonicalEntities() throws {
        let data = Data(#"{"entity":{"id":"first","type":"event","name":"First","summary":null},"aliases":[],"keyPassages":[],"sources":[],"eventSequence":{"eraId":"exodus","title":"Êxodo","context":"Ordem de leitura","position":1,"total":16,"previous":null,"next":{"id":"next","type":"event","name":"Próximo","summary":null}}}"#.utf8)
        let detail = try JSONDecoder().decode(EntityDetail.self, from: data)
        #expect(detail.eventSequence?.position == 1)
        #expect(detail.eventSequence?.previous == nil)
        #expect(detail.eventSequence?.next?.id == "next")
        #expect(detail.eventSequence?.next?.name == "Próximo")
    }
}
