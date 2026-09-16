package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.InMemoryPreferencesClient
import com.nexussoft.verbum.clients.NotificationAuthorization
import com.nexussoft.verbum.clients.NotificationClient
import com.nexussoft.verbum.clients.VerseNotification
import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.feature.scripture.DailyVerseFeature.Action
import com.nexussoft.verbum.feature.scripture.DailyVerseFeature.DelegateAction
import com.nexussoft.verbum.feature.scripture.DailyVerseFeature.State
import com.nexussoft.verbum.feature.scripture.DailyVerseFeature.TextState
import com.nexussoft.verbum.feature.scripture.DailyVerseFeature.reducer
import com.nexussoft.verbum.models.BiblePassage
import com.nexussoft.verbum.models.DailyVerses
import com.nexussoft.verbum.models.PassageReference
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyVerseFeatureTest {
    /** 2026-09-12 09:30 in São Paulo: the verse for that day is 2 Timothy 1:7. */
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val saturdayMorning = Clock.fixed(Instant.ofEpochSecond(1_789_216_200), zone)

    private fun passage(reference: PassageReference, text: String) =
        BiblePassage("t", "BSB", reference.bookId, reference.chapter, reference.verses!!.first, reference.verses!!.last, text)

    /** A scripted [NotificationClient]: records what was scheduled. */
    private class FakeNotifications(
        var granted: Boolean = false,
        var status: () -> NotificationAuthorization = { NotificationAuthorization.NOT_DETERMINED },
    ) : NotificationClient {
        var scheduled: List<VerseNotification>? = null
        var cancelled = false
        override suspend fun authorization() = status()
        override suspend fun requestAuthorization(): Boolean = granted
        override suspend fun scheduleVerses(notifications: List<VerseNotification>) { scheduled = notifications }
        override suspend fun cancelVerses() { cancelled = true }
    }

    @Test fun showsTodaysVerseAndItsShareText() = runTest {
        val expected = DailyVerses.verse(LocalDate.of(2026, 9, 12))
        val bible = StubBibleClient(passageStub = { passage(it, "For God has not given us a spirit of fear.") })
        val store = TestStore(State(), reducer(bible, FakeNotifications(), InMemoryPreferencesClient(), saturdayMorning))
        store.send(Action.Started) { it.copy(reference = expected) }
        store.receive(Action.TextLoaded(passage(expected, "For God has not given us a spirit of fear."))) {
            it.copy(text = TextState.Loaded(passage(expected, "For God has not given us a spirit of fear.")))
        }
        store.receive(Action.AuthorizationResponse(NotificationAuthorization.NOT_DETERMINED))
        assertEquals("“For God has not given us a spirit of fear.”\n— ${expected.formatted} · Berean Standard Bible", store.state.shareText)
        store.send(Action.OpenTapped)
        store.receive(Action.Delegate(DelegateAction.OpenPassage(expected)))
        store.finish()
    }

    @Test fun turningMorningsOnAsksThenSchedulesFourteenDays() = runTest {
        val notifications = FakeNotifications(granted = true)
        notifications.status = { if (notifications.granted) NotificationAuthorization.AUTHORIZED else NotificationAuthorization.NOT_DETERMINED }
        val preferences = InMemoryPreferencesClient()
        val bible = StubBibleClient(passageStub = { passage(it, "text of ${it.formatted}") })
        val store = TestStore(State(), reducer(bible, notifications, preferences, saturdayMorning))
        store.send(Action.MorningsToggled(true)) { it.copy(morningsEnabled = true) }
        store.receive(Action.AuthorizationResponse(NotificationAuthorization.AUTHORIZED)) { it.copy(authorization = NotificationAuthorization.AUTHORIZED) }
        store.finish()

        val plan = notifications.scheduled!!
        assertEquals(14, plan.size)
        // 09:30 is past 7:00, so the plan starts tomorrow.
        assertEquals(listOf(2026, 9, 13, 7, 0), plan.first().let { listOf(it.year, it.month, it.day, it.hour, it.minute) })
        assertEquals(PassageReference("Ps", 121, 1..1), plan.first().reference)
        assertTrue(plan.first().body.startsWith("“text of "))
        assertEquals(14, plan.map { it.id }.toSet().size)
        assertEquals("true", preferences.string(DailyVerseFeature.MORNINGS_KEY))
        assertTrue(store.state.morningsActive)
    }

    @Test fun deniedPermissionKeepsTheChoiceAndSchedulesNothing() = runTest {
        val notifications = FakeNotifications(granted = false, status = { NotificationAuthorization.DENIED })
        val store = TestStore(State(), reducer(StubBibleClient(), notifications, InMemoryPreferencesClient(), saturdayMorning))
        store.send(Action.MorningsToggled(true)) { it.copy(morningsEnabled = true) }
        store.receive(Action.AuthorizationResponse(NotificationAuthorization.DENIED)) { it.copy(authorization = NotificationAuthorization.DENIED) }
        store.finish()
        assertNull(notifications.scheduled)
        assertFalse(store.state.morningsActive)
    }

    @Test fun turningMorningsOffCancels() = runTest {
        val notifications = FakeNotifications()
        val preferences = InMemoryPreferencesClient(mapOf(DailyVerseFeature.MORNINGS_KEY to "true"))
        val store = TestStore(State(morningsEnabled = true), reducer(StubBibleClient(), notifications, preferences, saturdayMorning))
        store.send(Action.MorningsToggled(false)) { it.copy(morningsEnabled = false) }
        store.finish()
        assertTrue(notifications.cancelled)
        assertEquals("false", preferences.string(DailyVerseFeature.MORNINGS_KEY))
    }

    @Test fun changingReminderTimeReschedulesAndPersists() = runTest {
        val notifications = FakeNotifications(status = { NotificationAuthorization.AUTHORIZED })
        val preferences = InMemoryPreferencesClient(mapOf(DailyVerseFeature.MORNINGS_KEY to "true"))
        val bible = StubBibleClient(passageStub = { passage(it, "text") })
        val store = TestStore(State(morningsEnabled = true), reducer(bible, notifications, preferences, saturdayMorning))
        store.send(Action.ReminderTimeChanged(20 * 60 + 45)) { it.copy(reminderMinute = 20 * 60 + 45) }
        store.finish()
        assertEquals("1245", preferences.string(DailyVerseFeature.REMINDER_MINUTE_KEY))
        assertEquals(listOf(2026, 9, 12, 20, 45), notifications.scheduled!!.first().let { listOf(it.year, it.month, it.day, it.hour, it.minute) })
    }

    @Test fun chosenTimeSchedulesTodayOnlyWhenStillAhead() = runTest {
        val today = DailyVersePlan.build(LocalDateTime.of(2026, 9, 12, 9, 30), 1, 20, "", 45) { "text" }
        assertEquals(listOf(12, 20, 45), today.first().let { listOf(it.day, it.hour, it.minute) })
        val tomorrow = DailyVersePlan.build(LocalDateTime.of(2026, 9, 12, 9, 30), 1, 9, "", 15) { "text" }
        assertEquals(listOf(13, 9, 15), tomorrow.first().let { listOf(it.day, it.hour, it.minute) })
    }

    @Test fun planFallsBackToTheReferenceWhenTextIsUnavailable() = runTest {
        val plan = DailyVersePlan.build(LocalDateTime.of(2026, 9, 12, 9, 30), 2, 7, "Verse of the day") { null }
        assertEquals(
            listOf(PassageReference("Ps", 121, 1..1).formatted, DailyVerses.verse(LocalDate.of(2026, 9, 14)).formatted),
            plan.map { it.body },
        )
    }

    @Test fun planIncludesTodayBeforeSeven() = runTest {
        val plan = DailyVersePlan.build(LocalDateTime.of(2026, 9, 12, 6, 0), 1, 7, "") { "t" }
        assertEquals(listOf(9 to 12), plan.map { it.month to it.day })
        assertEquals(PassageReference("2Tim", 1, 7..7), plan.first().reference)
    }
}
