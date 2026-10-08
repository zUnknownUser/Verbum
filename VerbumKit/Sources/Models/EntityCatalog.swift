import Foundation

public struct EntityCatalogRequest: Equatable, Sendable {
    public var type: BibleEntityType
    public var query: String
    public var letter: String
    public var offset: Int
    public var category: String
    public var limit: Int
    public init(type: BibleEntityType, query: String = "", letter: String = "", offset: Int = 0, limit: Int = 30, category: String = "") {
        self.type = type; self.query = query; self.letter = letter; self.offset = offset; self.limit = limit; self.category = category
    }
}

public struct EntityCatalogPage: Codable, Equatable, Sendable {
    public var entities: [BibleEntity]
    public var letters: [String]
    public var nextOffset: Int?
    public init(entities: [BibleEntity], letters: [String], nextOffset: Int? = nil) {
        self.entities = entities; self.letters = letters; self.nextOffset = nextOffset
    }
}

public enum EntityCatalog {
    private static let fixtureCategories: [String: [String]] = [
        "fixture.theme.faith": ["with-god", "foundations"],
        "fixture.theme.prayer": ["with-god", "emotions"],
        "fixture.theme.love": ["relationships", "with-god"],
        "fixture.theme.forgiveness": ["relationships", "foundations"],
        "fixture.theme.anxiety": ["emotions"],
        "fixture.theme.suffering": ["emotions"],
        "fixture.theme.money": ["daily-life"],
        "fixture.theme.justice": ["daily-life", "community"],
        "fixture.theme.wisdom": ["character", "daily-life"],
        "fixture.theme.grace": ["foundations"]
    ]

    public static func normalized(_ name: String) -> String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "en_US_POSIX"))
    }
    public static func letter(_ name: String) -> String {
        guard let first = normalized(name).uppercased().first, ("A"..."Z").contains(String(first)) else { return "#" }
        return String(first)
    }
    /// Fixture-only paging; live clients always request a bounded page from the API.
    public static func page(_ entities: [BibleEntity], request: EntityCatalogRequest) -> EntityCatalogPage {
        let matching = entities.filter { (request.category.isEmpty || fixtureCategories[$0.id, default: []].contains(request.category)) && $0.type == request.type && (request.query.isEmpty || normalized($0.name).contains(normalized(request.query))) }
        let letters = Array(Set(matching.map { letter($0.name) })).sorted()
        let selected = matching.filter { request.letter.isEmpty || letter($0.name) == request.letter }.sorted {
            let left = letter($0.name), right = letter($1.name)
            if left != right { return left < right }
            let a = normalized($0.name), b = normalized($1.name)
            return a == b ? $0.id < $1.id : a < b
        }
        let page = Array(selected.dropFirst(request.offset).prefix(request.limit))
        let next = request.offset + page.count
        return .init(entities: page, letters: letters, nextOffset: next < selected.count ? next : nil)
    }
}
