import Foundation

public enum ReadingCollectionFilter: String, CaseIterable, Sendable {
    case all, saved, highlights, notes
}

/// Local-only search: references and the reader's notes, never a paid request.
public enum ReadingCollection {
    public static func entries(_ annotations: [ReaderAnnotation], filter: ReadingCollectionFilter, query: String) -> [ReaderAnnotation] {
        let terms = normalized(query).split(whereSeparator: { $0.isWhitespace })
        return annotations.filter { item in
            guard item.bookmarked == true || item.highlight != nil || !item.note.isEmpty else { return false }
            switch filter {
            case .saved: guard item.bookmarked == true else { return false }
            case .highlights: guard item.highlight != nil else { return false }
            case .notes: guard !item.note.isEmpty else { return false }
            case .all: break
            }
            let book = BibleBook.book(id: item.reference.bookId)
            let names = BookLanguage.allCases.map { book?.localizedName(for: $0) ?? "" }
            let reference = "\(item.reference.chapter):\(item.reference.verses?.lowerBound ?? 1)"
            let haystack = normalized(([item.reference.bookId, reference, item.note] + names).joined(separator: " "))
            return terms.allSatisfy { haystack.contains($0) }
        }.sorted {
            let left = ReaderCanon.index($0.reference), right = ReaderCanon.index($1.reference)
            return left == right ? ($0.reference.verses?.lowerBound ?? 1) < ($1.reference.verses?.lowerBound ?? 1) : left < right
        }
    }

    private static func normalized(_ text: String) -> String {
        text.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "en_US_POSIX"))
    }
}
