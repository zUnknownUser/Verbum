import ComposableArchitecture
import Models

/// Entities and their neighbourhoods (spec §38, §44). `entity` and `neighbors`
/// are the spec's endpoints; `detail` adds the page-level facts of spec §9.
@DependencyClient
public struct GraphClient: Sendable {
    public var entity: @Sendable (_ id: EntityID) async throws -> BibleEntity
    public var neighbors: @Sendable (_ id: EntityID, _ limit: Int) async throws -> GraphSnapshot
    public var detail: @Sendable (_ id: EntityID) async throws -> EntityDetail
    /// Every entity of one kind, for the Explore lists (spec §7). Passage nodes are never listed.
    public var entities: @Sendable (_ type: BibleEntityType) async throws -> [BibleEntity]
}

public enum GraphClientError: Error, Equatable, Sendable {
    case unknownEntity(EntityID)
}

extension GraphClient: DependencyKey {
    /// The backend (Task 11); fixtures remain the preview and test double.
    public static let liveValue = GraphClient.live(api: .shared)
    public static let previewValue = GraphClient.fixtures
}

extension DependencyValues {
    public var graphClient: GraphClient {
        get { self[GraphClient.self] }
        set { self[GraphClient.self] = newValue }
    }
}

extension GraphClient {
    public static let fixtures = GraphClient(
        entity: { id in
            guard let entity = EntityFixtureData.entity(id) else { throw GraphClientError.unknownEntity(id) }
            return entity
        },
        neighbors: { id, limit in
            guard let root = EntityFixtureData.entity(id) else { throw GraphClientError.unknownEntity(id) }
            // Undirected one-hop neighbourhood, in fixture order, capped.
            let edges = EntityFixtureData.relationships.filter { $0.sourceId == id || $0.targetId == id }
            var seen = Set<EntityID>()
            var nodes: [BibleEntity] = []
            for edge in edges {
                let otherId = edge.sourceId == id ? edge.targetId : edge.sourceId
                guard !seen.contains(otherId), let other = EntityFixtureData.entity(otherId) else { continue }
                seen.insert(otherId)
                nodes.append(other)
                if nodes.count == limit { break }
            }
            let kept = edges.filter { seen.contains($0.sourceId) || seen.contains($0.targetId) }
            return GraphSnapshot(root: root, nodes: nodes, edges: kept)
        },
        detail: { id in
            guard let detail = EntityFixtureData.details[id] else {
                guard let entity = EntityFixtureData.entity(id) else { throw GraphClientError.unknownEntity(id) }
                return EntityDetail(entity: entity, sources: [EntityFixtureData.editorialSource])
            }
            return detail
        },
        entities: { type in
            guard type != .passage else { return [] }
            return EntityFixtureData.entities.filter { $0.type == type }.sorted { $0.name < $1.name }
        }
    )
}
