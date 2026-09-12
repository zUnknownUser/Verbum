import Clients
import ComposableArchitecture
import Foundation
import Models
import Testing
@testable import Features

private func passage(_ reference: PassageReference, _ text: String) -> BiblePassage {
    BiblePassage(id: "t", translationId: "BSB", bookId: reference.bookId, chapter: reference.chapter, verseStart: reference.verses!.lowerBound, verseEnd: reference.verses!.upperBound, text: text)
}

@MainActor
@Suite struct DailyVerseFeatureTests {
    /// 2026-09-12 09:30 local: the verse for that day is 2 Timothy 1:7.
    static let saturdayMorning = Date(timeIntervalSince1970: 1_789_216_200)
    static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/Sao_Paulo")!
        return calendar
    }


    @Test func showsTodaysVerseAndItsShareText() async {
        let today = Self.calendar.dateComponents([.year, .month, .day], from: Self.saturdayMorning)
        let expected = DailyVerses.verse(year: today.year!, month: today.month!, day: today.day!)
        let store = TestStore(initialState: DailyVerseFeature.State()) {
            DailyVerseFeature()
        } withDependencies: {
            $0.date.now = Self.saturdayMorning
            $0.calendar = Self.calendar
            $0.bibleClient.passage = { reference in passage(reference, "For God has not given us a spirit of fear.") }
            $0.notificationClient.authorization = { .notDetermined }
        }
        await store.send(.task) { $0.reference = expected }
        await store.receive(\.textResponse.success) {
            $0.text = .loaded(passage(expected, "For God has not given us a spirit of fear."))
        }
        await store.receive(\.authorizationResponse, .notDetermined)
        #expect(store.state.shareText == "\u{201C}For God has not given us a spirit of fear.\u{201D}\n— \(expected.formatted) · Berean Standard Bible")
        await store.send(.openTapped)
        await store.receive(\.delegate.openPassage, expected)
    }

    @Test func turningMorningsOnAsksThenSchedulesFourteenDays() async throws {
        let scheduled = LockIsolated<[VerseNotification]>([])
        let granted = LockIsolated(false)
        let store = TestStore(initialState: DailyVerseFeature.State()) {
            DailyVerseFeature()
        } withDependencies: {
            $0.date.now = Self.saturdayMorning
            $0.calendar = Self.calendar
            $0.bibleClient.passage = { reference in passage(reference, "text of \(reference.formatted)") }
            $0.notificationClient.requestAuthorization = { granted.setValue(true); return true }
            $0.notificationClient.authorization = { granted.value ? .authorized : .notDetermined }
            $0.notificationClient.scheduleVerses = { scheduled.setValue($0) }
        }
        await store.send(.morningsToggled(true)) { $0.$morningsEnabled.withLock { $0 = true } }
        await store.receive(\.authorizationResponse, .authorized) { $0.authorization = .authorized }
        await store.finish()

        let plan = scheduled.value
        #expect(plan.count == 14)
        // 09:30 is past 7:00, so the plan starts tomorrow.
        let first = try #require(plan.first)
        let stamp: [Int] = [first.year, first.month, first.day, first.hour, first.minute]
        #expect(stamp == [2026, 9, 13, 7, 0])
        #expect(plan.first?.reference == PassageReference(bookId: "Ps", chapter: 121, verses: 1...1))
        #expect(first.body.hasPrefix("\u{201C}text of "))
        #expect(Set(plan.map(\.id)).count == 14)
        #expect(store.state.morningsActive)
    }

    @Test func deniedPermissionKeepsTheChoiceAndSchedulesNothing() async {
        let scheduled = LockIsolated(false)
        let store = TestStore(initialState: DailyVerseFeature.State()) {
            DailyVerseFeature()
        } withDependencies: {
            $0.date.now = Self.saturdayMorning
            $0.calendar = Self.calendar
            $0.bibleClient.passage = { _ in throw BibleClientError.networkUnavailable }
            $0.notificationClient.requestAuthorization = { false }
            $0.notificationClient.authorization = { .denied }
            $0.notificationClient.scheduleVerses = { _ in scheduled.setValue(true) }
        }
        await store.send(.morningsToggled(true)) { $0.$morningsEnabled.withLock { $0 = true } }
        await store.receive(\.authorizationResponse, .denied) { $0.authorization = .denied }
        await store.finish()
        #expect(!scheduled.value)
        #expect(!store.state.morningsActive)
    }

    @Test func turningMorningsOffCancels() async {
        let cancelled = LockIsolated(false)
        let store = TestStore(initialState: DailyVerseFeature.State()) {
            DailyVerseFeature()
        } withDependencies: {
            $0.notificationClient.cancelVerses = { cancelled.setValue(true) }
        }
        store.state.$morningsEnabled.withLock { $0 = true }
        await store.send(.morningsToggled(false)) { $0.$morningsEnabled.withLock { $0 = false } }
        await store.finish()
        #expect(cancelled.value)
    }

    @Test func planFallsBackToTheReferenceWhenTextIsUnavailable() async {
        let plan = await DailyVersePlan.build(
            from: Self.saturdayMorning, calendar: Self.calendar, days: 2, hour: 7, title: "Verse of the day",
            text: { _ in nil }
        )
        #expect(plan.map(\.body) == [PassageReference(bookId: "Ps", chapter: 121, verses: 1...1).formatted, DailyVerses.verse(year: 2026, month: 9, day: 14).formatted])
    }

    @Test func planIncludesTodayBeforeSeven() async {
        let sixAM = Self.calendar.date(bySettingHour: 6, minute: 0, second: 0, of: Self.saturdayMorning)!
        let plan = await DailyVersePlan.build(from: sixAM, calendar: Self.calendar, days: 1, hour: 7, title: "", text: { _ in "t" })
        let stamps: [[Int]] = plan.map { [$0.month, $0.day] }
        #expect(stamps == [[9, 12]])
        #expect(plan.first?.reference == PassageReference(bookId: "2Tim", chapter: 1, verses: 7...7))
    }
}
