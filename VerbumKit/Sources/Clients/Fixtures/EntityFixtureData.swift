import Models

// FIXTURE — hand-curated. Labelled as such; nothing here is production content.
//
// The first entity coverage the spec suggests (§68) plus the few people, events
// and passages the golden path needs (§75: David → Goliath → 1 Samuel 17).
// Summaries are short and non-interpretive (§3.5); dates are hedged. Every
// relationship carries a source (§33). Real content arrives with Task 11.
enum EntityFixtureData {
    // MARK: Sources

    static let scriptureSource = SourceReference(
        id: "fixture.source.web",
        citation: "World English Bible (public domain) — the passages cited",
        url: "https://worldenglish.bible"
    )
    static let editorialSource = SourceReference(
        id: "fixture.source.editorial",
        citation: "Verbum editorial notes (fixture; to be replaced by sourced content)",
        url: nil
    )
    static let sources: [SourceReference] = [scriptureSource, editorialSource]

    // MARK: Entities

    private static func person(_ key: String, _ name: String, _ summary: String) -> BibleEntity { .init(id: "fixture.person.\(key)", type: .person, name: name, summary: summary) }
    private static func place(_ key: String, _ name: String, _ summary: String) -> BibleEntity { .init(id: "fixture.place.\(key)", type: .place, name: name, summary: summary) }
    private static func theme(_ key: String, _ name: String, _ summary: String) -> BibleEntity { .init(id: "fixture.theme.\(key)", type: .theme, name: name, summary: summary) }
    private static func event(_ key: String, _ name: String, _ summary: String) -> BibleEntity { .init(id: "fixture.event.\(key)", type: .event, name: name, summary: summary) }

    /// Passages appear in the graph as nodes so "key passages" are edges like any other.
    static func passageNode(_ reference: PassageReference) -> BibleEntity {
        BibleEntity(id: "passage.\(reference.bookId).\(reference.chapter)", type: .passage, name: reference.formatted(for: .english), summary: nil)
    }

    static func passageReference(for node: BibleEntity) -> PassageReference? {
        guard node.type == .passage else { return nil }
        let parts = node.id.split(separator: ".")
        guard parts.count == 3, parts[0] == "passage", let chapter = Int(parts[2]) else { return nil }
        return PassageReference(bookId: String(parts[1]), chapter: chapter)
    }

