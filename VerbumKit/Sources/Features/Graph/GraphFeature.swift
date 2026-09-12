import Clients
import ComposableArchitecture
import Foundation
import Models

/// The Bible Graph around one entity (spec §8, §21.4, §44): one degree by
/// default, at most a dozen visible nodes, explicit incremental expansion,
/// and a list representation the graph never replaces (§43).
///
/// Layout is deterministic rings (`GraphLayout`), not a force simulation —
/// spec §60 Task 9 says not to build that complexity prematurely, and a stable
/// picture is easier to read than one that keeps settling.
@Reducer
public struct GraphFeature {
    @ObservableState
    public struct State: Equatable {
        public let rootID: EntityID
        public var content: Content = .idle
        public var presentation: Presentation = .graph

        public init(rootID: EntityID, presentation: Presentation = .graph) {
            self.rootID = rootID
            self.presentation = presentation
        }
    }

    public enum Presentation: Equatable, Sendable {
        case graph, list
    }

    public enum Content: Equatable, Sendable {
        case idle
        case loading
        case loaded(Graph)
        case failed
    }

    /// What is on screen: nodes with their layout, the edges among them, and
    /// which nodes have been expanded.
    public struct Graph: Equatable, Sendable {
        public var root: BibleEntity
        public var nodes: [Node]
        public var edges: [BibleRelationship]
        public var expanded: Set<EntityID> = []
        /// The node whose neighbourhood is being fetched, if any.
        public var expanding: EntityID?
        /// Set when an expansion was refused because the graph is full (§8.1).
        public var atCapacity = false

        public struct Node: Equatable, Sendable, Identifiable {
            public let entity: BibleEntity
            /// Unit coordinates: the first ring has radius 1 around the root at the origin.
            public var x: Double
            public var y: Double
            /// The node this one was laid out around (nil for the root).
            public let parentID: EntityID?

            public var id: EntityID { entity.id }
        }

        public func node(_ id: EntityID) -> Node? { nodes.first { $0.id == id } }
        public func contains(_ id: EntityID) -> Bool { nodes.contains { $0.id == id } }

        /// Edges touching `id`, with the entity on the other end.
        public func connections(of id: EntityID) -> [(edge: BibleRelationship, other: BibleEntity)] {
            edges.compactMap { edge in
                let otherID = edge.sourceId == id ? edge.targetId : edge.targetId == id ? edge.sourceId : nil
                guard let otherID, let other = node(otherID)?.entity else { return nil }
                return (edge, other)
            }
        }

        /// Sources cited by every visible edge, without duplicates (§33).
        public var sourceIDs: [String] {
            var seen = Set<String>()
            return edges.flatMap(\.sourceReferenceIds).filter { seen.insert($0).inserted }
        }
    }

    public enum Action: Equatable {
        case task
        case retryTapped
        case snapshotResponse(Result<GraphSnapshot, Failure>)
        case nodeTapped(BibleEntity)
        case expandTapped(EntityID)
        case expansionResponse(EntityID, Result<GraphSnapshot, Failure>)
        case focusTapped(EntityID)
        case presentationChanged(Presentation)
        case delegate(Delegate)

        @CasePathable
        public enum Delegate: Equatable {
            case openEntity(BibleEntity)
            case openPassage(PassageReference)
            /// Re-root the graph on another entity (a new graph page).
            case focus(BibleEntity)
        }
    }

    public struct Failure: Error, Equatable, Sendable {
        public init() {}
    }

    /// Spec §8.1: ~8–12 visible nodes at depth one.
    public static let visibleLimit = 12
    /// Asked of the client, so the visible dozen can be balanced across kinds.
    static let fetchLimit = 24
    /// New nodes one expansion may add.
    public static let expansionLimit = 6
    /// Beyond this the picture stops being readable; the user refocuses instead.
    public static let capacity = 24

    @Dependency(\.graphClient) var graphClient

    public init() {}

    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .task:
                guard state.content == .idle else { return .none }
                return load(&state)

            case .retryTapped:
                return load(&state)

            case .snapshotResponse(.success(let snapshot)):
                let neighbours = GraphLayout.balanced(snapshot.nodes, limit: Self.visibleLimit)
                let ids = Set(neighbours.map(\.id) + [snapshot.root.id])
                state.content = .loaded(Graph(
                    root: snapshot.root,
                    nodes: GraphLayout.ring(root: snapshot.root, neighbours: neighbours),
                    edges: snapshot.edges.filter { ids.contains($0.sourceId) && ids.contains($0.targetId) }
                ))
                return .none

            case .snapshotResponse(.failure):
                state.content = .failed
                return .none

            case .nodeTapped(let entity):
                if let reference = EntityFixturePassages.reference(for: entity) {
                    return .send(.delegate(.openPassage(reference)))
                }
                return .send(.delegate(.openEntity(entity)))

            case .expandTapped(let id):
                guard case .loaded(var graph) = state.content, graph.contains(id), !graph.expanded.contains(id), graph.expanding == nil else { return .none }
                guard graph.nodes.count < Self.capacity else {
                    graph.atCapacity = true
                    state.content = .loaded(graph)
                    return .none
                }
                graph.expanding = id
                state.content = .loaded(graph)
                return .run { [graphClient] send in
                    do {
                        await send(.expansionResponse(id, .success(try await graphClient.neighbors(id: id, limit: Self.fetchLimit))))
                    } catch {
                        await send(.expansionResponse(id, .failure(Failure())))
                    }
                }
                .cancellable(id: CancelID.expand, cancelInFlight: true)

