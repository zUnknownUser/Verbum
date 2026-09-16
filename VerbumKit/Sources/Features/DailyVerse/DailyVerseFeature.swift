import Clients
import ComposableArchitecture
import Foundation
import Models

/// The verse of the day on Home (spec §14): one verse, its text, a way to open
/// it in the reader, a way to share it, and an opt-in morning notification.
/// Every device shows the same verse on the same day (`DailyVerses`).
@Reducer
public struct DailyVerseFeature {
    @ObservableState
    public struct State: Equatable {
        public var reference: PassageReference = DailyVerses.pool[0]
        public var text: TextState = .loading
        /// The user's choice. Kept even while the system permission is denied,
        /// so turning notifications back on in Settings is enough.
        @Shared(.dailyVerseMornings) public var morningsEnabled
        @Shared(.dailyVerseReminderMinute) public var reminderMinute
        public var authorization: NotificationAuthorization = .notDetermined

        public init() {}

        /// What the share sheet sends: the verse, its reference, its translation.
        public var shareText: String? {
            guard case .loaded(let passage) = text else { return nil }
            return DailyVerseShare.text(passage, reference: reference)
        }

        /// The toggle is on and the system agrees.
        public var morningsActive: Bool { morningsEnabled && authorization == .authorized }
    }

    public enum TextState: Equatable, Sendable {
        case loading
        case loaded(BiblePassage)
        case failed
    }

    public enum Action: Equatable {
        case task
        case textResponse(Result<BiblePassage, Failure>)
        case authorizationResponse(NotificationAuthorization)
        case openTapped
        case morningsToggled(Bool)
        case reminderTimeChanged(Int)
        case delegate(Delegate)

        @CasePathable
        public enum Delegate: Equatable {
            case openPassage(PassageReference)
        }
    }

    public struct Failure: Error, Equatable, Sendable {
        public init() {}
    }

    /// How far ahead notifications are planned; refreshed on every launch.
    public static let plannedDays = 14

    @Dependency(\.date.now) var now
    @Dependency(\.calendar) var calendar
    @Dependency(\.bibleClient) var bibleClient
    @Dependency(\.notificationClient) var notificationClient

    public init() {}

    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .task:
                state.reference = DailyVerses.verse(on: now, calendar: calendar)
                state.text = .loading
                return .merge(
                    .run { [bibleClient, reference = state.reference] send in
                        do {
                            await send(.textResponse(.success(try await bibleClient.passage(reference))))
                        } catch {
                            await send(.textResponse(.failure(Failure())))
                        }
                    },
                    .run { [notificationClient] send in
                        await send(.authorizationResponse(await notificationClient.authorization()))
                    }
                )

            case .textResponse(.success(let passage)):
                state.text = .loaded(passage)
                return .none

            case .textResponse(.failure):
                state.text = .failed
                return .none

            case .authorizationResponse(let authorization):
                state.authorization = authorization
                return authorization == .authorized && state.morningsEnabled
                    ? plan(minuteOfDay: state.reminderMinute) : .none

            case .openTapped:
                return .send(.delegate(.openPassage(state.reference)))

            case .morningsToggled(true):
                state.$morningsEnabled.withLock { $0 = true }
                return .run { [notificationClient] send in
                    let granted = (try? await notificationClient.requestAuthorization()) ?? false
                    await send(.authorizationResponse(granted ? .authorized : .denied))
                }

            case .morningsToggled(false):
                state.$morningsEnabled.withLock { $0 = false }
                return .concatenate(.cancel(id: CancelID.plan), .run { [notificationClient] _ in await notificationClient.cancelVerses() })

            case .reminderTimeChanged(let minute):
                guard (0..<1440).contains(minute) else { return .none }
                state.$reminderMinute.withLock { $0 = minute }
                return state.morningsEnabled ? plan(minuteOfDay: minute) : .none

            case .delegate:
                return .none
            }
        }
    }

    /// Schedules the next `plannedDays` mornings, replacing what was pending.
    /// Skipped silently when the user has not granted notifications: asking
    /// the system to schedule would be a no-op, and the plan is rebuilt on the
    /// next launch anyway.
    private func plan(minuteOfDay: Int) -> Effect<Action> {
        .run { [now, calendar, bibleClient, notificationClient] _ in
            guard await notificationClient.authorization() == .authorized else { return }
            let notifications = await DailyVersePlan.build(
                from: now,
                calendar: calendar,
                days: Self.plannedDays,
                hour: minuteOfDay / 60,
                minute: minuteOfDay % 60,
                title: L10n.t("Verse of the day"),
                text: { reference in try? await bibleClient.passage(reference).text }
            )
            guard !Task.isCancelled else { return }
            await notificationClient.scheduleVerses(notifications)
        }
        .cancellable(id: CancelID.plan, cancelInFlight: true)
    }

    private enum CancelID { case plan }
}

extension SharedKey where Self == AppStorageKey<Int>.Default {
    public static var dailyVerseReminderMinute: Self {
        Self[.appStorage("dailyVerseReminderMinute"), default: 7 * 60]
    }
}

extension SharedKey where Self == AppStorageKey<Bool>.Default {
    /// Whether the user asked for the verse every morning.
    public static var dailyVerseMornings: Self {
        Self[.appStorage("dailyVerseMornings"), default: false]
    }
}

/// Builds the dated notifications for the mornings ahead. Pure apart from
/// `text`, so tests can pin the dates and the fallback body.
public enum DailyVersePlan {
    public static func build(
        from now: Date,
        calendar: Calendar,
        days: Int,
        hour: Int,
        minute: Int = 0,
        title: String,
        text: @Sendable (PassageReference) async -> String?
    ) async -> [VerseNotification] {
        var notifications: [VerseNotification] = []
        let today = calendar.startOfDay(for: now)
        // The chosen local time today is included only while still ahead.
        let nowMinute = calendar.component(.hour, from: now) * 60 + calendar.component(.minute, from: now)
        let firstOffset = nowMinute < hour * 60 + minute ? 0 : 1
        for offset in firstOffset..<(firstOffset + days) {
            guard let date = calendar.date(byAdding: .day, value: offset, to: today) else { continue }
            let c = calendar.dateComponents([.year, .month, .day], from: date)
            guard let year = c.year, let month = c.month, let day = c.day else { continue }
            let reference = DailyVerses.verse(year: year, month: month, day: day)
            let text = await text(reference)
            notifications.append(VerseNotification(
                year: year, month: month, day: day, hour: hour, minute: minute,
                title: title,
                body: body(reference: reference, text: text),
                reference: reference
            ))
        }
        return notifications
    }

    /// `“text” — John 3:16`, or just the reference when the text could not be
    /// read while planning (the tap still opens the verse).
    static func body(reference: PassageReference, text: String?) -> String {
        guard let text, !text.isEmpty else { return reference.formatted }
        return "\u{201C}\(text)\u{201D} — \(reference.formatted)"
    }
}

/// What leaves the app when the verse is shared.
public enum DailyVerseShare {
    public static func text(_ passage: BiblePassage, reference: PassageReference) -> String {
        "\u{201C}\(passage.text)\u{201D}\n— \(reference.formatted) · \(HelloAOTranslation.name(for: passage.translationId))"
    }
}
