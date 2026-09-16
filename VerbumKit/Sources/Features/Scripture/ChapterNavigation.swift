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
