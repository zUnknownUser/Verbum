package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.GraphClient
import com.nexussoft.verbum.common.arch.Effect
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.runEffect
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.BibleRelationship
import com.nexussoft.verbum.models.EntityId
import com.nexussoft.verbum.models.GraphSnapshot
import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.CancellationException
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Bible Graph around one entity (docs/PRODUCT.md §8, §21.4, §44): one degree by default,
 * at most a dozen visible nodes, explicit incremental expansion, and a list representation the
 * graph never replaces (§43). Layout is deterministic rings ([GraphLayout]), not a force
 * simulation. Twin of iOS `GraphFeature`.
 */
object GraphFeature {
    data class State(
        val rootId: EntityId,
        val content: Content = Content.Idle,
        val presentation: Presentation = Presentation.GRAPH,
    )

    enum class Presentation { GRAPH, LIST }

    sealed interface Content {
        data object Idle : Content
        data object Loading : Content
        data class Loaded(val graph: Graph) : Content
        data object Failed : Content
    }

    /** What is on screen: nodes with their layout, the edges among them, which nodes were expanded. */
    data class Graph(
        val root: BibleEntity,
        val nodes: List<Node>,
        val edges: List<BibleRelationship>,
        val expanded: Set<EntityId> = emptySet(),
        /** The node whose neighbourhood is being fetched, if any. */
        val expanding: EntityId? = null,
        /** Set when an expansion was refused because the graph is full (§8.1). */
        val atCapacity: Boolean = false,
    ) {
        /** Unit coordinates: the first ring has radius 1 around the root at the origin. */
        data class Node(val entity: BibleEntity, val x: Double, val y: Double, val parentId: EntityId?) {
            val id: EntityId get() = entity.id
        }

        fun node(id: EntityId): Node? = nodes.firstOrNull { it.id == id }
        fun contains(id: EntityId): Boolean = nodes.any { it.id == id }

        /** Edges touching [id], with the entity on the other end. */
        fun connections(id: EntityId): List<Pair<BibleRelationship, BibleEntity>> = edges.mapNotNull { edge ->
            val otherId = when (id) { edge.sourceId -> edge.targetId; edge.targetId -> edge.sourceId; else -> return@mapNotNull null }
            node(otherId)?.let { edge to it.entity }
        }

        /** Sources cited by every visible edge, without duplicates (§33). */
        val sourceIds: List<String> get() = edges.flatMap { it.sourceReferenceIds }.distinct()
    }

    sealed interface Action {
        data object Started : Action
        data object RetryTapped : Action
        data class SnapshotLoaded(val snapshot: GraphSnapshot) : Action
        data object SnapshotFailed : Action
        data class NodeTapped(val entity: BibleEntity) : Action
        data class ExpandTapped(val id: EntityId) : Action
        data class ExpansionLoaded(val id: EntityId, val snapshot: GraphSnapshot) : Action
        data class ExpansionFailed(val id: EntityId) : Action
        data class FocusTapped(val id: EntityId) : Action
        data class PresentationChanged(val presentation: Presentation) : Action
        data class Delegate(val delegate: DelegateAction) : Action
    }

    sealed interface DelegateAction {
        data class OpenEntity(val entity: BibleEntity) : DelegateAction
        data class OpenPassage(val reference: PassageReference) : DelegateAction
        /** Re-root the graph on another entity (a new graph page). */
        data class Focus(val entity: BibleEntity) : DelegateAction
    }

    /** §8.1: ~8–12 visible nodes at depth one. */
    const val VISIBLE_LIMIT = 12
    /** Asked of the client, so the visible dozen can be balanced across kinds. */
    const val FETCH_LIMIT = 24
    /** New nodes one expansion may add. */
    const val EXPANSION_LIMIT = 6
    /** Beyond this the picture stops being readable; the user refocuses instead. */
    const val CAPACITY = 24

    private object LoadId
    private object ExpandId

