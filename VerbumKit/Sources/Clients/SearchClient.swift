import ComposableArchitecture
import Core
import Models

/// Search across references, books and entities (spec §27, §38).
@DependencyClient
public struct SearchClient: Sendable {
    public var search: @Sendable (_ query: String) async throws -> SearchResponse
}

extension SearchClient: DependencyKey {
    /// The backend (Task 11); fixtures remain the preview and test double.
    public static let liveValue = SearchClient.live(api: .shared)
    public static let previewValue = SearchClient.fixtures
}

extension DependencyValues {
    public var searchClient: SearchClient {
        get { self[SearchClient.self] }
        set { self[SearchClient.self] = newValue }
    }
}

extension SearchClient {
    /// Deterministic in-memory search: a parsed reference wins outright (spec §28);
    /// otherwise book matches, then fixture entities whose name starts with the
    /// query, or any word of it. Never throws.
    public static let fixtures = SearchClient.fixtures(language: .current)

    /// Same fixture, with the naming language pinned (tests, previews).
    public static func fixtures(language: BookLanguage) -> SearchClient {
        SearchClient(
        search: { query in
            let local = SearchResponse.local(query, language: language)
            let key = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            guard !key.isEmpty else { return local }
            let entities = EntityFixtureData.entities.filter { entity in
                guard entity.type != .passage else { return false }
                let name = entity.name.lowercased()
                return name.hasPrefix(key) || name.split(separator: " ").contains { $0.hasPrefix(key) }
            }
            return SearchResponse(query: query, passages: local.passages, books: local.books, entities: entities)
        }
        )
    }
}