    static let entities: [BibleEntity] = [
        // People (§68) + golden-path people
        person("jesus", "Jesus", "Central figure of the New Testament."),
        person("paul", "Paul", "Apostle; author of many New Testament letters."),
        person("peter", "Peter", "Disciple of Jesus; leader among the apostles."),
        person("abraham", "Abraham", "Patriarch; the covenant with God begins with him."),
        person("moses", "Moses", "Led Israel out of Egypt; received the Law."),
        person("david", "David", "King of Israel; associated with many Psalms."),
        person("solomon", "Solomon", "King of Israel; built the first temple."),
        person("saul", "Saul", "First king of Israel."),
        person("mary", "Mary", "Mother of Jesus."),
        person("john", "John", "Disciple of Jesus; traditionally linked to the Gospel of John."),
        person("goliath", "Goliath", "Philistine champion from Gath, defeated by David."),
        person("samuel", "Samuel", "Prophet and judge; anointed Saul and David."),
        person("bathsheba", "Bathsheba", "Wife of Uriah, then of David; mother of Solomon."),
        // Places (§68)
        place("jerusalem", "Jerusalem", "City of David; site of the temple."),
        place("bethlehem", "Bethlehem", "Birthplace of David and of Jesus."),
        place("nazareth", "Nazareth", "Town in Galilee where Jesus grew up."),
        place("galilee", "Galilee", "Northern region; setting of much of Jesus' ministry."),
        place("rome", "Rome", "Capital of the empire; destination of Paul's letter to the Romans."),
        place("corinth", "Corinth", "Greek city; recipient of two of Paul's letters."),
        place("ephesus", "Ephesus", "City in Asia Minor; recipient of Paul's letter to the Ephesians."),
        place("babylon", "Babylon", "Empire and city; place of Judah's exile."),
        place("egypt", "Egypt", "Where Israel was enslaved before the Exodus."),
        place("elah", "Valley of Elah", "Where Israel and the Philistines faced each other in 1 Samuel 17."),
        // Themes (§68)
        theme("faith", "Faith", "Trust in God; a recurring theme across both testaments."),
        theme("grace", "Grace", "Unearned favour; central in Paul's letters."),
        theme("forgiveness", "Forgiveness", "Release from wrongdoing, divine and human."),
        theme("love", "Love", "Of God, and for neighbour; the greatest commandments."),
        theme("anxiety", "Anxiety", "Worry and its answer; e.g. Matthew 6:25–34."),
        theme("wisdom", "Wisdom", "Skill in living well; Proverbs, Job, Ecclesiastes."),
        theme("justice", "Justice", "Right dealing, especially toward the vulnerable."),
        theme("prayer", "Prayer", "Speaking with God; Psalms, the Lord's Prayer."),
        theme("money", "Money", "Wealth, generosity and its dangers."),
        theme("suffering", "Suffering", "Pain and its meaning; Job, Lamentations, the Passion."),
        // Events
        event("david-anointed", "Anointing of David", "Samuel anoints David in Bethlehem while Saul is still king."),
        event("david-goliath", "David and Goliath", "David defeats the Philistine champion in the Valley of Elah."),
        event("david-takes-jerusalem", "David takes Jerusalem", "David captures the Jebusite stronghold and makes it his capital."),
        event("exodus", "The Exodus", "Israel leaves Egypt under Moses."),
        // Passage nodes
        passageNode(.init(bookId: "Gen", chapter: 1)),
        passageNode(.init(bookId: "1Sam", chapter: 16)),
        passageNode(.init(bookId: "1Sam", chapter: 17)),
        passageNode(.init(bookId: "2Sam", chapter: 5)),
        passageNode(.init(bookId: "Ps", chapter: 23)),
        passageNode(.init(bookId: "Ps", chapter: 51)),
        passageNode(.init(bookId: "Matt", chapter: 6)),
        passageNode(.init(bookId: "John", chapter: 3)),
        passageNode(.init(bookId: "Rom", chapter: 8)),
    ]

    private static let byID: [EntityID: BibleEntity] = Dictionary(uniqueKeysWithValues: entities.map { ($0.id, $0) })
    static func entity(_ id: EntityID) -> BibleEntity? { byID[id] }

    // MARK: Relationships (§22.2), each with a source (§33)

    private static func edge(_ source: String, _ type: RelationshipType, _ target: String, scripture: Bool = true) -> BibleRelationship {
        BibleRelationship(
            id: "fixture.edge.\(source).\(type.rawValue).\(target)",
            sourceId: source, targetId: target, type: type,
            confidence: scripture ? 1.0 : 0.8,
            sourceReferenceIds: [scripture ? scriptureSource.id : editorialSource.id]
        )
    }
    private static let P = "fixture.person.", L = "fixture.place.", T = "fixture.theme.", E = "fixture.event."

