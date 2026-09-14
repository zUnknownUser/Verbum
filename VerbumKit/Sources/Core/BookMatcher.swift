import Foundation
import Models

/// Finds books a partial query could mean: `sam` → 1 Samuel, 2 Samuel;
/// `jn` → John; `song` → Song of Solomon. Pure, deterministic, canon-ordered.
public enum BookMatcher {
    /// Books whose name, any word of the name, or any abbreviation starts with
    /// the query (case-insensitive, periods and spaces ignored). Books the
    /// query names exactly come first (`jn` → John before Jonah); within each
    /// group, canon order. Empty query matches nothing.
    public static func books(matching query: String, language: BookLanguage = .english) -> [BibleBook] {
        let key = normalize(query)
        guard !key.isEmpty else { return [] }
        var exact: [BibleBook] = []
        var prefix: [BibleBook] = []
        for book in BibleBook.canon {
            let keys = [book.name, book.localizedName(for: language)] + book.abbreviations + book.localizedAbbreviations(for: language)
            if keys.contains(where: { normalize($0) == key }) {
                exact.append(book)
            } else if keys.contains(where: { normalize($0).hasPrefix(key) })
                || [book.name, book.localizedName(for: language)].flatMap({ $0.split(separator: " ") }).contains(where: { normalize(String($0)).hasPrefix(key) && !$0.allSatisfy(\.isNumber) }) {
                prefix.append(book)
            }
        }
        return exact + prefix
    }

    static func normalize(_ text: String) -> String {
        text.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "en_US_POSIX")).filter { $0 != "." && !$0.isWhitespace }
    }
}
