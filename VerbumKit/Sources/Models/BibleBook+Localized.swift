import Foundation

/// A book's name and accepted abbreviations in one language.
public struct LocalizedBookName: Equatable, Sendable {
    public let name: String
    public let abbreviations: [String]
}

/// Which naming table applies to a locale. English is the base (`BibleBook.name`
/// and `abbreviations`); other languages overlay it. Any Portuguese locale
/// gets the Brazilian table.
public enum BookLanguage: String, Sendable, CaseIterable {
    case english = "en"
    case portuguese = "pt"

    public init(locale: Locale) {
        self = (locale.language.languageCode?.identifier == "pt" || locale.region?.identifier == "BR") ? .portuguese : .english
    }

    public var locale: Locale { Locale(identifier: self == .portuguese ? "pt_BR" : "en_US") }

    /// Resolved once: iOS relaunches the app when its language changes.
    public static let current: BookLanguage = BookLanguage(locale: .current)
}

extension BibleBook {
    /// Display name in the device language (`João`, `1 Samuel`, `Gênesis`).
    public var localizedName: String { localizedName(for: .current) }

    public func localizedName(for language: BookLanguage) -> String {
        Self.localizedNames[language.rawValue]?[id]?.name ?? name
    }

    /// Abbreviations in that language, in addition to the English ones the
    /// parser always accepts. Empty for English.
    public func localizedAbbreviations(for language: BookLanguage) -> [String] {
        Self.localizedNames[language.rawValue]?[id]?.abbreviations ?? []
    }

