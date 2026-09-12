import Clients
import ComposableArchitecture
import Foundation
import Models
import Testing
@testable import Features

private func entity(_ kind: BibleEntityType, _ key: String) -> BibleEntity {
    BibleEntity(id: "fixture.\(kind.rawValue).\(key)", type: kind, name: key.capitalized, summary: nil)
}

private func edge(_ a: BibleEntity, _ b: BibleEntity, _ type: RelationshipType = .relatedTo) -> BibleRelationship {
    BibleRelationship(id: "\(a.id)>\(b.id)", sourceId: a.id, targetId: b.id, type: type, confidence: 1, sourceReferenceIds: ["src"])
}

/// What the reducer should build from a snapshot: the same pure layout, spelled out.
private func initial(_ snapshot: GraphSnapshot) -> GraphFeature.Graph {
    let neighbours = GraphLayout.balanced(snapshot.nodes, limit: GraphFeature.visibleLimit)
    let ids = Set(neighbours.map(\.id) + [snapshot.root.id])
    return GraphFeature.Graph(root: snapshot.root, nodes: GraphLayout.ring(root: snapshot.root, neighbours: neighbours), edges: snapshot.edges.filter { ids.contains($0.sourceId) && ids.contains($0.targetId) })
}

private func expanded(_ graph: GraphFeature.Graph, _ id: EntityID, _ snapshot: GraphSnapshot) -> GraphFeature.Graph {
    var graph = graph
    let room = GraphFeature.capacity - graph.nodes.count
    let fresh = GraphLayout.balanced(snapshot.nodes.filter { !graph.contains($0.id) }, limit: min(GraphFeature.expansionLimit, room))
    graph.nodes += GraphLayout.arc(around: id, in: graph, children: fresh)
    let ids = Set(graph.nodes.map(\.id))
    let known = Set(graph.edges.map(\.pairKey))
    graph.edges += snapshot.edges.filter { ids.contains($0.sourceId) && ids.contains($0.targetId) && !known.contains($0.pairKey) }
    graph.expanded.insert(id)
    graph.expanding = nil
    graph.atCapacity = graph.nodes.count >= GraphFeature.capacity
    return graph
}

@MainActor
@Suite struct GraphFeatureTests {
    let david = entity(.person, "david")

    /// David with 5 passages, 5 people, 3 events, 2 places, 2 themes — like the fixture.
    var wideNeighbourhood: GraphSnapshot {
        let passages = (1...5).map { BibleEntity(id: "passage.Ps.\($0)", type: .passage, name: "Psalms \($0)", summary: nil) }
        let people = ["goliath", "saul", "samuel", "bathsheba", "solomon"].map { entity(.person, $0) }
        let events = ["anointed", "goliath-fight", "jerusalem-taken"].map { entity(.event, $0) }
        let places = ["bethlehem", "jerusalem"].map { entity(.place, $0) }
        let themes = ["forgiveness", "prayer"].map { entity(.theme, $0) }
        let nodes = passages + people + events + places + themes
        return GraphSnapshot(root: david, nodes: nodes, edges: nodes.map { edge(david, $0) } + [edge(people[0], events[1], .participatesIn)])
    }

