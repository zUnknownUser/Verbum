extension BibleBook {
    /// Traditional grouping of the Protestant canon by literary kind. Used to
    /// lay the canon out as a map: a shelf per division.
    public enum Division: String, Codable, Sendable, CaseIterable {
        case law
        case history
        case poetry
        case prophets
        case gospelsAndActs
        case lettersOfPaul
        case generalLetters
        case revelation

        public var title: String {
            switch self {
            case .law: "Law"
            case .history: "History"
            case .poetry: "Poetry & Wisdom"
            case .prophets: "Prophets"
            case .gospelsAndActs: "Gospels & Acts"
            case .lettersOfPaul: "Letters of Paul"
            case .generalLetters: "General Letters"
            case .revelation: "Revelation"
            }
        }

        public var testament: Testament {
            switch self {
            case .law, .history, .poetry, .prophets: .old
            case .gospelsAndActs, .lettersOfPaul, .generalLetters, .revelation: .new
            }
        }

        /// Books in canonical order. Computed once per division.
        public var books: [BibleBook] { Self.booksByDivision[self] ?? [] }

        private static let booksByDivision: [Division: [BibleBook]] = Dictionary(grouping: BibleBook.canon, by: \.division)
    }

    public var division: Division {
        switch order {
        case 1...5: .law
        case 6...17: .history
        case 18...22: .poetry
        case 23...39: .prophets
        case 40...44: .gospelsAndActs
        case 45...57: .lettersOfPaul
        case 58...65: .generalLetters
        default: .revelation
        }
    }
}
