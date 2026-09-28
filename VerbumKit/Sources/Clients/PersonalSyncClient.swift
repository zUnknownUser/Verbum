import Foundation
import Models

/// Durable outbox and server baseline. Captured edits survive process death and
/// retries use the same mutation ID. Applying a response never overwrites newer local edits.
public actor PersonalSyncClient {
    public typealias Snapshot = [String: PersonalValue]
    private struct Ledger: Codable {
        var cursor: Int64 = 0
        var records: [String: PersonalRecord] = [:]
        var pending: [PersonalChange] = []
    }
    private let file: URL
    private let read: @Sendable () async throws -> Snapshot
    private let apply: @Sendable (_ record: PersonalRecord, _ expected: PersonalValue?) async throws -> Void
    private let flush: @Sendable () async throws -> Void
    private let exchange: @Sendable (PersonalSyncRequest) async throws -> PersonalSyncResponse
    private let active: @Sendable () -> Bool
    private var running = false
    public init(
        file: URL, read: @escaping @Sendable () async throws -> Snapshot,
        apply: @escaping @Sendable (PersonalRecord, PersonalValue?) async throws -> Void,
        flush: @escaping @Sendable () async throws -> Void = {},
        exchange: @escaping @Sendable (PersonalSyncRequest) async throws -> PersonalSyncResponse,
        active: @escaping @Sendable () -> Bool
    ) {
        self.file = file
        self.read = read
        self.apply = apply
        self.flush = flush
        self.exchange = exchange
        self.active = active
    }
    /// Bounded catch-up avoids monopolizing the connection on a new device.
    public func sync() async throws -> Bool {
        guard !running, active() else { return false }
        running = true
        defer { running = false }
        var ledger =
            FileManager.default.fileExists(atPath: file.path)
            ? try JSONDecoder().decode(Ledger.self, from: Data(contentsOf: file)) : Ledger()
        for _ in 0..<10 {
            try Task.checkCancellation()
            guard active() else { throw CancellationError() }
            var snapshot = try await read()
            // History is cumulative. Restore known entries if local storage was
            // evicted instead of interpreting their absence as a deletion.
            let missingHistory = ledger.records.values.filter {
                !$0.id.hasPrefix("annotation:") && $0.value != nil && snapshot[$0.id] == nil
            }
            if !missingHistory.isEmpty {
                for record in missingHistory { try await apply(record, nil) }
                try await flush()
                snapshot = try await read()
            }
            if ledger.pending.isEmpty {
                let ids = Set(snapshot.keys).union(ledger.records.keys).sorted()
                ledger.pending = ids.compactMap { id in
                    let base = ledger.records[id]?.value
                    return snapshot[id] == base
                        ? nil
                        : PersonalChange(
                            id: id, base: base, value: snapshot[id], baseRevision: ledger.records[id]?.revision ?? 0)
                }
                if !ledger.pending.isEmpty { try save(ledger) }
            }
            let batch = Array(ledger.pending.prefix(100))
            let response = try await exchange(.init(cursor: ledger.cursor, changes: batch))
            try Task.checkCancellation()
            guard active() else { throw CancellationError() }
            let sent = Dictionary(uniqueKeysWithValues: batch.map { ($0.id, $0) })
            // Accepted writes can be newer than the current download page.
            for record in (response.records + response.accepted).sorted(by: { $0.revision < $1.revision }) {
                if let old = ledger.records[record.id], old.revision >= record.revision { continue }
                let expected = sent[record.id].map(\.value) ?? ledger.records[record.id]?.value
                // Don't apply an older page entry for a record acknowledged further ahead.
                if response.accepted.contains(where: { $0.id == record.id && $0.revision > record.revision }) {
                    continue
                }
                try await apply(record, expected)
                ledger.records[record.id] = record
            }
            let changed = !batch.isEmpty || response.cursor != ledger.cursor || !response.records.isEmpty
            ledger.pending.removeAll { sent[$0.id]?.mutationId == $0.mutationId }
            ledger.cursor = response.cursor
            if changed {
                // Shared file storage can debounce writes. A downloaded page must
                // reach disk before advancing the durable cursor.
                try await flush()
                try save(ledger)
            }
            if !response.more && ledger.pending.isEmpty {
                // Edits made while the request was in flight remain different from baseline.
                let current = try await read()
                return Set(current.keys).union(ledger.records.keys).allSatisfy {
                    current[$0] == ledger.records[$0]?.value
                }
            }
        }
        return false
    }
    private func save(_ ledger: Ledger) throws {
        guard active() else { throw CancellationError() }
        try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(ledger).write(
            to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
}
