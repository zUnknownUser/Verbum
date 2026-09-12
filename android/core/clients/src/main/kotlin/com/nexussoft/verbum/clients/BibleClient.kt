package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.BiblePassage
import com.nexussoft.verbum.models.BookId
import com.nexussoft.verbum.models.PassageReference

/**
 * Reads Scripture text (docs/PRODUCT.md §38). Mirrors the iOS `BibleClient`.
 *
 * Features depend on this interface and never see a concrete backend.
 * [chapter] returns one [BiblePassage] per verse so readers can address verses
 * individually; [passage] returns the requested range as a single passage with
 * its verses joined.
 *
 * Both functions throw [BibleClientException] on failure.
 */
interface BibleClient {
    suspend fun passage(reference: PassageReference): BiblePassage
    suspend fun chapter(bookId: BookId, chapter: Int): List<BiblePassage>
}

/**
 * Why Scripture could not be read (§52: distinguish content unavailable from
 * network problems; never surface raw backend errors).
 */
sealed class BibleClientException : Exception() {
    /** The book id is not in the canon. */
    data class UnknownBook(val bookId: BookId) : BibleClientException()
    /** The chapter is valid but no text is available for it in this translation/source. */
    data class ContentUnavailable(val reference: PassageReference) : BibleClientException()
    /** The chapter exists but the requested verses exceed what it contains. */
    data class VerseOutOfRange(val reference: PassageReference, val available: Int) : BibleClientException()
    /** Could not reach the content service and nothing is cached (§52). */
    data object NetworkUnavailable : BibleClientException() {
        private fun readResolve(): Any = NetworkUnavailable
    }

    // data classes on an Exception subclass: keep equality by fields, not by stack trace
    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int
}

/** Convenience used by fixtures and, later, backend mappers. */
internal fun requireKnownBook(bookId: BookId): BibleBook =
    BibleBook.book(bookId) ?: throw BibleClientException.UnknownBook(bookId)
