import ComposableArchitecture
import Foundation
import Models
import Testing

@testable import Clients

@Suite struct PersonalSyncTests {
    @Test func durableRetryPreservesEditsDuringUpload() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = directory.appendingPathComponent("ledger.json")
        let id = "annotation:Job.38.4"
        let note = PersonalValue(book: "Job", chapter: 38, verse: 4, note: "First")
        let newer = PersonalValue(book: "Job", chapter: 38, verse: 4, note: "Edited during upload")
        let local = LockIsolated([id: note])
        let mutation = LockIsolated("")
        enum Offline: Error { case disconnected }
        let failing = PersonalSyncClient(
            file: file, read: { local.value }, apply: { _, _ in },
            exchange: { request in
                mutation.setValue(request.changes[0].mutationId)
                throw Offline.disconnected
            }, active: { true })
        await #expect(throws: Offline.self) { try await failing.sync() }
        let calls = LockIsolated(0)
        let restarted = PersonalSyncClient(
            file: file, read: { local.value },
            apply: { record, expected in
                local.withValue { if $0[record.id] == expected { $0[record.id] = record.value } }
            },
            exchange: { request in
                let count = calls.withValue {
                    $0 += 1
                    return $0
                }
                let change = try #require(request.changes.first)
                if count == 1 {
                    #expect(change.mutationId == mutation.value)
                    local.withValue { $0[id] = newer }
                }
                return PersonalSyncResponse(
                    cursor: Int64(count), more: false, records: [],
                    accepted: [.init(id: id, revision: Int64(count), value: change.value)])
            }, active: { true })
        #expect(try await restarted.sync() == false)
        #expect(local.value[id] == newer)
        #expect(try await restarted.sync())
    }
    @Test func changedAccountCannotApplyResponse() async throws {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: file) }
        let active = LockIsolated(true)
        let applied = LockIsolated(false)
        let client = PersonalSyncClient(
            file: file, read: { [:] }, apply: { _, _ in applied.setValue(true) },
            exchange: { _ in
                active.setValue(false)
                return .init(
                    cursor: 1, more: false,
                    records: [.init(id: "day:2026-09-28", revision: 1, value: .init(day: "2026-09-28"))], accepted: [])
            }, active: { active.value })
        await #expect(throws: CancellationError.self) { try await client.sync() }
        #expect(!applied.value)
    }

    @Test func cursorOnlyAdvancesAfterDownloadedDataReachesDisk() async throws {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: file) }
        let id = "day:2026-09-28"
        let value = PersonalValue(day: "2026-09-28")
        let local = LockIsolated([String: PersonalValue]())
        let canSave = LockIsolated(false)
        enum DiskFailure: Error { case full }
        let client = PersonalSyncClient(
            file: file, read: { local.value },
            apply: { record, _ in
                local.withValue { $0[record.id] = record.value }
            },
            flush: {
                if !canSave.value { throw DiskFailure.full }
            },
            exchange: { request in
                #expect(request.cursor == 0)
                return .init(cursor: 1, more: false, records: [.init(id: id, revision: 1, value: value)], accepted: [])
            }, active: { true })
        await #expect(throws: DiskFailure.self) { try await client.sync() }
        #expect(!FileManager.default.fileExists(atPath: file.path))
        local.setValue([:])  // Simulate losing the unflushed in-memory value on restart.
        canSave.setValue(true)
        #expect(try await client.sync())
        #expect(local.value[id] == value)
    }

    @Test func missingLocalHistoryIsRestoredWithoutSendingDeletion() async throws {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: file) }
        let id = "day:2026-09-28"
        let value = PersonalValue(day: "2026-09-28")
        let local = LockIsolated([String: PersonalValue]())
        let client = PersonalSyncClient(
            file: file, read: { local.value },
            apply: { record, _ in
                local.withValue { $0[record.id] = record.value }
            },
            exchange: { request in
                #expect(request.changes.isEmpty)
                return .init(
                    cursor: 1, more: false,
                    records: request.cursor == 0 ? [.init(id: id, revision: 1, value: value)] : [], accepted: [])
            }, active: { true })
        #expect(try await client.sync())
        local.setValue([:])
        #expect(try await client.sync())
        #expect(local.value[id] == value)
    }
}