    /// Generated from one table shared with Android. Brazilian Portuguese
    /// follows the Almeida convention (Gn, Êx, Sl, Jo, Ap…).
    static let localizedNames: [String: [BookID: LocalizedBookName]] = [
        "pt": [
            "Gen": LocalizedBookName(name: "Gênesis", abbreviations: ["Gn", "Gên", "Gen"]),
            "Exod": LocalizedBookName(name: "Êxodo", abbreviations: ["Êx", "Ex", "Êxo"]),
            "Lev": LocalizedBookName(name: "Levítico", abbreviations: ["Lv", "Lev"]),
            "Num": LocalizedBookName(name: "Números", abbreviations: ["Nm", "Núm", "Num"]),
            "Deut": LocalizedBookName(name: "Deuteronômio", abbreviations: ["Dt", "Deut"]),
            "Josh": LocalizedBookName(name: "Josué", abbreviations: ["Js", "Jos"]),
            "Judg": LocalizedBookName(name: "Juízes", abbreviations: ["Jz", "Juí", "Jui"]),
            "Ruth": LocalizedBookName(name: "Rute", abbreviations: ["Rt", "Rut"]),
            "1Sam": LocalizedBookName(name: "1 Samuel", abbreviations: ["1Sm", "1 Sm", "1 Sam"]),
            "2Sam": LocalizedBookName(name: "2 Samuel", abbreviations: ["2Sm", "2 Sm", "2 Sam"]),
            "1Kgs": LocalizedBookName(name: "1 Reis", abbreviations: ["1Rs", "1 Rs", "1 Reis"]),
            "2Kgs": LocalizedBookName(name: "2 Reis", abbreviations: ["2Rs", "2 Rs", "2 Reis"]),
            "1Chr": LocalizedBookName(name: "1 Crônicas", abbreviations: ["1Cr", "1 Cr", "1 Crô"]),
            "2Chr": LocalizedBookName(name: "2 Crônicas", abbreviations: ["2Cr", "2 Cr", "2 Crô"]),
            "Ezra": LocalizedBookName(name: "Esdras", abbreviations: ["Ed", "Esd"]),
            "Neh": LocalizedBookName(name: "Neemias", abbreviations: ["Ne", "Nee"]),
            "Esth": LocalizedBookName(name: "Ester", abbreviations: ["Et", "Est"]),
            "Job": LocalizedBookName(name: "Jó", abbreviations: ["Jó", "Job"]),
            "Ps": LocalizedBookName(name: "Salmos", abbreviations: ["Sl", "Sal", "Salmo"]),
            "Prov": LocalizedBookName(name: "Provérbios", abbreviations: ["Pv", "Pr", "Prov"]),
            "Eccl": LocalizedBookName(name: "Eclesiastes", abbreviations: ["Ec", "Ecl"]),
            "Song": LocalizedBookName(name: "Cântico dos Cânticos", abbreviations: ["Ct", "Cânticos", "Cantares", "Cânt"]),
            "Isa": LocalizedBookName(name: "Isaías", abbreviations: ["Is", "Isa"]),
            "Jer": LocalizedBookName(name: "Jeremias", abbreviations: ["Jr", "Jer"]),
            "Lam": LocalizedBookName(name: "Lamentações", abbreviations: ["Lm", "Lam"]),
            "Ezek": LocalizedBookName(name: "Ezequiel", abbreviations: ["Ez", "Eze"]),
            "Dan": LocalizedBookName(name: "Daniel", abbreviations: ["Dn", "Dan"]),
            "Hos": LocalizedBookName(name: "Oseias", abbreviations: ["Os", "Ose"]),
            "Joel": LocalizedBookName(name: "Joel", abbreviations: ["Jl"]),
            "Amos": LocalizedBookName(name: "Amós", abbreviations: ["Am"]),
            "Obad": LocalizedBookName(name: "Obadias", abbreviations: ["Ob", "Oba"]),
            "Jonah": LocalizedBookName(name: "Jonas", abbreviations: ["Jn", "Jon"]),
            "Mic": LocalizedBookName(name: "Miqueias", abbreviations: ["Mq", "Miq"]),
            "Nah": LocalizedBookName(name: "Naum", abbreviations: ["Na"]),
            "Hab": LocalizedBookName(name: "Habacuque", abbreviations: ["Hc", "Hab"]),
            "Zeph": LocalizedBookName(name: "Sofonias", abbreviations: ["Sf", "Sof"]),
            "Hag": LocalizedBookName(name: "Ageu", abbreviations: ["Ag"]),
            "Zech": LocalizedBookName(name: "Zacarias", abbreviations: ["Zc", "Zac"]),
            "Mal": LocalizedBookName(name: "Malaquias", abbreviations: ["Ml", "Mal"]),
            "Matt": LocalizedBookName(name: "Mateus", abbreviations: ["Mt", "Mat"]),
            "Mark": LocalizedBookName(name: "Marcos", abbreviations: ["Mc", "Mr", "Mar"]),
            "Luke": LocalizedBookName(name: "Lucas", abbreviations: ["Lc", "Luc"]),
            "John": LocalizedBookName(name: "João", abbreviations: ["Jo", "Joao"]),
            "Acts": LocalizedBookName(name: "Atos", abbreviations: ["At", "Atos"]),
            "Rom": LocalizedBookName(name: "Romanos", abbreviations: ["Rm", "Ro", "Rom"]),
            "1Cor": LocalizedBookName(name: "1 Coríntios", abbreviations: ["1Co", "1 Co", "1 Cor"]),
            "2Cor": LocalizedBookName(name: "2 Coríntios", abbreviations: ["2Co", "2 Co", "2 Cor"]),
            "Gal": LocalizedBookName(name: "Gálatas", abbreviations: ["Gl", "Gál"]),
            "Eph": LocalizedBookName(name: "Efésios", abbreviations: ["Ef", "Efé"]),
            "Phil": LocalizedBookName(name: "Filipenses", abbreviations: ["Fp", "Fl", "Fil"]),
            "Col": LocalizedBookName(name: "Colossenses", abbreviations: ["Cl", "Col"]),
            "1Thess": LocalizedBookName(name: "1 Tessalonicenses", abbreviations: ["1Ts", "1 Ts", "1 Tes"]),
            "2Thess": LocalizedBookName(name: "2 Tessalonicenses", abbreviations: ["2Ts", "2 Ts", "2 Tes"]),
            "1Tim": LocalizedBookName(name: "1 Timóteo", abbreviations: ["1Tm", "1 Tm", "1 Tim"]),
            "2Tim": LocalizedBookName(name: "2 Timóteo", abbreviations: ["2Tm", "2 Tm", "2 Tim"]),
            "Titus": LocalizedBookName(name: "Tito", abbreviations: ["Tt", "Tit"]),
            "Phlm": LocalizedBookName(name: "Filemom", abbreviations: ["Fm", "Flm"]),
            "Heb": LocalizedBookName(name: "Hebreus", abbreviations: ["Hb", "Heb"]),
            "Jas": LocalizedBookName(name: "Tiago", abbreviations: ["Tg", "Tia"]),
            "1Pet": LocalizedBookName(name: "1 Pedro", abbreviations: ["1Pe", "1 Pe", "1 Pd"]),
            "2Pet": LocalizedBookName(name: "2 Pedro", abbreviations: ["2Pe", "2 Pe", "2 Pd"]),
            "1John": LocalizedBookName(name: "1 João", abbreviations: ["1Jo", "1 Jo"]),
            "2John": LocalizedBookName(name: "2 João", abbreviations: ["2Jo", "2 Jo"]),
            "3John": LocalizedBookName(name: "3 João", abbreviations: ["3Jo", "3 Jo"]),
            "Jude": LocalizedBookName(name: "Judas", abbreviations: ["Jd", "Jud"]),
            "Rev": LocalizedBookName(name: "Apocalipse", abbreviations: ["Ap", "Apoc"]),
        ],
    ]
}

extension BibleBook.Division {
    public var localizedTitle: String { localizedTitle(for: .current) }

    public func localizedTitle(for language: BookLanguage) -> String {
        switch language {
        case .english: title
        case .portuguese: Self.portugueseTitles[self] ?? title
        }
    }

    private static let portugueseTitles: [BibleBook.Division: String] = [
            .law: "Lei",
            .history: "História",
            .poetry: "Poesia e Sabedoria",
            .prophets: "Profetas",
            .gospelsAndActs: "Evangelhos e Atos",
            .lettersOfPaul: "Cartas de Paulo",
            .generalLetters: "Cartas Gerais",
            .revelation: "Apocalipse",
    ]
}
