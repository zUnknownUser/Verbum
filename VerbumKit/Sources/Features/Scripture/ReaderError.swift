import Clients
import Models

/// What the reader can tell the user when a chapter fails to load (spec §52:
/// distinguish causes, never show raw errors).
public enum ReaderError: Error, Equatable, Sendable {
    case chapterUnavailable(PassageReference)
    case unknownBook(BookID)
    case offline
    case unexpected

    init(_ error: any Error) {
        switch error {
        case BibleClientError.contentUnavailable(let reference):
            self = .chapterUnavailable(reference)
        case BibleClientError.verseOutOfRange(let reference, _):
            self = .chapterUnavailable(PassageReference(bookId: reference.bookId, chapter: reference.chapter))
        case BibleClientError.unknownBook(let bookId):
            self = .unknownBook(bookId)
        case BibleClientError.networkUnavailable:
            self = .offline
        default:
            self = .unexpected
        }
    }

    public var title: String {
        switch self {
        case .chapterUnavailable(let reference): L10n.t("\(reference.formatted) isn't available yet")
        case .unknownBook: L10n.t("Unknown book")
        case .offline: L10n.t("You're offline")
        case .unexpected: L10n.t("Couldn't load this chapter")
        }
    }

    public var message: String {
        switch self {
        case .chapterUnavailable:
            L10n.t("This chapter isn't in this translation.")
        case .unknownBook(let bookId):
            L10n.t("“\(bookId)” isn't a book we know.")
        case .offline:
            L10n.t("Connect to load this chapter. Chapters you've already read stay available.")
        case .unexpected:
            L10n.t("Something went wrong on our side. Try again in a moment.")
        }
    }
}