    static let relationships: [BibleRelationship] = [
        // David cluster — the golden path
        edge(P + "david", .appearsIn, "passage.1Sam.16"),
        edge(P + "david", .appearsIn, "passage.1Sam.17"),
        edge(P + "david", .appearsIn, "passage.2Sam.5"),
        edge(P + "david", .appearsIn, "passage.Ps.23"),
        edge(P + "david", .appearsIn, "passage.Ps.51"),
        edge(P + "david", .relatedTo, P + "goliath"),
        edge(P + "david", .relatedTo, P + "saul"),
        edge(P + "david", .relatedTo, P + "samuel"),
        edge(P + "david", .relatedTo, P + "bathsheba"),
        edge(P + "david", .relatedTo, P + "solomon"),
        edge(P + "david", .participatesIn, E + "david-anointed"),
        edge(P + "david", .participatesIn, E + "david-goliath"),
        edge(P + "david", .participatesIn, E + "david-takes-jerusalem"),
        edge(P + "david", .relatedTo, L + "bethlehem"),
        edge(P + "david", .relatedTo, L + "jerusalem"),
        edge(P + "david", .relatedToTheme, T + "forgiveness", scripture: false),
        edge(P + "david", .relatedToTheme, T + "prayer", scripture: false),
        edge(P + "goliath", .appearsIn, "passage.1Sam.17"),
        edge(P + "goliath", .participatesIn, E + "david-goliath"),
        edge(P + "saul", .appearsIn, "passage.1Sam.16"),
        edge(P + "saul", .appearsIn, "passage.1Sam.17"),
        edge(P + "saul", .relatedTo, P + "samuel"),
        edge(P + "samuel", .appearsIn, "passage.1Sam.16"),
        edge(P + "samuel", .participatesIn, E + "david-anointed"),
        edge(P + "bathsheba", .relatedTo, P + "solomon"),
        edge(P + "solomon", .relatedTo, L + "jerusalem"),
        edge(E + "david-anointed", .occursAt, L + "bethlehem"),
        edge(E + "david-anointed", .precedes, E + "david-goliath"),
        edge(E + "david-goliath", .occursAt, L + "elah"),
        edge(E + "david-goliath", .precedes, E + "david-takes-jerusalem"),
        edge(E + "david-goliath", .relatedToTheme, T + "faith", scripture: false),
        edge(E + "david-takes-jerusalem", .occursAt, L + "jerusalem"),
        edge(E + "david-takes-jerusalem", .appearsIn, "passage.2Sam.5"),
        edge("passage.Ps.51", .relatedToTheme, T + "forgiveness", scripture: false),
        edge("passage.Ps.23", .relatedToTheme, T + "prayer", scripture: false),
        // Jesus cluster
        edge(P + "jesus", .appearsIn, "passage.John.3"),
        edge(P + "jesus", .appearsIn, "passage.Matt.6"),
        edge(P + "jesus", .relatedTo, P + "mary"),
        edge(P + "jesus", .relatedTo, P + "peter"),
        edge(P + "jesus", .relatedTo, P + "john"),
        edge(P + "jesus", .relatedTo, L + "bethlehem"),
        edge(P + "jesus", .relatedTo, L + "nazareth"),
        edge(P + "jesus", .relatedTo, L + "galilee"),
        edge(P + "jesus", .relatedTo, L + "jerusalem"),
        edge("passage.Matt.6", .relatedToTheme, T + "anxiety", scripture: false),
        edge("passage.Matt.6", .relatedToTheme, T + "money", scripture: false),
        edge("passage.Matt.6", .relatedToTheme, T + "prayer", scripture: false),
        edge("passage.John.3", .relatedToTheme, T + "love", scripture: false),
        edge("passage.John.3", .relatedToTheme, T + "faith", scripture: false),
        // Paul cluster
        edge(P + "paul", .appearsIn, "passage.Rom.8"),
        edge(P + "paul", .relatedTo, L + "rome"),
        edge(P + "paul", .relatedTo, L + "corinth"),
        edge(P + "paul", .relatedTo, L + "ephesus"),
        edge("passage.Rom.8", .relatedToTheme, T + "grace", scripture: false),
        edge("passage.Rom.8", .relatedToTheme, T + "suffering", scripture: false),
        // Moses / Abraham
        edge(P + "moses", .participatesIn, E + "exodus"),
        edge(P + "moses", .relatedTo, L + "egypt"),
        edge(E + "exodus", .occursAt, L + "egypt"),
        edge(P + "abraham", .relatedToTheme, T + "faith", scripture: false),
    ]

    // MARK: Details (§9)

    private static func detail(_ id: String, aliases: [String] = [], dates: String? = nil, role: String? = nil, modern: String? = nil, passages: [PassageReference] = []) -> EntityDetail {
        EntityDetail(entity: byID[id]!, aliases: aliases, approximateDates: dates, role: role, modernGeography: modern, keyPassages: passages, sources: sources)
    }

