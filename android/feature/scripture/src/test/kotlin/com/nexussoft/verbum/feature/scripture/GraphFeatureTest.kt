package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.feature.scripture.GraphFeature.Action
import com.nexussoft.verbum.feature.scripture.GraphFeature.Content
import com.nexussoft.verbum.feature.scripture.GraphFeature.DelegateAction
import com.nexussoft.verbum.feature.scripture.GraphFeature.Presentation
import com.nexussoft.verbum.feature.scripture.GraphFeature.State
import com.nexussoft.verbum.feature.scripture.GraphFeature.reducer
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.BibleRelationship
import com.nexussoft.verbum.models.GraphSnapshot
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.RelationshipType
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphFeatureTest {
    private fun entity(kind: BibleEntityType, key: String) = BibleEntity("fixture.${kind.wireValue}.$key", kind, key.replaceFirstChar { it.uppercase() }, null)
    private fun edge(a: BibleEntity, b: BibleEntity, type: RelationshipType = RelationshipType.RELATED_TO) =
        BibleRelationship("${a.id}>${b.id}", a.id, b.id, type, 1.0, listOf("src"))

    private val david = entity(BibleEntityType.PERSON, "david")
    private val goliath = entity(BibleEntityType.PERSON, "goliath")

    /** David with 5 passages, 5 people, 3 events, 2 places, 2 themes — like the fixture. */
    private val wide: GraphSnapshot = run {
        val passages = (1..5).map { BibleEntity("passage.Ps.$it", BibleEntityType.PASSAGE, "Psalms $it", null) }
        val people = listOf("goliath", "saul", "samuel", "bathsheba", "solomon").map { entity(BibleEntityType.PERSON, it) }
        val events = listOf("anointed", "goliath-fight", "jerusalem-taken").map { entity(BibleEntityType.EVENT, it) }
        val places = listOf("bethlehem", "jerusalem").map { entity(BibleEntityType.PLACE, it) }
        val themes = listOf("forgiveness", "prayer").map { entity(BibleEntityType.THEME, it) }
        val nodes = passages + people + events + places + themes
        GraphSnapshot(david, nodes, nodes.map { edge(david, it) } + edge(people[0], events[1], RelationshipType.PARTICIPATES_IN))
    }

    private fun graph(store: TestStore<State, Action>) = (store.state.content as Content.Loaded).graph

    /** What the reducer should build from a snapshot: the same pure layout, spelled out. */
    private fun initial(snapshot: GraphSnapshot): GraphFeature.Graph {
        val neighbours = GraphLayout.balanced(snapshot.nodes, GraphFeature.VISIBLE_LIMIT)
        val ids = (neighbours.map { it.id } + snapshot.root.id).toSet()
        return GraphFeature.Graph(snapshot.root, GraphLayout.ring(snapshot.root, neighbours), snapshot.edges.filter { it.sourceId in ids && it.targetId in ids })
    }

    private fun expanded(graph: GraphFeature.Graph, id: String, snapshot: GraphSnapshot): GraphFeature.Graph {
        val room = GraphFeature.CAPACITY - graph.nodes.size
        val fresh = GraphLayout.balanced(snapshot.nodes.filter { !graph.contains(it.id) }, minOf(GraphFeature.EXPANSION_LIMIT, room))
        val nodes = graph.nodes + GraphLayout.arc(id, graph, fresh)
        val ids = nodes.map { it.id }.toSet()
        val known = graph.edges.map { it.pairKey }.toSet()
        return graph.copy(
            nodes = nodes, edges = graph.edges + snapshot.edges.filter { it.sourceId in ids && it.targetId in ids && it.pairKey !in known },
            expanded = graph.expanded + id, expanding = null, atCapacity = nodes.size >= GraphFeature.CAPACITY,
        )
    }

    @Test fun aDozenBalancedNodesOnARing() = runTest {
        val client = StubGraphClient(neighborsStub = { id, limit -> assertEquals(david.id, id); assertEquals(24, limit); wide })
        val store = TestStore(State(david.id), reducer(client))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.SnapshotLoaded(wide)) { it.copy(content = Content.Loaded(initial(wide))) }
        val graph = graph(store)
        assertEquals(13, graph.nodes.size)
        assertEquals(david, graph.nodes[0].entity); assertEquals(0.0, graph.nodes[0].x); assertEquals(0.0, graph.nodes[0].y)
        val kinds = graph.nodes.drop(1).groupingBy { it.entity.type }.eachCount()
        assertEquals(mapOf(BibleEntityType.PERSON to 3, BibleEntityType.EVENT to 3, BibleEntityType.PLACE to 2, BibleEntityType.THEME to 2, BibleEntityType.PASSAGE to 2), kinds)
        assertEquals(
            listOf(BibleEntityType.PERSON, BibleEntityType.PERSON, BibleEntityType.PERSON, BibleEntityType.EVENT, BibleEntityType.EVENT, BibleEntityType.EVENT,
                BibleEntityType.PLACE, BibleEntityType.PLACE, BibleEntityType.THEME, BibleEntityType.THEME, BibleEntityType.PASSAGE, BibleEntityType.PASSAGE),
            graph.nodes.drop(1).map { it.entity.type },
        )
        graph.nodes.drop(1).forEach { assertTrue(abs(it.x * it.x + it.y * it.y - 1) < 1e-9) }
        assertEquals(-1.0, graph.nodes[1].y)
        val ids = graph.nodes.map { it.id }.toSet()
        assertEquals(13, graph.edges.size)
        assertTrue(graph.edges.all { it.sourceId in ids && it.targetId in ids })
        store.send(Action.Started)
        store.finish()
    }

    @Test fun expansionAddsUpToSixNewNodesAroundTheNode() = runTest {
        val extra = (1..8).map { entity(BibleEntityType.PLACE, "place$it") }
        val goliathHood = GraphSnapshot(goliath, listOf(david) + extra, listOf(edge(goliath, david)) + extra.map { edge(goliath, it, RelationshipType.OCCURS_AT) })
        val client = StubGraphClient(neighborsStub = { id, _ -> if (id == david.id) wide else goliathHood })
        val store = TestStore(State(david.id), reducer(client))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.SnapshotLoaded(wide)) { it.copy(content = Content.Loaded(initial(wide))) }
        store.send(Action.ExpandTapped(goliath.id)) { it.copy(content = Content.Loaded(initial(wide).copy(expanding = goliath.id))) }
        store.receive(Action.ExpansionLoaded(goliath.id, goliathHood)) { it.copy(content = Content.Loaded(expanded(initial(wide), goliath.id, goliathHood))) }
        val graph = graph(store)
        assertEquals(setOf(goliath.id), graph.expanded)
        assertNull(graph.expanding)
        assertEquals(19, graph.nodes.size)
        val children = graph.nodes.filter { it.parentId == goliath.id }
        assertEquals(6, children.size)
        assertFalse(children.any { it.id == david.id })
        val parent = assertNotNull(graph.node(goliath.id))
        children.forEach {
            assertTrue(abs(hypot(it.x - parent.x, it.y - parent.y) - 0.75) < 1e-9)
            assertTrue(hypot(it.x, it.y) > hypot(parent.x, parent.y))
        }
        assertEquals(0, graph.edges.count { it.sourceId == goliath.id && it.targetId == david.id })
        // Six new edges to the places, plus Goliath's edge to the fight that was already visible.
        assertEquals(7, graph.edges.count { it.sourceId == goliath.id })
        store.send(Action.ExpandTapped(goliath.id))
        store.finish()
    }

    @Test fun fullGraphRefusesToExpandAndSaysSo() = runTest {
        val root = entity(BibleEntityType.PERSON, "root")
        val many = (1..30).map { entity(BibleEntityType.PERSON, "p$it") }
        val hood = GraphSnapshot(root, many, many.map { edge(root, it) })
        val store = TestStore(State(root.id), reducer(StubGraphClient(neighborsStub = { _, _ -> hood })))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.SnapshotLoaded(hood)) { it.copy(content = Content.Loaded(initial(hood))) }
        // 13 visible; the first expansion adds 6 (19), the second is clipped to 5 (24), the third is refused.
        var expected = initial(hood)
        for (key in listOf("p1", "p2")) {
            val id = "fixture.person.$key"
            store.send(Action.ExpandTapped(id)) { it.copy(content = Content.Loaded(expected.copy(expanding = id))) }
            expected = expanded(expected, id, hood)
            store.receive(Action.ExpansionLoaded(id, hood)) { it.copy(content = Content.Loaded(expected)) }
        }
        assertEquals(24, graph(store).nodes.size)
        assertTrue(graph(store).atCapacity)
        store.send(Action.ExpandTapped("fixture.person.p3"))
        store.finish()
    }

    @Test fun tapsOpenDetailOrPassageAndFocusDelegates() = runTest {
        val store = TestStore(State(david.id), reducer(StubGraphClient(neighborsStub = { _, _ -> wide })))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.SnapshotLoaded(wide)) { it.copy(content = Content.Loaded(initial(wide))) }
        store.send(Action.NodeTapped(goliath))
        store.receive(Action.Delegate(DelegateAction.OpenEntity(goliath)))
        val psalm = BibleEntity("passage.Ps.1", BibleEntityType.PASSAGE, "Psalms 1", null)
        store.send(Action.NodeTapped(psalm))
        store.receive(Action.Delegate(DelegateAction.OpenPassage(PassageReference("Ps", 1))))
        store.send(Action.FocusTapped(goliath.id))
        store.receive(Action.Delegate(DelegateAction.Focus(goliath)))
        store.send(Action.FocusTapped(david.id))
        store.send(Action.PresentationChanged(Presentation.LIST)) { it.copy(presentation = Presentation.LIST) }
        store.finish()
    }

    @Test fun failureCanRetry() = runTest {
        val store = TestStore(State(david.id), reducer(StubGraphClient(neighborsStub = { _, _ -> error("offline") })))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.SnapshotFailed) { it.copy(content = Content.Failed) }
        store.send(Action.RetryTapped) { it.copy(content = Content.Loading) }
        store.receive(Action.SnapshotFailed) { it.copy(content = Content.Failed) }
        store.finish()
    }
}