    @Test func aDozenBalancedNodesOnARing() async {
        let store = TestStore(initialState: GraphFeature.State(rootID: david.id)) {
            GraphFeature()
        } withDependencies: {
            $0.graphClient.neighbors = { [wideNeighbourhood] id, limit in
                #expect(id == "fixture.person.david")
                #expect(limit == 24)
                return wideNeighbourhood
            }
        }
        await store.send(.task) { $0.content = .loading }
        await store.receive(\.snapshotResponse.success) { [wideNeighbourhood] in $0.content = .loaded(initial(wideNeighbourhood)) }
        guard case .loaded(let graph) = store.state.content else { Issue.record("not loaded"); return }
        #expect(graph.nodes.count == 13)
        #expect(graph.nodes[0].entity == david && graph.nodes[0].x == 0 && graph.nodes[0].y == 0)
        // Balanced: no kind hogs the ring.
        let kinds = Dictionary(grouping: graph.nodes.dropFirst(), by: \.entity.type).mapValues(\.count)
        #expect(kinds[.person] == 3 && kinds[.event] == 3 && kinds[.place] == 2 && kinds[.theme] == 2 && kinds[.passage] == 2)
        // Like sits with like: the ring is grouped in kind order.
        #expect(graph.nodes.dropFirst().map(\.entity.type) == [.person, .person, .person, .event, .event, .event, .place, .place, .theme, .theme, .passage, .passage])
        // On the unit circle, first one at the top.
        for node in graph.nodes.dropFirst() { #expect(abs(node.x * node.x + node.y * node.y - 1) < 1e-9) }
        #expect(graph.nodes[1].y == -1)
        // Only edges among visible nodes survive.
        let ids = Set(graph.nodes.map(\.id))
        #expect(graph.edges.count == 13)
        #expect(graph.edges.allSatisfy { ids.contains($0.sourceId) && ids.contains($0.targetId) })
        // Idempotent.
        await store.send(.task)
    }

    @Test func expansionAddsUpToSixNewNodesAroundTheNode() async throws {
        let goliath = entity(.person, "goliath")
        let extra = (1...8).map { entity(.place, "place\($0)") }
        let store = TestStore(initialState: GraphFeature.State(rootID: david.id)) {
            GraphFeature()
        } withDependencies: {
            $0.graphClient.neighbors = { [wideNeighbourhood, david] id, _ in
                if id == david.id { return wideNeighbourhood }
                // Goliath's neighbourhood: David (already shown) plus eight places.
                return GraphSnapshot(root: goliath, nodes: [david] + extra, edges: [edge(goliath, david)] + extra.map { edge(goliath, $0, .occursAt) })
            }
        }
        let goliathHood = GraphSnapshot(root: goliath, nodes: [david] + extra, edges: [edge(goliath, david)] + extra.map { edge(goliath, $0, .occursAt) })
        await store.send(.task) { $0.content = .loading }
        await store.receive(\.snapshotResponse.success) { [wideNeighbourhood] in $0.content = .loaded(initial(wideNeighbourhood)) }
        await store.send(.expandTapped(goliath.id)) { [wideNeighbourhood] in
            var graph = initial(wideNeighbourhood)
            graph.expanding = goliath.id
            $0.content = .loaded(graph)
        }
        await store.receive(\.expansionResponse) { [wideNeighbourhood] in $0.content = .loaded(expanded(initial(wideNeighbourhood), goliath.id, goliathHood)) }
        guard case .loaded(let graph) = store.state.content else { Issue.record("not loaded"); return }
        #expect(graph.expanded == [goliath.id])
        #expect(graph.expanding == nil)
        #expect(graph.nodes.count == 13 + 6)
        let children = graph.nodes.filter { $0.parentID == goliath.id }
        #expect(children.count == 6)
        #expect(!children.contains { $0.id == david.id })
        // Children sit 0.75 from Goliath, on the far side from the root.
        let parent = try #require(graph.node(goliath.id))
        for child in children {
            #expect(abs(hypot(child.x - parent.x, child.y - parent.y) - 0.75) < 1e-9)
            #expect(hypot(child.x, child.y) > hypot(parent.x, parent.y))
        }
        // Edges to the new children came along; David–Goliath was not duplicated.
        #expect(graph.edges.filter { $0.sourceId == goliath.id && $0.targetId == david.id }.count == 0)
        // Six new edges to the places, plus Goliath's edge to the fight that was already visible.
        #expect(graph.edges.filter { $0.sourceId == goliath.id }.count == 7)
        // Expanding again is a no-op.
        await store.send(.expandTapped(goliath.id))
    }

    @Test func fullGraphRefusesToExpandAndSaysSo() async {
        let many = (1...30).map { entity(.person, "p\($0)") }
        let root = entity(.person, "root")
        let hood = GraphSnapshot(root: root, nodes: many, edges: many.map { edge(root, $0) })
        let store = TestStore(initialState: GraphFeature.State(rootID: root.id)) {
            GraphFeature()
        } withDependencies: {
            $0.graphClient.neighbors = { _, _ in hood }
        }
        await store.send(.task) { $0.content = .loading }
        await store.receive(\.snapshotResponse.success) { $0.content = .loaded(initial(hood)) }
        // 13 visible; the first expansion adds 6 (19), the second is clipped to 5 (24), the third is refused.
        var expected = initial(hood)
        for key in ["p1", "p2"] {
            let id = "fixture.person.\(key)"
            await store.send(.expandTapped(id)) { [expected] in
                var graph = expected
                graph.expanding = id
                $0.content = .loaded(graph)
            }
            expected = expanded(expected, id, hood)
            await store.receive(\.expansionResponse) { [expected] in $0.content = .loaded(expected) }
        }
        guard case .loaded(let graph) = store.state.content else { return }
        #expect(graph.nodes.count == 24)
        #expect(graph.atCapacity)
        await store.send(.expandTapped("fixture.person.p3"))
    }

    @Test func tapsOpenDetailOrPassageAndFocusDelegates() async {
        let store = TestStore(initialState: GraphFeature.State(rootID: david.id)) {
            GraphFeature()
        } withDependencies: {
            $0.graphClient.neighbors = { [wideNeighbourhood] _, _ in wideNeighbourhood }
        }
        await store.send(.task) { $0.content = .loading }
        await store.receive(\.snapshotResponse.success) { [wideNeighbourhood] in $0.content = .loaded(initial(wideNeighbourhood)) }
        let goliath = entity(.person, "goliath")
        await store.send(.nodeTapped(goliath))
        await store.receive(\.delegate.openEntity, goliath)
        let psalm = BibleEntity(id: "passage.Ps.1", type: .passage, name: "Psalms 1", summary: nil)
        await store.send(.nodeTapped(psalm))
        await store.receive(\.delegate.openPassage, PassageReference(bookId: "Ps", chapter: 1))
        await store.send(.focusTapped(goliath.id))
        await store.receive(\.delegate.focus, goliath)
        await store.send(.focusTapped(david.id)) // already the root
        await store.send(.presentationChanged(.list)) { $0.presentation = .list }
    }

    @Test func failureCanRetry() async {
        let store = TestStore(initialState: GraphFeature.State(rootID: david.id)) {
            GraphFeature()
        } withDependencies: {
            $0.graphClient.neighbors = { _, _ in throw GraphClientError.unknownEntity("x") }
        }
        await store.send(.task) { $0.content = .loading }
        await store.receive(\.snapshotResponse.failure) { $0.content = .failed }
        await store.send(.retryTapped) { $0.content = .loading }
        await store.receive(\.snapshotResponse.failure) { $0.content = .failed }
    }
}
