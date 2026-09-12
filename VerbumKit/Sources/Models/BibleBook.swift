public enum Testament: String, Codable, Sendable, CaseIterable {
    case old
    case new
}

/// A book of the Bible and the facts about it that never change per translation.
///
/// `id` is the OSIS book abbreviation (`"Gen"`, `"1Sam"`, `"Rev"`), the de facto
/// interchange id across Bible APIs and datasets. `abbreviations` are common
/// human short forms the reference parser will accept (spec §60 Task 3:
/// `"Jn 3:16"`). `name` is the English display name.
public struct BibleBook: Identifiable, Codable, Equatable, Hashable, Sendable {
    public let id: BookID
    public let name: String
    public let abbreviations: [String]
    public let testament: Testament
    /// 1-based position in the canon.
    public let order: Int
    public let chapterCount: Int

    public init(
        id: BookID,
        name: String,
        abbreviations: [String],
        testament: Testament,
        order: Int,
        chapterCount: Int
    ) {
        self.id = id
        self.name = name
        self.abbreviations = abbreviations
        self.testament = testament
        self.order = order
        self.chapterCount = chapterCount
    }
}

extension BibleBook {
    /// Lookup by OSIS id. `nil` when the id is not part of `canon`.
    public static func book(id: BookID) -> BibleBook? {
        canonByID[id]
    }

    private static let canonByID: [BookID: BibleBook] = Dictionary(
        uniqueKeysWithValues: canon.map { ($0.id, $0) }
    )

