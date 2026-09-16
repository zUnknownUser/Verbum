import Foundation
import Testing
import ComposableArchitecture
import Models
@testable import Clients

struct AnnotationIsolationTests {
 @Test func accountsRemainSeparateAndDeletedAccountRejectsLateWrites() async throws {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    defer { try? FileManager.default.removeItem(at: root) }
    let writable = LockIsolated(true)
    let a = AnnotationFile(owner: "a", url: root.appendingPathComponent("a/notes.json"), writable: { writable.value })
    let b = AnnotationFile(owner: "b", url: root.appendingPathComponent("b/notes.json"), writable: { true })
    let ref = PassageReference(bookId: "John", chapter: 3, verses: 16...16)
    try await a.save(ReaderAnnotation(reference: ref, note: "Private A"))
    #expect(try await b.load().isEmpty)
    try await b.save(ReaderAnnotation(reference: ref, note: "Private B"))
    try await a.save(ReaderAnnotation(reference: ref, note: "Late A"))
    #expect(try await b.load().first?.note == "Private B")
    writable.setValue(false)
    await #expect(throws: (any Error).self) { try await a.save(ReaderAnnotation(reference: ref, note: "Deleted")) }
    #expect(try await b.load().first?.note == "Private B")
 }
 @Test func ownershipKeysCannotTraversePathsOrCollideWithGuest() {
    #expect(LocalAccountData.key("../../notes").count == 64)
    #expect(LocalAccountData.key(nil) != LocalAccountData.key("local-guest"))
 }
}
