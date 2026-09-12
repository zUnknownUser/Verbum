package com.nexussoft.verbum

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nexussoft.verbum.clients.NotificationAuthorization
import com.nexussoft.verbum.clients.NotificationClient
import com.nexussoft.verbum.clients.VerseNotification
import com.nexussoft.verbum.models.PassageReference
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Live [NotificationClient] over `AlarmManager` + `NotificationManager`.
 *
 * The plan the feature builds (dated notifications with their text) is stored as-is; one
 * inexact alarm is armed for the next entry. When it fires, [VerseAlarmReceiver] posts that
 * entry and arms the following one — no network at 7:00. After a reboot [BootReceiver]
 * re-arms from the stored plan. The app refreshes the plan on every launch.
 */
class AndroidNotificationClient(context: Context) : NotificationClient {
    private val context = context.applicationContext

    override suspend fun authorization(): NotificationAuthorization = when {
        !NotificationManagerCompat.from(context).areNotificationsEnabled() ->
            if (NotificationPermission.wasAsked(context)) NotificationAuthorization.DENIED else NotificationAuthorization.NOT_DETERMINED
        else -> NotificationAuthorization.AUTHORIZED
    }

    override suspend fun requestAuthorization(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return true
        return NotificationPermission.request(context)
    }

    override suspend fun scheduleVerses(notifications: List<VerseNotification>) {
        VersePlanStore.save(context, notifications)
        VerseAlarms.armNext(context)
    }

    override suspend fun cancelVerses() {
        VersePlanStore.save(context, emptyList())
        VerseAlarms.cancel(context)
    }

    override fun openedVerses(): Flow<PassageReference> = VerseTapRelay.flow
}

/** Bridges the runtime permission dialog (an Activity concern) to a suspending call. */
object NotificationPermission {
    private const val ASKED_KEY = "notificationPermissionAsked"
    var launcher: ActivityResultLauncher<String>? = null
    private var pending: CompletableDeferred<Boolean>? = null

    suspend fun request(context: Context): Boolean {
        val launcher = launcher ?: return false
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        prefs(context).edit().putBoolean(ASKED_KEY, true).apply()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return deferred.await()
    }

    fun complete(granted: Boolean) {
        pending?.complete(granted)
        pending = null
    }

    fun wasAsked(context: Context) = prefs(context).getBoolean(ASKED_KEY, false)
    private fun prefs(context: Context) = context.getSharedPreferences("verbum.notifications", Context.MODE_PRIVATE)
}

/** Taps on verse notifications, buffered until the app shell reads them (covers cold start). */
object VerseTapRelay {
    private val channel = Channel<PassageReference>(Channel.BUFFERED)
    val flow: Flow<PassageReference> = channel.receiveAsFlow()

    fun offer(reference: PassageReference) { channel.trySend(reference) }

    const val EXTRA_BOOK = "verse.bookId"
    const val EXTRA_CHAPTER = "verse.chapter"
    const val EXTRA_START = "verse.start"
    const val EXTRA_END = "verse.end"

    fun reference(intent: Intent?): PassageReference? {
        val bookId = intent?.getStringExtra(EXTRA_BOOK) ?: return null
        val chapter = intent.getIntExtra(EXTRA_CHAPTER, 0).takeIf { it > 0 } ?: return null
        val start = intent.getIntExtra(EXTRA_START, 0)
        val end = intent.getIntExtra(EXTRA_END, 0)
        return PassageReference(bookId, chapter, if (start in 1..end) start..end else null)
    }

    fun putReference(intent: Intent, reference: PassageReference): Intent = intent.apply {
        putExtra(EXTRA_BOOK, reference.bookId)
        putExtra(EXTRA_CHAPTER, reference.chapter)
        reference.verses?.let { putExtra(EXTRA_START, it.first); putExtra(EXTRA_END, it.last) }
    }
}

/** The stored plan: what to show on which morning. */
object VersePlanStore {
    private const val PLAN_KEY = "dailyVersePlan"

    fun save(context: Context, notifications: List<VerseNotification>) {
        val array = JSONArray()
        notifications.forEach { n ->
            array.put(JSONObject().apply {
                put("year", n.year); put("month", n.month); put("day", n.day); put("hour", n.hour); put("minute", n.minute)
                put("title", n.title); put("body", n.body)
                put("bookId", n.reference.bookId); put("chapter", n.reference.chapter)
                n.reference.verses?.let { put("start", it.first); put("end", it.last) }
            })
        }
        prefs(context).edit().putString(PLAN_KEY, array.toString()).apply()
    }

    fun load(context: Context): List<VerseNotification> {
        val raw = prefs(context).getString(PLAN_KEY, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val verses = if (o.has("start") && o.has("end")) o.getInt("start")..o.getInt("end") else null
            VerseNotification(
                o.getInt("year"), o.getInt("month"), o.getInt("day"), o.getInt("hour"), o.getInt("minute"),
                o.getString("title"), o.getString("body"), PassageReference(o.getString("bookId"), o.getInt("chapter"), verses),
            )
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("verbum.notifications", Context.MODE_PRIVATE)
}

object VerseAlarms {
    const val CHANNEL_ID = "daily_verse"
    private const val NOTIFICATION_ID = 7
    private const val ALARM_REQUEST = 7
    /** Inexact on purpose: no exact-alarm permission, and "7:00" within a quarter hour is fine. */
    private const val WINDOW_MS = 15L * 60 * 1000

    fun armNext(context: Context) {
        val now = LocalDateTime.now()
        val next = VersePlanStore.load(context).map { it to it.at() }.filter { it.second.isAfter(now) }.minByOrNull { it.second }
        val manager = context.getSystemService(AlarmManager::class.java)
        if (next == null) { manager.cancel(alarmIntent(context)); return }
        val at = next.second.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        manager.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, alarmIntent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
    }

    /** Posts the entry due now (the latest one not in the future), then arms the next. */
    fun fire(context: Context) {
        val now = LocalDateTime.now()
        val due = VersePlanStore.load(context).map { it to it.at() }.filter { !it.second.isAfter(now) }.maxByOrNull { it.second }?.first
        if (due != null && NotificationManagerCompat.from(context).areNotificationsEnabled()) post(context, due)
        armNext(context)
    }

    private fun post(context: Context, notification: VerseNotification) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_daily_verse), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notification_channel_daily_verse_description)
            },
        )
        val open = VerseTapRelay.putReference(Intent(context, MainActivity::class.java), notification.reference)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(context, NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val built = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_verse)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, built) } // SecurityException if permission was revoked meanwhile
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, ALARM_REQUEST, Intent(context, VerseAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun VerseNotification.at(): LocalDateTime = LocalDate.of(year, month, day).atTime(hour, minute)
}

class VerseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = VerseAlarms.fire(context)
}

/** Alarms do not survive a reboot; the stored plan does. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) VerseAlarms.armNext(context)
    }
}
