package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.GraphClient
import com.nexussoft.verbum.clients.TimelineClient
import com.nexussoft.verbum.common.arch.Effect
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.runEffect
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.EntityDetail
import com.nexussoft.verbum.models.EntityId
import com.nexussoft.verbum.models.GraphSnapshot
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.SourceReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** One entity's page (docs/PRODUCT.md §9). Twin of the iOS `EntityDetailFeature`. */
object EntityDetailFeature {
    data class State(val entityId: EntityId, val content: Content = Content.Idle)

    sealed interface Content {
        data object Idle : Content
        data object Loading : Content
        data class Loaded(val page: Page) : Content
        data object Failed : Content
    }

    /** [isOnTimeline]: whether the timeline has this entity (§8.2 "Open in timeline"). */
    data class Page(val detail: EntityDetail, val neighborhood: GraphSnapshot, val isOnTimeline: Boolean = false) {
        val entity: BibleEntity get() = detail.entity
        fun related(type: BibleEntityType): List<BibleEntity> = neighborhood.nodes(type)

        val passages: List<PassageReference>
            get() = detail.keyPassages.ifEmpty { neighborhood.nodes(BibleEntityType.PASSAGE).mapNotNull(::passageReference) }

        val sources: List<SourceReference>
            get() {
                val fromEdges = neighborhood.edges.flatMap { it.sourceReferenceIds }.mapNotNull { id -> detail.sources.firstOrNull { it.id == id } }
                return (detail.sources + fromEdges).distinctBy { it.id }
            }
    }

    sealed interface Action {
        data object Started : Action
        data object RetryTapped : Action
        data class PageLoaded(val page: Page) : Action
        data object PageFailed : Action
        data class PassageTapped(val reference: PassageReference) : Action
        data class EntityTapped(val entity: BibleEntity) : Action
        data object GraphTapped : Action
        data object TimelineTapped : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }

    sealed interface DelegateAction {
        data class OpenPassage(val reference: PassageReference) : DelegateAction
        data class OpenEntity(val entity: BibleEntity) : DelegateAction
        /** §8: the graph starts from the selected entity. */
        data class OpenGraph(val entityId: EntityId) : DelegateAction
        /** The timeline, scrolled to this entity's events. */
        data class OpenTimeline(val entityId: EntityId) : DelegateAction
    }

    /** §8.1: default depth one, at most ~8–12 visible nodes. */
    const val NEIGHBOR_LIMIT = 12

    private object LoadId

    /** [timeline] is optional context: if it fails, the page still loads. */
    fun reducer(graphClient: GraphClient, timeline: TimelineClient? = null): Reducer<State, Action> = Reducer { state, action ->
        when (action) {
            Action.Started, Action.RetryTapped -> state.copy(content = Content.Loading).with(
                runEffect(id = LoadId, cancelInFlight = true) { send ->
                    try {
                        val page = coroutineScope {
                            val detail = async { graphClient.detail(state.entityId) }
                            val neighborhood = async { graphClient.neighbors(state.entityId, NEIGHBOR_LIMIT) }
                            val onTimeline = async { timeline?.let { t -> runCatching { t.eventsFor(state.entityId).isNotEmpty() }.getOrDefault(false) } ?: false }
                            Page(detail.await(), neighborhood.await(), onTimeline.await())
                        }
                        send(Action.PageLoaded(page))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        send(Action.PageFailed)
                    }
                },
            )
            is Action.PageLoaded -> state.copy(content = Content.Loaded(action.page)).only()
            Action.PageFailed -> state.copy(content = Content.Failed).only()
            is Action.PassageTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenPassage(action.reference))))
            is Action.EntityTapped -> {
                val reference = passageReference(action.entity)
                if (reference != null) state.with(Effect.Send(Action.Delegate(DelegateAction.OpenPassage(reference))))
                else state.with(Effect.Send(Action.Delegate(DelegateAction.OpenEntity(action.entity))))
            }
            Action.GraphTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenGraph(state.entityId))))
            Action.TimelineTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenTimeline(state.entityId))))
            is Action.Delegate -> state.only()
        }
    }

    /** Passage nodes carry their reference in the id (`passage.1Sam.17`). */
    internal fun passageReference(node: BibleEntity): PassageReference? {
        if (node.type != BibleEntityType.PASSAGE) return null
        val parts = node.id.split(".")
        if (parts.size != 3 || parts[0] != "passage") return null
        return PassageReference(parts[1], parts[2].toIntOrNull() ?: return null)
    }
}
