import Clients
import ComposableArchitecture
import Foundation
import Models
import SwiftUI

struct PersonalSyncPresentation: Equatable {
    enum Status { case local, syncing, synced, pending }
    var status: Status = .local
    var revision = 0
    var message: String {
        let pt = BookLanguage.current == .portuguese
        switch status {
        case .local:
            return pt
                ? "Salvo neste aparelho. Entre na sua conta para sincronizar."
                : "Saved on this device. Sign in to sync."
        case .syncing: return pt ? "Sincronizando seus dados…" : "Syncing your data…"
        case .synced:
            return pt
                ? "Sincronizado com sua conta. Disponível offline." : "Synced with your account. Available offline."
        case .pending:
            return pt
                ? "Há dados aguardando sincronização. Suas alterações continuam salvas neste aparelho."
                : "Sync is pending. Your changes are still saved on this device."
        }
    }
}
private struct PersonalSyncPresentationKey: EnvironmentKey { static let defaultValue = PersonalSyncPresentation() }
extension EnvironmentValues {
    var personalSync: PersonalSyncPresentation {
        get { self[PersonalSyncPresentationKey.self] }
        set { self[PersonalSyncPresentationKey.self] = newValue }
    }
}

/// Shared history is accessed on the main actor; network and annotation I/O remain
/// in clients. This bridge is recreated with the immutable account root.
@MainActor private final class PersonalDataBridge {
    @Shared(.readingActivity) var activity
    @Shared(.lastRead) var lastRead
    let owner = LocalAccountData.owner
    func snapshot() async throws -> PersonalSyncClient.Snapshot {
        if let error = $activity.loadError { throw error }
        if let error = $lastRead.loadError { throw error }
        var values = try await PersonalAnnotationStorage.snapshot(owner: owner)
        for visit in activity.visits {
            values["visit:" + visit.id] = PersonalValue(
                book: visit.reference.bookId, chapter: visit.reference.chapter, time: milliseconds(visit.lastOpened))
        }
        for day in activity.days {
            let parts = day.split(separator: "-").compactMap { Int($0) }
            if parts.count == 3 {
                let normalized = String(format: "%04d-%02d-%02d", parts[0], parts[1], parts[2])
                values["day:" + normalized] = PersonalValue(day: normalized)
            }
        }
        if let lastRead {
            values["position:last"] = position(lastRead)
        }
        return values
    }
    func flush() async throws {
        try await $activity.save()
        try await $lastRead.save()
    }
    func position(_ reference: PassageReference) -> PersonalValue {
        PersonalValue(
            book: reference.bookId, chapter: reference.chapter,
            time: activity.visits.first(where: { $0.reference == reference }).map { milliseconds($0.lastOpened) } ?? 1)
    }
    func apply(_ record: PersonalRecord, expected: PersonalValue?) async throws {
        guard LocalAccountData.owner == owner else { throw CancellationError() }
        if record.id.hasPrefix("annotation:") {
            try await PersonalAnnotationStorage.apply(record, expected: expected, owner: owner)
        } else if record.id.hasPrefix("visit:"), let value = record.value, let book = value.book,
            let chapter = value.chapter, let time = value.time
        {
            $activity.withLock {
                $0.mergeVisit(
                    .init(bookId: book, chapter: chapter), at: Date(timeIntervalSince1970: Double(time) / 1000))
            }
        } else if let day = record.value?.day {
            let parts = day.split(separator: "-").compactMap { Int($0) }
            if parts.count == 3 { $activity.withLock { $0.mergeDay("\(parts[0])-\(parts[1])-\(parts[2])") } }
        } else if record.id == "position:last", lastRead.map(position) == expected,
            let value = record.value, let book = value.book, let chapter = value.chapter
        {
            $lastRead.withLock { $0 = .init(bookId: book, chapter: chapter) }
        }
    }
    private func milliseconds(_ date: Date) -> Int64 { Int64((date.timeIntervalSince1970 * 1000).rounded()) }
}
private struct PersonalDataSyncModifier: ViewModifier {
    @Environment(\.scenePhase) private var phase
    @State private var presentation = PersonalSyncPresentation()
    @State private var bridge = PersonalDataBridge()
    @State private var client: PersonalSyncClient?
    func body(content: Content) -> some View {
        content.environment(\.personalSync, presentation)
            .task(id: phase) {
                guard phase == .active, let uid = PersonalSyncIdentity.uid else { return }
                let owner = bridge.owner
                let sync =
                    client
                    ?? PersonalSyncClient(
                        file: LocalAccountData.url("sync-ledger.json", owner: owner),
                        read: { try await bridge.snapshot() }, apply: { try await bridge.apply($0, expected: $1) },
                        flush: { try await bridge.flush() },
                        exchange: { try await VerbumAPI.shared.syncPersonalData($0, uid: uid) },
                        active: { PersonalSyncIdentity.uid == uid && LocalAccountData.owner == owner })
                client = sync
                var delay: UInt64 = 5
                while !Task.isCancelled {
                    presentation.status = .syncing
                    do {
                        let complete = try await sync.sync()
                        presentation.status = complete ? .synced : .pending
                        presentation.revision += 1
                        delay = complete ? 30 : 5
                    } catch is CancellationError { return } catch {
                        presentation.status = .pending
                        delay = min(delay * 2, 120)
                    }
                    do { try await Task.sleep(for: .seconds(delay)) } catch { return }
                }
            }
    }
}
extension View {
    public func personalDataSync() -> some View { modifier(PersonalDataSyncModifier()) }
}
