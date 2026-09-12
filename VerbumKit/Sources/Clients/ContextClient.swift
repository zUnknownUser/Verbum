import ComposableArchitecture
import Models

@DependencyClient
public struct ContextClient: Sendable {
    public var chapter: @Sendable (_ reference: PassageReference) async throws -> PassageContext?
}

extension ContextClient: DependencyKey {
    public static let liveValue = Self.fixtures
    public static let previewValue = Self.fixtures
}

extension DependencyValues {
    public var contextClient: ContextClient {
        get { self[ContextClient.self] }
        set { self[ContextClient.self] = newValue }
    }
}

extension ContextClient {
    /// Reuses only existing, sourced fixture edges. Related passages share a
    /// connected entity; they are not asserted to be quotations or parallels.
    public static let fixtures = Self(chapter: { reference in
        let chapter = PassageReference(bookId: reference.bookId, chapter: reference.chapter)
        let id = EntityFixtureData.passageNode(chapter).id
        let edges = EntityFixtureData.relationships.filter { $0.sourceId == id || $0.targetId == id }
        // Detail entries also explicitly cite chapters (e.g. Valley of Elah).
        // Do not recursively expand neighbours: that would imply unsupported
        // chapter associations and grow without a useful bound.
        let details = EntityFixtureData.details.values.filter { detail in
            detail.keyPassages.contains { $0.bookId == chapter.bookId && $0.chapter == chapter.chapter }
        }
        let ids = Set(edges.map { $0.sourceId == id ? $0.targetId : $0.sourceId })
            .union(details.map { $0.entity.id })
        guard !ids.isEmpty else { return nil }
        let entities = EntityFixtureData.entities.filter { ids.contains($0.id) && $0.type != .passage }
        var seen = Set<PassageReference>()
        var related: [PassageReference] = []
        var sourceIDs = Set(edges.flatMap(\.sourceReferenceIds))
        sourceIDs.formUnion(details.flatMap { $0.sources.map(\.id) })
        for edge in EntityFixtureData.relationships {
            let other: String
            if ids.contains(edge.sourceId) { other = edge.targetId }
            else if ids.contains(edge.targetId) { other = edge.sourceId }
            else { continue }
            guard let node = EntityFixtureData.entity(other),
                  let passage = EntityFixtureData.passageReference(for: node),
                  passage != chapter else { continue }
            if seen.insert(passage).inserted { related.append(passage) }
            sourceIDs.formUnion(edge.sourceReferenceIds)
        }
        return PassageContext(
            reference: chapter,
            entities: entities,
            relatedPassages: related,
            sources: EntityFixtureData.sources.filter { sourceIDs.contains($0.id) },
            isFixture: true
        )
    })
}