    static let details: [EntityID: EntityDetail] = Dictionary(uniqueKeysWithValues: [
        detail(P + "david", dates: "c. 1010–970 BC (commonly dated; the chronology is approximate)", role: "Second king of Israel",
               passages: [.init(bookId: "1Sam", chapter: 16), .init(bookId: "1Sam", chapter: 17), .init(bookId: "2Sam", chapter: 5), .init(bookId: "Ps", chapter: 23), .init(bookId: "Ps", chapter: 51)]),
        detail(P + "goliath", role: "Philistine champion from Gath", passages: [.init(bookId: "1Sam", chapter: 17)]),
        detail(P + "saul", dates: "late 11th century BC (commonly dated)", role: "First king of Israel", passages: [.init(bookId: "1Sam", chapter: 16), .init(bookId: "1Sam", chapter: 17)]),
        detail(P + "samuel", role: "Prophet and last of the judges", passages: [.init(bookId: "1Sam", chapter: 16)]),
        detail(P + "solomon", dates: "c. 970–931 BC (commonly dated)", role: "Third king of Israel"),
        detail(P + "jesus", aliases: ["Jesus of Nazareth", "Christ", "Messiah"], dates: "c. 4 BC – c. AD 30/33 (scholars vary)", role: "Central figure of the New Testament",
               passages: [.init(bookId: "John", chapter: 3), .init(bookId: "Matt", chapter: 6)]),
        detail(P + "paul", aliases: ["Saul of Tarsus"], dates: "c. AD 5 – c. 64/67 (scholars vary)", role: "Apostle to the Gentiles", passages: [.init(bookId: "Rom", chapter: 8)]),
        detail(P + "peter", aliases: ["Simon", "Cephas"], role: "Disciple and apostle"),
        detail(P + "moses", role: "Prophet; leader of the Exodus"),
        detail(P + "abraham", aliases: ["Abram"], role: "Patriarch"),
        detail(L + "jerusalem", aliases: ["Jebus", "Salem", "Zion"], modern: "Jerusalem, Israel/Palestine", passages: [.init(bookId: "2Sam", chapter: 5)]),
        detail(L + "bethlehem", aliases: ["Ephrath"], modern: "Bethlehem, West Bank", passages: [.init(bookId: "1Sam", chapter: 16)]),
        detail(L + "elah", modern: "Wadi es-Sunt, Israel (commonly identified)", passages: [.init(bookId: "1Sam", chapter: 17)]),
        detail(L + "egypt", modern: "Egypt"),
        detail(L + "rome", modern: "Rome, Italy", passages: [.init(bookId: "Rom", chapter: 8)]),
        detail(E + "david-goliath", dates: "during Saul's reign (commonly dated to the late 11th century BC)", passages: [.init(bookId: "1Sam", chapter: 17)]),
        detail(E + "david-anointed", dates: "during Saul's reign", passages: [.init(bookId: "1Sam", chapter: 16)]),
        detail(E + "david-takes-jerusalem", dates: "early in David's reign (commonly dated c. 1000 BC)", passages: [.init(bookId: "2Sam", chapter: 5)]),
        detail(E + "exodus", dates: "disputed; commonly placed in the 15th or 13th century BC"),
        detail(T + "forgiveness", passages: [.init(bookId: "Ps", chapter: 51)]),
        detail(T + "anxiety", passages: [.init(bookId: "Matt", chapter: 6, verses: 25...34)]),
        detail(T + "prayer", passages: [.init(bookId: "Ps", chapter: 23), .init(bookId: "Matt", chapter: 6, verses: 5...15)]),
        detail(T + "grace", passages: [.init(bookId: "Rom", chapter: 8)]),
        detail(T + "faith", passages: [.init(bookId: "1Sam", chapter: 17), .init(bookId: "John", chapter: 3)]),
        detail(T + "love", passages: [.init(bookId: "John", chapter: 3, verses: 16...16)]),
    ].map { ($0.entity.id, $0) })
}
