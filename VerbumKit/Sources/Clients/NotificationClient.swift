import ComposableArchitecture
import Models

/// Local notifications for the verse of the day. Features schedule a short
/// plan of dated notifications (the text is resolved when the plan is built,
/// so delivery needs no network) and learn which verse was tapped.
@DependencyClient
public struct NotificationClient: Sendable {
    public var authorization: @Sendable () async -> NotificationAuthorization = { .notDetermined }
    /// Asks once; the system remembers the answer. `true` when granted.
    public var requestAuthorization: @Sendable () async throws -> Bool
    /// Replaces every pending verse notification with these.
    public var scheduleVerses: @Sendable (_ notifications: [VerseNotification]) async -> Void
    public var cancelVerses: @Sendable () async -> Void
    /// The verse of each notification the user taps, including the one that
    /// launched the app. Never finishes on its own.
    public var openedVerses: @Sendable () -> AsyncStream<PassageReference> = { .finished }
}

public enum NotificationAuthorization: Equatable, Sendable {
    case notDetermined, authorized, denied
}

/// One dated local notification carrying a verse. Wall-clock components, not a
/// `Date`, so "7:00" stays 7:00 across time-zone changes.
public struct VerseNotification: Equatable, Sendable {
    public let year: Int
    public let month: Int
    public let day: Int
    public let hour: Int
    public let minute: Int
    public let title: String
    public let body: String
    public let reference: PassageReference

    public init(year: Int, month: Int, day: Int, hour: Int, minute: Int, title: String, body: String, reference: PassageReference) {
        self.year = year
        self.month = month
        self.day = day
        self.hour = hour
        self.minute = minute
        self.title = title
        self.body = body
        self.reference = reference
    }

    /// Stable per day, so rescheduling replaces rather than duplicates.
    public var id: String { "dailyVerse.\(year)-\(month)-\(day)" }
}

extension NotificationClient: DependencyKey {
    public static let liveValue = NotificationClient.userNotifications
    public static let previewValue = NotificationClient(
        authorization: { .authorized },
        requestAuthorization: { true },
        scheduleVerses: { _ in },
        cancelVerses: {},
        openedVerses: { .finished }
    )
}

extension DependencyValues {
    public var notificationClient: NotificationClient {
        get { self[NotificationClient.self] }
        set { self[NotificationClient.self] = newValue }
    }
}