    /// The 66-book Protestant canon in canonical order.
    ///
    /// Deuterocanonical books are not included yet; adding them is a product
    /// decision tied to translation licensing (spec §34), not a code change here.
    /// Chapter counts follow the standard English versification.
    public static let canon: [BibleBook] = [
        BibleBook(id: "Gen", name: "Genesis", abbreviations: ["Gn", "Ge", "Gen"], testament: .old, order: 1, chapterCount: 50),
        BibleBook(id: "Exod", name: "Exodus", abbreviations: ["Ex", "Exo", "Exod"], testament: .old, order: 2, chapterCount: 40),
        BibleBook(id: "Lev", name: "Leviticus", abbreviations: ["Lv", "Le", "Lev"], testament: .old, order: 3, chapterCount: 27),
        BibleBook(id: "Num", name: "Numbers", abbreviations: ["Nm", "Nu", "Num"], testament: .old, order: 4, chapterCount: 36),
        BibleBook(id: "Deut", name: "Deuteronomy", abbreviations: ["Dt", "De", "Deu", "Deut"], testament: .old, order: 5, chapterCount: 34),
        BibleBook(id: "Josh", name: "Joshua", abbreviations: ["Jos", "Josh"], testament: .old, order: 6, chapterCount: 24),
        BibleBook(id: "Judg", name: "Judges", abbreviations: ["Jdg", "Jgs", "Judg"], testament: .old, order: 7, chapterCount: 21),
        BibleBook(id: "Ruth", name: "Ruth", abbreviations: ["Ru", "Rth"], testament: .old, order: 8, chapterCount: 4),
        BibleBook(id: "1Sam", name: "1 Samuel", abbreviations: ["1 Sam", "1 Sm", "1Sam", "1Sm", "I Samuel"], testament: .old, order: 9, chapterCount: 31),
        BibleBook(id: "2Sam", name: "2 Samuel", abbreviations: ["2 Sam", "2 Sm", "2Sam", "2Sm", "II Samuel"], testament: .old, order: 10, chapterCount: 24),
        BibleBook(id: "1Kgs", name: "1 Kings", abbreviations: ["1 Kgs", "1 Ki", "1Kgs", "1Ki", "I Kings"], testament: .old, order: 11, chapterCount: 22),
        BibleBook(id: "2Kgs", name: "2 Kings", abbreviations: ["2 Kgs", "2 Ki", "2Kgs", "2Ki", "II Kings"], testament: .old, order: 12, chapterCount: 25),
        BibleBook(id: "1Chr", name: "1 Chronicles", abbreviations: ["1 Chr", "1 Ch", "1Chr", "1Ch", "I Chronicles"], testament: .old, order: 13, chapterCount: 29),
        BibleBook(id: "2Chr", name: "2 Chronicles", abbreviations: ["2 Chr", "2 Ch", "2Chr", "2Ch", "II Chronicles"], testament: .old, order: 14, chapterCount: 36),
        BibleBook(id: "Ezra", name: "Ezra", abbreviations: ["Ezr"], testament: .old, order: 15, chapterCount: 10),
        BibleBook(id: "Neh", name: "Nehemiah", abbreviations: ["Ne", "Neh"], testament: .old, order: 16, chapterCount: 13),
        BibleBook(id: "Esth", name: "Esther", abbreviations: ["Es", "Est", "Esth"], testament: .old, order: 17, chapterCount: 10),
        BibleBook(id: "Job", name: "Job", abbreviations: ["Jb"], testament: .old, order: 18, chapterCount: 42),
        BibleBook(id: "Ps", name: "Psalms", abbreviations: ["Ps", "Psa", "Psalm", "Pss"], testament: .old, order: 19, chapterCount: 150),
        BibleBook(id: "Prov", name: "Proverbs", abbreviations: ["Pr", "Prv", "Pro", "Prov"], testament: .old, order: 20, chapterCount: 31),
        BibleBook(id: "Eccl", name: "Ecclesiastes", abbreviations: ["Ec", "Ecc", "Eccl", "Qoheleth"], testament: .old, order: 21, chapterCount: 12),
        BibleBook(id: "Song", name: "Song of Solomon", abbreviations: ["Sg", "Song", "Song of Songs", "SoS", "Canticles"], testament: .old, order: 22, chapterCount: 8),
        BibleBook(id: "Isa", name: "Isaiah", abbreviations: ["Is", "Isa"], testament: .old, order: 23, chapterCount: 66),
        BibleBook(id: "Jer", name: "Jeremiah", abbreviations: ["Je", "Jer"], testament: .old, order: 24, chapterCount: 52),
        BibleBook(id: "Lam", name: "Lamentations", abbreviations: ["La", "Lam"], testament: .old, order: 25, chapterCount: 5),
        BibleBook(id: "Ezek", name: "Ezekiel", abbreviations: ["Eze", "Ezk", "Ezek"], testament: .old, order: 26, chapterCount: 48),
        BibleBook(id: "Dan", name: "Daniel", abbreviations: ["Da", "Dn", "Dan"], testament: .old, order: 27, chapterCount: 12),
        BibleBook(id: "Hos", name: "Hosea", abbreviations: ["Ho", "Hos"], testament: .old, order: 28, chapterCount: 14),
        BibleBook(id: "Joel", name: "Joel", abbreviations: ["Jl", "Joe"], testament: .old, order: 29, chapterCount: 3),
        BibleBook(id: "Amos", name: "Amos", abbreviations: ["Am"], testament: .old, order: 30, chapterCount: 9),
        BibleBook(id: "Obad", name: "Obadiah", abbreviations: ["Ob", "Oba", "Obad"], testament: .old, order: 31, chapterCount: 1),
        BibleBook(id: "Jonah", name: "Jonah", abbreviations: ["Jon", "Jnh"], testament: .old, order: 32, chapterCount: 4),
        BibleBook(id: "Mic", name: "Micah", abbreviations: ["Mi", "Mic"], testament: .old, order: 33, chapterCount: 7),
        BibleBook(id: "Nah", name: "Nahum", abbreviations: ["Na", "Nah"], testament: .old, order: 34, chapterCount: 3),
        BibleBook(id: "Hab", name: "Habakkuk", abbreviations: ["Hb", "Hab"], testament: .old, order: 35, chapterCount: 3),
        BibleBook(id: "Zeph", name: "Zephaniah", abbreviations: ["Zp", "Zep", "Zeph"], testament: .old, order: 36, chapterCount: 3),
        BibleBook(id: "Hag", name: "Haggai", abbreviations: ["Hg", "Hag"], testament: .old, order: 37, chapterCount: 2),
        BibleBook(id: "Zech", name: "Zechariah", abbreviations: ["Zc", "Zec", "Zech"], testament: .old, order: 38, chapterCount: 14),
        BibleBook(id: "Mal", name: "Malachi", abbreviations: ["Ml", "Mal"], testament: .old, order: 39, chapterCount: 4),
        BibleBook(id: "Matt", name: "Matthew", abbreviations: ["Mt", "Mat", "Matt"], testament: .new, order: 40, chapterCount: 28),
        BibleBook(id: "Mark", name: "Mark", abbreviations: ["Mk", "Mr", "Mrk"], testament: .new, order: 41, chapterCount: 16),
        BibleBook(id: "Luke", name: "Luke", abbreviations: ["Lk", "Lu", "Luk"], testament: .new, order: 42, chapterCount: 24),
        BibleBook(id: "John", name: "John", abbreviations: ["Jn", "Jhn", "Joh"], testament: .new, order: 43, chapterCount: 21),
        BibleBook(id: "Acts", name: "Acts", abbreviations: ["Ac", "Act"], testament: .new, order: 44, chapterCount: 28),
        BibleBook(id: "Rom", name: "Romans", abbreviations: ["Ro", "Rm", "Rom"], testament: .new, order: 45, chapterCount: 16),
        BibleBook(id: "1Cor", name: "1 Corinthians", abbreviations: ["1 Cor", "1 Co", "1Cor", "1Co", "I Corinthians"], testament: .new, order: 46, chapterCount: 16),
        BibleBook(id: "2Cor", name: "2 Corinthians", abbreviations: ["2 Cor", "2 Co", "2Cor", "2Co", "II Corinthians"], testament: .new, order: 47, chapterCount: 13),
        BibleBook(id: "Gal", name: "Galatians", abbreviations: ["Ga", "Gal"], testament: .new, order: 48, chapterCount: 6),
        BibleBook(id: "Eph", name: "Ephesians", abbreviations: ["Ep", "Eph"], testament: .new, order: 49, chapterCount: 6),
        BibleBook(id: "Phil", name: "Philippians", abbreviations: ["Php", "Phil", "Philip"], testament: .new, order: 50, chapterCount: 4),
        BibleBook(id: "Col", name: "Colossians", abbreviations: ["Co", "Col"], testament: .new, order: 51, chapterCount: 4),
        BibleBook(id: "1Thess", name: "1 Thessalonians", abbreviations: ["1 Thess", "1 Th", "1Thess", "1Th", "I Thessalonians"], testament: .new, order: 52, chapterCount: 5),
        BibleBook(id: "2Thess", name: "2 Thessalonians", abbreviations: ["2 Thess", "2 Th", "2Thess", "2Th", "II Thessalonians"], testament: .new, order: 53, chapterCount: 3),
        BibleBook(id: "1Tim", name: "1 Timothy", abbreviations: ["1 Tim", "1 Ti", "1Tim", "1Ti", "I Timothy"], testament: .new, order: 54, chapterCount: 6),
        BibleBook(id: "2Tim", name: "2 Timothy", abbreviations: ["2 Tim", "2 Ti", "2Tim", "2Ti", "II Timothy"], testament: .new, order: 55, chapterCount: 4),
        BibleBook(id: "Titus", name: "Titus", abbreviations: ["Tit"], testament: .new, order: 56, chapterCount: 3),
        BibleBook(id: "Phlm", name: "Philemon", abbreviations: ["Phm", "Phlm", "Philem"], testament: .new, order: 57, chapterCount: 1),
        BibleBook(id: "Heb", name: "Hebrews", abbreviations: ["He", "Heb"], testament: .new, order: 58, chapterCount: 13),
        BibleBook(id: "Jas", name: "James", abbreviations: ["Ja", "Jm", "Jas"], testament: .new, order: 59, chapterCount: 5),
        BibleBook(id: "1Pet", name: "1 Peter", abbreviations: ["1 Pet", "1 Pe", "1Pet", "1Pe", "I Peter"], testament: .new, order: 60, chapterCount: 5),
        BibleBook(id: "2Pet", name: "2 Peter", abbreviations: ["2 Pet", "2 Pe", "2Pet", "2Pe", "II Peter"], testament: .new, order: 61, chapterCount: 3),
        BibleBook(id: "1John", name: "1 John", abbreviations: ["1 Jn", "1 Jo", "1John", "1Jn", "I John"], testament: .new, order: 62, chapterCount: 5),
        BibleBook(id: "2John", name: "2 John", abbreviations: ["2 Jn", "2 Jo", "2John", "2Jn", "II John"], testament: .new, order: 63, chapterCount: 1),
        BibleBook(id: "3John", name: "3 John", abbreviations: ["3 Jn", "3 Jo", "3John", "3Jn", "III John"], testament: .new, order: 64, chapterCount: 1),
        BibleBook(id: "Jude", name: "Jude", abbreviations: ["Jud", "Jde"], testament: .new, order: 65, chapterCount: 1),
        BibleBook(id: "Rev", name: "Revelation", abbreviations: ["Re", "Rv", "Rev", "Apocalypse"], testament: .new, order: 66, chapterCount: 22),
    ]
}
