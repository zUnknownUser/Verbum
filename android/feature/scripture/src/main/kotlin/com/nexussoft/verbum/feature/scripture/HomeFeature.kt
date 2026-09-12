package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.BibleClient
import com.nexussoft.verbum.clients.NotificationClient
import com.nexussoft.verbum.clients.PreferencesClient
import com.nexussoft.verbum.common.arch.Effect
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.combine
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.pullback
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.models.PassageReference
import java.time.Clock
import java.time.LocalDateTime

/** Home (§5): a question, a way back into what you were reading, the verse of the day. Twin of iOS `HomeFeature`. */
object HomeFeature {
    data class State(
        val lastRead: PassageReference? = null,
        val greeting: Greeting = Greeting.MORNING,
        val dailyVerse: DailyVerseFeature.State = DailyVerseFeature.State(),
    )

    enum class Greeting {
        MORNING, AFTERNOON, EVENING;

        companion object {
            fun at(hour: Int) = when (hour) {
                in 5..11 -> MORNING
                in 12..17 -> AFTERNOON
                else -> EVENING
            }
        }
    }

    sealed interface Action {
        data object Started : Action
        data object ContinueReadingTapped : Action
        data object SearchTapped : Action
        data object ArrivalTapped : Action
        data class DailyVerse(val action: DailyVerseFeature.Action) : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }

    sealed interface DelegateAction {
        data class OpenPassage(val reference: PassageReference) : DelegateAction
        data object OpenSearch : DelegateAction
        data object OpenArrival : DelegateAction
    }

    fun reducer(
        preferences: PreferencesClient,
        clock: Clock = Clock.systemDefaultZone(),
        bibleClient: BibleClient,
        notifications: NotificationClient,
        dailyVerseTitle: () -> String = { "Verse of the day" },
    ): Reducer<State, Action> = combine(
        DailyVerseFeature.reducer(bibleClient, notifications, preferences, clock, dailyVerseTitle).pullback(
            get = { it.dailyVerse }, set = { s, c -> s.copy(dailyVerse = c) },
            extractAction = { (it as? Action.DailyVerse)?.action }, embedAction = { Action.DailyVerse(it) },
        ),
        Reducer { state, action ->
            when (action) {
                Action.Started -> {
                    val now = LocalDateTime.now(clock)
                    state.copy(
                        lastRead = LastRead.decode(preferences.string(ChapterReaderFeature.LAST_READ_KEY)),
                        greeting = Greeting.at(now.hour),
                    ).only()
                }
                Action.ContinueReadingTapped -> state.lastRead?.let { state.with(Effect.Send(Action.Delegate(DelegateAction.OpenPassage(it)))) } ?: state.only()
                Action.SearchTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenSearch)))
                Action.ArrivalTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenArrival)))
                is Action.DailyVerse -> when (val d = (action.action as? DailyVerseFeature.Action.Delegate)?.delegate) {
                    is DailyVerseFeature.DelegateAction.OpenPassage -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenPassage(d.reference))))
                    null -> state.only()
                }
                is Action.Delegate -> state.only()
            }
        },
    )
}
