package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.clients.BibleClientException
import com.nexussoft.verbum.models.BookId
import com.nexussoft.verbum.models.PassageReference

/** What the reader can tell the user when a chapter fails to load (docs/PRODUCT.md §52). */
sealed interface ReaderError {
    data class ChapterUnavailable(val reference: PassageReference) : ReaderError
    data class UnknownBook(val bookId: BookId) : ReaderError
    data object Offline : ReaderError
    data object Unexpected : ReaderError

    /** Title and message as string resources; the argument (if any) is filled in by the view. */
    val titleRes: Int
        get() = when (this) {
            is ChapterUnavailable -> R.string.err_chapter_unavailable_title
            is UnknownBook -> R.string.err_unknown_book_title
            Offline -> R.string.err_offline_title
            Unexpected -> R.string.err_unexpected_title
        }

    val messageRes: Int
        get() = when (this) {
            is ChapterUnavailable -> R.string.err_chapter_unavailable_message
            is UnknownBook -> R.string.err_unknown_book_message
            Offline -> R.string.err_offline_message
            Unexpected -> R.string.err_unexpected_message
        }

    /** Argument for the title/message format, when there is one. */
    val argument: String?
        get() = when (this) {
            is ChapterUnavailable -> reference.formatted
            is UnknownBook -> bookId
            Offline, Unexpected -> null
        }

    companion object {
        fun from(error: Throwable): ReaderError = when (error) {
            is BibleClientException.ContentUnavailable -> ChapterUnavailable(error.reference)
            is BibleClientException.VerseOutOfRange -> ChapterUnavailable(PassageReference(error.reference.bookId, error.reference.chapter))
            is BibleClientException.UnknownBook -> UnknownBook(error.bookId)
            is BibleClientException.NetworkUnavailable -> Offline
            else -> Unexpected
        }
    }
}
