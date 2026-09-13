import ComposableArchitecture
import Core
import Models

// The `.live` value of every content client: `VerbumAPI` behind the interface
// the features already use, with the API's failures mapped to each client's
// own errors (spec §37: features never see a backend, §52: typed states).

extension GraphClient {
    public static func live(api: VerbumAPI, language: BookLanguage = .current) -> GraphClient {
        GraphClient(
            entity: { id in try await mapUnknown(id) { try await api.entityDetail(id).entity } },
            neighbors: { id, limit in try await mapUnknown(id) { try await api.graph(id, limit: limit) } },
            detail: { id in try await mapUnknown(id) { try await api.entityDetail(id) } },
            entities: { type in
                guard type != .passage else { return [] }
                return try await api.entities(of: type, language: language)
            }
        )
    }

    /// `404 unknown_entity` is the client's own `unknownEntity`; anything else passes through.
    private static func mapUnknown<T>(_ id: EntityID, _ body: () async throws -> T) async throws -> T {
        do {
            return try await body()
        } catch VerbumAPIError.problem(.unknownEntity, _) {
            throw GraphClientError.unknownEntity(id)
        }
    }
}

extension SearchClient {
    /// The server ranks Scripture hits and entities (§27–28); a reference the
    /// device can parse still wins outright and books are matched locally,
    /// so a typed `Jn 3:16` opens instantly and the two rankings agree.
    public static func live(api: VerbumAPI, language: BookLanguage = .current) -> SearchClient {
        SearchClient(search: { query in
            let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { return .empty(query) }
            let local = SearchResponse.local(trimmed, language: language)
            let remote = try await api.search(trimmed, language: language)
            var passages = local.passages
            for passage in remote.passages where !passages.contains(passage) { passages.append(passage) }
            return SearchResponse(
                query: query,
                passages: passages,
                books: local.passages.isEmpty ? (local.books + remote.books.filter { !local.books.contains($0) }) : [],
                entities: remote.entities
            )
        })
    }
}

extension SearchResponse {
    /// What this device knows without a server: the parsed reference and the
    /// books whose name the query starts (§28). Never throws.
    public static func local(_ query: String, language: BookLanguage) -> SearchResponse {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return .empty(query) }
        let reference = try? PassageReferenceParser.parse(trimmed, language: language)
        // A reference is the answer; listing its book beside it is noise.
        return SearchResponse(
            query: query,
            passages: reference.map { [$0] } ?? [],
            books: reference == nil ? BookMatcher.books(matching: trimmed) : [],
            entities: []
        )
    }
}

extension ContextClient {
    public static func live(api: VerbumAPI) -> ContextClient {
        ContextClient(chapter: { reference in try await api.context(reference) })
    }
}

extension TimelineClient {
    public static func live(api: VerbumAPI) -> TimelineClient {
        TimelineClient(
            events: { try await api.timeline().events },
            eventsFor: { id in try await api.timeline(entity: id).events }
        )
    }
}