    fun reducer(graphClient: GraphClient): Reducer<State, Action> {
        fun load(state: State): com.nexussoft.verbum.common.arch.Reduced<State, Action> = state.copy(content = Content.Loading).with(
            runEffect(id = LoadId, cancelInFlight = true) { send ->
                try {
                    send(Action.SnapshotLoaded(graphClient.neighbors(state.rootId, FETCH_LIMIT)))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    send(Action.SnapshotFailed)
                }
            },
        )

        return Reducer { state, action ->
            when (action) {
                Action.Started -> if (state.content == Content.Idle) load(state) else state.only()
                Action.RetryTapped -> load(state)
                is Action.SnapshotLoaded -> {
                    val snapshot = action.snapshot
                    val neighbours = GraphLayout.balanced(snapshot.nodes, VISIBLE_LIMIT)
                    val ids = (neighbours.map { it.id } + snapshot.root.id).toSet()
                    state.copy(
                        content = Content.Loaded(
                            Graph(
                                root = snapshot.root,
                                nodes = GraphLayout.ring(snapshot.root, neighbours),
                                edges = snapshot.edges.filter { it.sourceId in ids && it.targetId in ids },
                            ),
                        ),
                    ).only()
                }
                Action.SnapshotFailed -> state.copy(content = Content.Failed).only()
                is Action.NodeTapped -> {
                    val reference = EntityDetailFeature.passageReference(action.entity)
                    if (reference != null) state.with(Effect.Send(Action.Delegate(DelegateAction.OpenPassage(reference))))
                    else state.with(Effect.Send(Action.Delegate(DelegateAction.OpenEntity(action.entity))))
                }
                is Action.ExpandTapped -> {
                    val graph = (state.content as? Content.Loaded)?.graph ?: return@Reducer state.only()
                    if (!graph.contains(action.id) || action.id in graph.expanded || graph.expanding != null) return@Reducer state.only()
                    if (graph.nodes.size >= CAPACITY) return@Reducer state.copy(content = Content.Loaded(graph.copy(atCapacity = true))).only()
                    state.copy(content = Content.Loaded(graph.copy(expanding = action.id))).with(
                        runEffect(id = ExpandId, cancelInFlight = true) { send ->
                            try {
                                send(Action.ExpansionLoaded(action.id, graphClient.neighbors(action.id, FETCH_LIMIT)))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                send(Action.ExpansionFailed(action.id))
                            }
                        },
                    )
                }
                is Action.ExpansionLoaded -> {
                    val graph = (state.content as? Content.Loaded)?.graph ?: return@Reducer state.only()
                    if (graph.expanding != action.id) return@Reducer state.only()
                    val room = CAPACITY - graph.nodes.size
                    val fresh = GraphLayout.balanced(action.snapshot.nodes.filter { !graph.contains(it.id) }, minOf(EXPANSION_LIMIT, room))
                    val nodes = graph.nodes + GraphLayout.arc(action.id, graph, fresh)
                    val ids = nodes.map { it.id }.toSet()
                    val known = graph.edges.map { it.pairKey }.toSet()
                    val edges = graph.edges + action.snapshot.edges.filter { it.sourceId in ids && it.targetId in ids && it.pairKey !in known }
                    state.copy(
                        content = Content.Loaded(
                            graph.copy(nodes = nodes, edges = edges, expanded = graph.expanded + action.id, expanding = null, atCapacity = nodes.size >= CAPACITY),
                        ),
                    ).only()
                }
                is Action.ExpansionFailed -> {
                    val graph = (state.content as? Content.Loaded)?.graph ?: return@Reducer state.only()
                    if (graph.expanding != action.id) return@Reducer state.only()
                    state.copy(content = Content.Loaded(graph.copy(expanding = null))).only()
                }
                is Action.FocusTapped -> {
                    val graph = (state.content as? Content.Loaded)?.graph ?: return@Reducer state.only()
                    val entity = graph.node(action.id)?.entity
                    if (entity == null || action.id == graph.root.id) state.only()
                    else state.with(Effect.Send(Action.Delegate(DelegateAction.Focus(entity))))
                }
                is Action.PresentationChanged -> state.copy(presentation = action.presentation).only()
                is Action.Delegate -> state.only()
            }
        }
    }
}

/** The same connection seen from either end is one line in the picture. */
internal val BibleRelationship.pairKey: String get() = listOf(sourceId, targetId).sorted().joinToString("|") + "|" + type.wireValue

/** Deterministic placement. Same arithmetic as iOS `GraphLayout`, so both graphs match. */
object GraphLayout {
    /** The order kinds are laid out in, so like sits with like around the ring. */
    val kindOrder: List<BibleEntityType> = listOf(
        BibleEntityType.PERSON, BibleEntityType.EVENT, BibleEntityType.PLACE, BibleEntityType.THEME, BibleEntityType.PASSAGE,
        BibleEntityType.BOOK, BibleEntityType.PROPHECY, BibleEntityType.ORIGINAL_TERM, BibleEntityType.HISTORICAL_PERIOD,
    )

    /** Up to [limit] entities, round-robin across kinds, so a person with many passages still shows places and events. */
    fun balanced(entities: List<BibleEntity>, limit: Int): List<BibleEntity> {
        if (limit <= 0) return emptyList()
        val queues = entities.groupBy { it.type }
        val picked = mutableListOf<BibleEntity>()
        var round = 0
        outer@ while (picked.size < limit) {
            var any = false
            for (kind in kindOrder) {
                val queue = queues[kind] ?: continue
                if (round >= queue.size) continue
                picked += queue[round]
                any = true
                if (picked.size == limit) break@outer
            }
            if (!any) break
            round += 1
        }
        return kindOrder.flatMap { kind -> picked.filter { it.type == kind } }
    }

    /** Root at the origin, neighbours evenly on a unit circle starting at the top. */
    fun ring(root: BibleEntity, neighbours: List<BibleEntity>): List<GraphFeature.Graph.Node> {
        val nodes = mutableListOf(GraphFeature.Graph.Node(root, 0.0, 0.0, null))
        val count = neighbours.size
        neighbours.forEachIndexed { index, entity ->
            val angle = -PI / 2 + 2 * PI * index / count
            nodes += GraphFeature.Graph.Node(entity, cos(angle), sin(angle), root.id)
        }
        return nodes
    }

    /** New children on an arc behind [parentId], facing away from the node it was laid out around. */
    fun arc(parentId: EntityId, graph: GraphFeature.Graph, children: List<BibleEntity>): List<GraphFeature.Graph.Node> {
        val parent = graph.node(parentId) ?: return emptyList()
        if (children.isEmpty()) return emptyList()
        val origin = parent.parentId?.let { graph.node(it) }
        val heading = if (origin != null) atan2(parent.y - origin.y, parent.x - origin.x) else -PI / 2
        val spread = PI * 0.8
        val radius = 0.75
        val count = children.size
        return children.mapIndexed { index, entity ->
            val offset = if (count == 1) 0.0 else spread * (index.toDouble() / (count - 1) - 0.5)
            val angle = heading + offset
            GraphFeature.Graph.Node(entity, parent.x + radius * cos(angle), parent.y + radius * sin(angle), parentId)
        }
    }
}
