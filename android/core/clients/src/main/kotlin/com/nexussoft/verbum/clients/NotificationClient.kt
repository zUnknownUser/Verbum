package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Local notifications for the verse of the day. Mirrors the iOS `NotificationClient`:
 * features schedule a short plan of dated notifications (the text is resolved when the
 * plan is built, so delivery needs no network) and learn which verse was tapped.
 */
interface NotificationClient {
    suspend fun authorization(): NotificationAuthorization
    /** Asks once; the system remembers the answer. `true` when granted. */
    suspend fun requestAuthorization(): Boolean
    /** Replaces every pending verse notification with these. */
    suspend fun scheduleVerses(notifications: List<VerseNotification>)
    suspend fun cancelVerses()
    /** The verse of each notification the user taps, including the one that launched the app. */
    fun openedVerses(): Flow<PassageReference> = emptyFlow()
}

enum class NotificationAuthorization { NOT_DETERMINED, AUTHORIZED, DENIED }

/**
 * One dated local notification carrying a verse. Wall-clock fields, not an instant, so
 * "7:00" stays 7:00 across time-zone changes.
 */
data class VerseNotification(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val title: String,
    val body: String,
    val reference: PassageReference,
) {
    /** Stable per day, so rescheduling replaces rather than duplicates. */
    val id: String get() = "dailyVerse.$year-$month-$day"
}

/** Preview / fixture implementation: grants everything, schedules nothing. */
object NoopNotificationClient : NotificationClient {
    override suspend fun authorization() = NotificationAuthorization.AUTHORIZED
    override suspend fun requestAuthorization() = true
    override suspend fun scheduleVerses(notifications: List<VerseNotification>) = Unit
    override suspend fun cancelVerses() = Unit
}
