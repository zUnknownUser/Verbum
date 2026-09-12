package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.GraphClient
import com.nexussoft.verbum.clients.TimelineClient
import com.nexussoft.verbum.common.arch.Effect
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.runEffect
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.models.EntityId
import com.nexussoft.verbum.models.TimelineDatePrecision
import com.nexussoft.verbum.models.TimelineEvent
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

/**
 * The timeline (docs/PRODUCT.md §4.2, §21.5): periods and events in order, each with its dating
 * and how sure that dating is. Tapping an event opens it in place. Opened from an entity page, it
 * highlights that entity's events. Twin of iOS `TimelineFeature`.
 */
object TimelineFeature {
    data class State(
        /** The entity whose events are highlighted, when opened from its page. */
        val highlight: EntityId? = null,
        val content: Content = Content.Idle,
        val selectedId: String? = null,
        /** Names for the entities the events mention, resolved after loading. */
        val entityNames: Map<EntityId, String> = emptyMap(),
    ) {
        /** The first highlighted event, to scroll to on arrival. */
        val highlightedEventId: String?
            get() = highlight?.let { h -> (content as? Content.Loaded)?.events?.firstOrNull { h in it.entityIds }?.id }
    }

    sealed interface Content {
        data object Idle : Content
        data object Loading : Content
        data class Loaded(val events: List<TimelineEvent>) : Content
        data object Failed : Content
    }

    sealed interface Action {
        data object Started : Action
        data object RetryTapped : Action
        data class EventsLoaded(val events: List<TimelineEvent>) : Action
        data object EventsFailed : Action
        data class NamesLoaded(val names: Map<EntityId, String>) : Action
        data class EventTapped(val id: String) : Action
        data class EntityTapped(val id: EntityId) : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }

    sealed interface DelegateAction {
        data class OpenEntity(val id: EntityId) : DelegateAction
    }

    private object LoadId
    private object NamesId

    fun reducer(timeline: TimelineClient, graph: GraphClient): Reducer<State, Action> {
        fun load(state: State) = state.copy(content = Content.Loading).with<State, Action>(
            runEffect(id = LoadId, cancelInFlight = true) { send ->
                try {
                    send(Action.EventsLoaded(timeline.events()))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    send(Action.EventsFailed)
                }
            },
        )
        return Reducer { state, action ->
            when (action) {
                Action.Started -> if (state.content == Content.Idle) load(state) else state.only()
                Action.RetryTapped -> load(state)
                is Action.EventsLoaded -> {
                    val loaded = state.copy(content = Content.Loaded(action.events))
                    // Arriving from an entity page: its first event starts open.
                    val opened = if (loaded.selectedId == null) loaded.copy(selectedId = loaded.highlightedEventId) else loaded
                    val ids = action.events.flatMap { it.entityIds }.toSet().sorted()
                    opened.with(
                        runEffect(id = NamesId, cancelInFlight = true) { send ->
                            val names = LinkedHashMap<EntityId, String>()
                            for (id in ids) runCatching { graph.entity(id) }.getOrNull()?.let { names[id] = it.name }
                            send(Action.NamesLoaded(names))
                        },
                    )
                }
                Action.EventsFailed -> state.copy(content = Content.Failed).only()
                is Action.NamesLoaded -> state.copy(entityNames = action.names).only()
                is Action.EventTapped -> state.copy(selectedId = if (state.selectedId == action.id) null else action.id).only()
                is Action.EntityTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenEntity(action.id))))
                is Action.Delegate -> state.only()
            }
        }
    }
}

/**
 * How a dating reads (§4.2: approximate dates and uncertainty, always shown). The words come from
 * [Words] so the UI can localise them: `c. 1010–970 BC`, `1446–1250 BC · debated`, `AD 30–33 · debated`,
 * `c. 516 BC – AD 70`, `date unknown`. Twin of iOS `TimelineDates`.
 */
object TimelineDates {
    /** Localised fragments. Defaults are the English base strings. */
    data class Words(
        val circa: String = "c.",
        val debated: String = "debated",
        val unknown: String = "date unknown",
        val bc: (Int) -> String = { "$it BC" },
        val ad: (Int) -> String = { "AD $it" },
        val bcRange: (Int, Int) -> String = { a, b -> "$a–$b BC" },
        val adRange: (Int, Int) -> String = { a, b -> "AD $a–$b" },
    )

    fun text(event: TimelineEvent, words: Words = Words()): String {
        val start = event.startYear ?: return words.unknown
        val prefix = if (event.datePrecision == TimelineDatePrecision.APPROXIMATE) words.circa + " " else ""
        val end = event.endYear
        val span = when {
            end == null || end == start -> year(start, words)
            start < 0 && end < 0 -> words.bcRange(-start, -end)
            start >= 0 && end >= 0 -> words.adRange(start, end)
            else -> "${year(start, words)} – ${year(end, words)}"
        }
        val suffix = if (event.datePrecision == TimelineDatePrecision.DEBATED) " · " + words.debated else ""
        return prefix + span + suffix
    }

    fun year(year: Int, words: Words = Words()): String = if (year < 0) words.bc(abs(year)) else words.ad(year)
}