            case .expansionResponse(let id, .success(let snapshot)):
                guard case .loaded(var graph) = state.content, graph.expanding == id else { return .none }
                graph.expanding = nil
                graph.expanded.insert(id)
                let room = Self.capacity - graph.nodes.count
                let fresh = GraphLayout.balanced(snapshot.nodes.filter { !graph.contains($0.id) }, limit: min(Self.expansionLimit, room))
                graph.nodes += GraphLayout.arc(around: id, in: graph, children: fresh)
                let ids = Set(graph.nodes.map(\.id))
                let known = Set(graph.edges.map(\.pairKey))
                graph.edges += snapshot.edges.filter { ids.contains($0.sourceId) && ids.contains($0.targetId) && !known.contains($0.pairKey) }
                graph.atCapacity = graph.nodes.count >= Self.capacity
                state.content = .loaded(graph)
                return .none

            case .expansionResponse(let id, .failure):
                guard case .loaded(var graph) = state.content, graph.expanding == id else { return .none }
                graph.expanding = nil
                state.content = .loaded(graph)
                return .none

            case .focusTapped(let id):
                guard case .loaded(let graph) = state.content, let entity = graph.node(id)?.entity, id != graph.root.id else { return .none }
                return .send(.delegate(.focus(entity)))

            case .presentationChanged(let presentation):
                state.presentation = presentation
                return .none

            case .delegate:
                return .none
            }
        }
    }

    private func load(_ state: inout State) -> Effect<Action> {
        state.content = .loading
        return .run { [graphClient, id = state.rootID] send in
            do {
                await send(.snapshotResponse(.success(try await graphClient.neighbors(id: id, limit: Self.fetchLimit))))
            } catch {
                await send(.snapshotResponse(.failure(Failure())))
            }
        }
        .cancellable(id: CancelID.load, cancelInFlight: true)
    }

    private enum CancelID { case load, expand }
}

extension BibleRelationship {
    /// The same connection seen from either end is one line in the picture.
    var pairKey: String { [sourceId, targetId].sorted().joined(separator: "|") + "|" + type.rawValue }
}

/// Deterministic placement. Same arithmetic on Android so both graphs match.
public enum GraphLayout {
    /// The order kinds are laid out in, so like sits with like around the ring.
    public static let kindOrder: [BibleEntityType] = [.person, .event, .place, .theme, .passage, .book, .prophecy, .originalTerm, .historicalPeriod]

    /// Up to `limit` entities, taken round-robin across kinds in `kindOrder`,
    /// so a person with many passages still shows their places and events.
    public static func balanced(_ entities: [BibleEntity], limit: Int) -> [BibleEntity] {
        guard limit > 0 else { return [] }
        var queues: [BibleEntityType: [BibleEntity]] = [:]
        for entity in entities { queues[entity.type, default: []].append(entity) }
        var picked: [BibleEntity] = []
        var round = 0
        while picked.count < limit {
            var any = false
            for kind in kindOrder {
                guard let queue = queues[kind], round < queue.count else { continue }
                picked.append(queue[round])
                any = true
                if picked.count == limit { break }
            }
            if !any { break }
            round += 1
        }
        // Group by kind for the ring, keeping each kind's own order.
        return kindOrder.flatMap { kind in picked.filter { $0.type == kind } }
    }

    /// Root at the origin, neighbours evenly on a unit circle starting at the top.
    public static func ring(root: BibleEntity, neighbours: [BibleEntity]) -> [GraphFeature.Graph.Node] {
        var nodes = [GraphFeature.Graph.Node(entity: root, x: 0, y: 0, parentID: nil)]
        let count = neighbours.count
        for (index, entity) in neighbours.enumerated() {
            let angle = -Double.pi / 2 + 2 * Double.pi * Double(index) / Double(count)
            nodes.append(.init(entity: entity, x: cos(angle), y: sin(angle), parentID: root.id))
        }
        return nodes
    }

    /// New children on an arc behind `parentID`, facing away from the node it
    /// was laid out around, at a shorter radius so the picture stays compact.
    public static func arc(around parentID: EntityID, in graph: GraphFeature.Graph, children: [BibleEntity]) -> [GraphFeature.Graph.Node] {
        guard let parent = graph.node(parentID), !children.isEmpty else { return [] }
        let origin = parent.parentID.flatMap { graph.node($0) }
        let heading: Double = origin.map { atan2(parent.y - $0.y, parent.x - $0.x) } ?? -Double.pi / 2
        let spread = Double.pi * 0.8
        let radius = 0.75
        let count = children.count
        return children.enumerated().map { index, entity in
            let offset = count == 1 ? 0 : spread * (Double(index) / Double(count - 1) - 0.5)
            let angle = heading + offset
            return .init(entity: entity, x: parent.x + radius * cos(angle), y: parent.y + radius * sin(angle), parentID: parentID)
        }
    }
}
