import Models

/// Chapter arithmetic over the canon: stepping past the last chapter of a
/// book continues into the next book, and before chapter 1 into the previous
/// book's last chapter. Pure, so the reducer stays small and this stays tested.
enum ChapterNavigation {
    static func next(after reference: PassageReference) -> PassageReference? {
        guard let book = BibleBook.book(id: reference.bookId) else { return nil }
        if reference.chapter < book.chapterCount {
            return PassageReference(bookId: book.id, chapter: reference.chapter + 1)
        }
        guard let nextBook = BibleBook.canon.first(where: { $0.order == book.order + 1 }) else { return nil }
        return PassageReference(bookId: nextBook.id, chapter: 1)
    }

    static func previous(before reference: PassageReference) -> PassageReference? {
        guard let book = BibleBook.book(id: reference.bookId) else { return nil }
        if reference.chapter > 1 {
            return PassageReference(bookId: book.id, chapter: reference.chapter - 1)
        }
        guard let previousBook = BibleBook.canon.first(where: { $0.order == book.order - 1 }) else { return nil }
        return PassageReference(bookId: previousBook.id, chapter: previousBook.chapterCount)
    }
}

/// Formats a verse selection the way people cite it: contiguous runs become
/// ranges, gaps become commas. `John 3:16-18, 21`.
enum SelectionFormatter {
    static func format(bookId: BookID, chapter: Int, verses: Set<Int>) -> String? {
        guard !verses.isEmpty else { return nil }
        let bookName = BibleBook.book(id: bookId)?.name ?? bookId
        var runs: [ClosedRange<Int>] = []
        for verse in verses.sorted() {
            if let last = runs.last, last.upperBound + 1 == verse {
                runs[runs.count - 1] = last.lowerBound...verse
            } else {
                runs.append(verse...verse)
            }
        }
        let parts = runs.map { $0.lowerBound == $0.upperBound ? "\($0.lowerBound)" : "\($0.lowerBound)-\($0.upperBound)" }
        return "\(bookName) \(chapter):\(parts.joined(separator: ", "))"
    }
}
