import Models
import UserNotifications

extension NotificationClient {
    /// `UNUserNotificationCenter`. Verse notifications are the only ones the app
    /// sends, identified by `VerseNotification.id`'s prefix.
    public static let userNotifications = NotificationClient(
        authorization: {
            switch await UNUserNotificationCenter.current().notificationSettings().authorizationStatus {
            case .notDetermined: .notDetermined
            case .denied: .denied
            case .authorized, .provisional, .ephemeral: .authorized
            @unknown default: .denied
            }
        },
        requestAuthorization: {
            try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
        },
        scheduleVerses: { notifications in
            let center = UNUserNotificationCenter.current()
            await center.removePendingVerseNotifications()
            for notification in notifications {
                let content = UNMutableNotificationContent()
                content.title = notification.title
                content.body = notification.body
                content.sound = .default
                content.userInfo = VerseNotificationRelay.userInfo(for: notification.reference)
                var components = DateComponents()
                components.year = notification.year
                components.month = notification.month
                components.day = notification.day
                components.hour = notification.hour
                components.minute = notification.minute
                let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
                try? await center.add(UNNotificationRequest(identifier: notification.id, content: content, trigger: trigger))
            }
        },
        cancelVerses: {
            await UNUserNotificationCenter.current().removePendingVerseNotifications()
        },
        openedVerses: { VerseNotificationRelay.shared.stream }
    )

    /// Call before the app finishes launching (`App.init`), or the tap that
    /// opened the app is lost — the system only hands it to a delegate that is
    /// already installed.
    public static func installNotificationDelegate() {
        UNUserNotificationCenter.current().delegate = VerseNotificationRelay.shared
    }
}

extension UNUserNotificationCenter {
    fileprivate func removePendingVerseNotifications() async {
        let ids = await pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix("dailyVerse.") }
        removePendingNotificationRequests(withIdentifiers: ids)
    }
}

/// Forwards notification taps to whoever reads `stream` (the app shell).
/// Taps that arrive before anyone reads are buffered, which covers cold start.
final class VerseNotificationRelay: NSObject, UNUserNotificationCenterDelegate, @unchecked Sendable {
    static let shared = VerseNotificationRelay()

    let stream: AsyncStream<PassageReference>
    private let continuation: AsyncStream<PassageReference>.Continuation

    private override init() {
        (stream, continuation) = AsyncStream.makeStream(of: PassageReference.self)
        super.init()
    }

    static func userInfo(for reference: PassageReference) -> [String: Any] {
        var info: [String: Any] = ["bookId": reference.bookId, "chapter": reference.chapter]
        if let verses = reference.verses {
            info["verseStart"] = verses.lowerBound
            info["verseEnd"] = verses.upperBound
        }
        return info
    }

    static func reference(from userInfo: [AnyHashable: Any]) -> PassageReference? {
        guard let bookId = userInfo["bookId"] as? String, let chapter = userInfo["chapter"] as? Int else { return nil }
        if let start = userInfo["verseStart"] as? Int, let end = userInfo["verseEnd"] as? Int, start <= end {
            return PassageReference(bookId: bookId, chapter: chapter, verses: start...end)
        }
        return PassageReference(bookId: bookId, chapter: chapter)
    }

    // Show the morning verse even if the app happens to be open.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        guard let reference = Self.reference(from: response.notification.request.content.userInfo) else { return }
        continuation.yield(reference)
    }
}
