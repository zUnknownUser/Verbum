package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.BibleClient
import com.nexussoft.verbum.clients.NotificationAuthorization
import com.nexussoft.verbum.clients.NotificationClient
import com.nexussoft.verbum.clients.PreferencesClient
import com.nexussoft.verbum.clients.VerseNotification
import com.nexussoft.verbum.clients.helloao.HelloAOTranslation
import com.nexussoft.verbum.common.arch.Effect
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.merge
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.runEffect
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.models.BiblePassage
import com.nexussoft.verbum.models.DailyVerses
import com.nexussoft.verbum.models.PassageReference
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The verse of the day on Home (§14): one verse, its text, a way to open it in the reader, a
 * way to share it, and an opt-in morning notification. Twin of iOS `DailyVerseFeature`.
 */
object DailyVerseFeature {
    data class State(
        val reference: PassageReference = DailyVerses.pool[0],
        val text: TextState = TextState.Loading,
        /** The user's choice. Kept while the system permission is denied, so re-enabling in Settings is enough. */
        val morningsEnabled: Boolean = false,
        val authorization: NotificationAuthorization = NotificationAuthorization.NOT_DETERMINED,
    ) {
        /** What the share sheet sends: the verse, its reference, its translation. */
        val shareText: String? get() = (text as? TextState.Loaded)?.let { DailyVerseShare.text(it.passage, reference) }

        /** The toggle is on and the system agrees. */
        val morningsActive: Boolean get() = morningsEnabled && authorization == NotificationAuthorization.AUTHORIZED
    }

    sealed interface TextState {
        data object Loading : TextState
        data class Loaded(val passage: BiblePassage) : TextState
        data object Failed : TextState
    }

    sealed interface Action {
        data object Started : Action
        data class TextLoaded(val passage: BiblePassage) : Action
        data object TextFailed : Action
        data class AuthorizationResponse(val authorization: NotificationAuthorization) : Action
        data object OpenTapped : Action
        data class MorningsToggled(val enabled: Boolean) : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }

    sealed interface DelegateAction {
        data class OpenPassage(val reference: PassageReference) : DelegateAction
    }

    /** Wall-clock hour of the morning notification. */
    const val MORNING_HOUR = 7
    /** How far ahead notifications are planned; refreshed on every launch. */
    const val PLANNED_DAYS = 14
    const val MORNINGS_KEY = "dailyVerseMornings"
    private const val PLAN_ID = "dailyVerse.plan"

    fun reducer(
        bibleClient: BibleClient,
        notifications: NotificationClient,
        preferences: PreferencesClient,
        clock: Clock = Clock.systemDefaultZone(),
        title: () -> String = { "Verse of the day" },
    ): Reducer<State, Action> {
        /** Schedules the next [PLANNED_DAYS] mornings, replacing what was pending. Skipped without permission. */
        fun plan(): Effect<Action> = runEffect(id = PLAN_ID, cancelInFlight = true) {
            if (notifications.authorization() != NotificationAuthorization.AUTHORIZED) return@runEffect
            val plan = DailyVersePlan.build(LocalDateTime.now(clock), PLANNED_DAYS, MORNING_HOUR, title()) { reference ->
                runCatching { bibleClient.passage(reference).text }.getOrNull()
            }
            notifications.scheduleVerses(plan)
        }

        return Reducer { state, action ->
            when (action) {
                Action.Started -> {
                    val enabled = preferences.string(MORNINGS_KEY) == "true"
                    val reference = DailyVerses.verse(LocalDate.now(clock))
                    state.copy(reference = reference, text = TextState.Loading, morningsEnabled = enabled).with(
                        merge(
                            runEffect { send ->
                                val passage = runCatching { bibleClient.passage(reference) }.getOrNull()
                                send(if (passage != null) Action.TextLoaded(passage) else Action.TextFailed)
                            },
                            runEffect { send -> send(Action.AuthorizationResponse(notifications.authorization())) },
                            if (enabled) plan() else Effect.None,
                        ),
                    )
                }
                is Action.TextLoaded -> state.copy(text = TextState.Loaded(action.passage)).only()
                Action.TextFailed -> state.copy(text = TextState.Failed).only()
                is Action.AuthorizationResponse -> state.copy(authorization = action.authorization).only()
                Action.OpenTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenPassage(state.reference))))
                is Action.MorningsToggled -> {
                    preferences.setString(MORNINGS_KEY, action.enabled.toString())
                    if (action.enabled) {
                        state.copy(morningsEnabled = true).with(
                            runEffect { send ->
                                val granted = runCatching { notifications.requestAuthorization() }.getOrDefault(false)
                                send(Action.AuthorizationResponse(if (granted) NotificationAuthorization.AUTHORIZED else NotificationAuthorization.DENIED))
                                // After the answer, so a fresh grant is honoured in the same tap.
                                if (granted) {
                                    val plan = DailyVersePlan.build(LocalDateTime.now(clock), PLANNED_DAYS, MORNING_HOUR, title()) { reference ->
                                        runCatching { bibleClient.passage(reference).text }.getOrNull()
                                    }
                                    notifications.scheduleVerses(plan)
                                }
                            },
                        )
                    } else {
                        state.copy(morningsEnabled = false).with(runEffect { notifications.cancelVerses() })
                    }
                }
                is Action.Delegate -> state.only()
            }
        }
    }
}

/** Builds the dated notifications for the mornings ahead. Pure apart from [text]. Twin of iOS `DailyVersePlan`. */
object DailyVersePlan {
    suspend fun build(now: LocalDateTime, days: Int, hour: Int, title: String, text: suspend (PassageReference) -> String?): List<VerseNotification> {
        // Today's morning only if it is still ahead; otherwise the plan starts tomorrow.
        val first = if (now.hour < hour) now.toLocalDate() else now.toLocalDate().plusDays(1)
        return (0 until days).map { offset ->
            val date = first.plusDays(offset.toLong())
            val reference = DailyVerses.verse(date)
            VerseNotification(
                year = date.year, month = date.monthValue, day = date.dayOfMonth, hour = hour, minute = 0,
                title = title,
                body = body(reference, text(reference)),
                reference = reference,
            )
        }
    }

    /** `“text” — John 3:16`, or just the reference when the text could not be read while planning. */
    fun body(reference: PassageReference, text: String?): String =
        if (text.isNullOrEmpty()) reference.formatted else "“$text” — ${reference.formatted}"
}

/** What leaves the app when the verse is shared. Twin of iOS `DailyVerseShare`. */
object DailyVerseShare {
    fun text(passage: BiblePassage, reference: PassageReference): String =
        "“${passage.text}”\n— ${reference.formatted} · ${HelloAOTranslation.name(passage.translationId)}"
}
