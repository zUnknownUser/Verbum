/// What a search returns (spec §27, §38). Groups are kept separate so the UI
/// can render them as `Passages · Books · People · Places · Themes` and rank
/// direct reference matches first (spec §28).
public struct SearchResponse: Equatable, Codable, Sendable {
    public let query: String
    /// A passage the query names directly, e.g. `Jn 3:16`.
    public let passages: [PassageReference]
    /// Books whose name or abbreviation the query starts.
    public let books: [BibleBook]
    /// People, places, themes, events. Grouped by `type` in the UI.
    public let entities: [BibleEntity]

    public init(query: String, passages: [PassageReference], books: [BibleBook], entities: [BibleEntity]) {
        self.query = query
        self.passages = passages
        self.books = books
        self.entities = entities
    }

    public static func empty(_ query: String) -> SearchResponse {
        SearchResponse(query: query, passages: [], books: [], entities: [])
    }

    public var isEmpty: Bool { passages.isEmpty && books.isEmpty && entities.isEmpty }

    public func entities(of type: BibleEntityType) -> [BibleEntity] {
        entities.filter { $0.type == type }
    }
}
