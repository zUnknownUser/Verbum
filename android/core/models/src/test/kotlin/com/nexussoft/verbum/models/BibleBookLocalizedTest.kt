package com.nexussoft.verbum.models

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleBookLocalizedTest {
    @Test
    fun everyBookHasAPortugueseName() {
        val sameInBoth = setOf("Daniel", "Joel", "1 Samuel", "2 Samuel")
        for (book in BibleBook.canon) {
            assertTrue(book.localizedName(BookLanguage.PORTUGUESE) != book.name || book.name in sameInBoth, "${book.id} has no Portuguese name")
            assertTrue(book.localizedAbbreviations(BookLanguage.PORTUGUESE).isNotEmpty(), "${book.id} has no Portuguese abbreviations")
        }
        assertEquals("João", BibleBook.book("John")?.localizedName(BookLanguage.PORTUGUESE))
        assertEquals("Gênesis", BibleBook.book("Gen")?.localizedName(BookLanguage.PORTUGUESE))
    }

    @Test
    fun englishIsTheBase() {
        for (book in BibleBook.canon) {
            assertEquals(book.name, book.localizedName(BookLanguage.ENGLISH))
            assertTrue(book.localizedAbbreviations(BookLanguage.ENGLISH).isEmpty())
        }
    }

    @Test
    fun portugueseKeysNeverCollideAcrossBooks() {
        val seen = mutableMapOf<String, BookId>()
        for (book in BibleBook.canon) {
            for (key in (listOf(book.localizedName(BookLanguage.PORTUGUESE)) + book.localizedAbbreviations(BookLanguage.PORTUGUESE)).map { it.lowercase().filterNot(Char::isWhitespace) }) {
                val other = seen[key]
                assertTrue(other == null || other == book.id, "$key is used by $other and ${book.id}")
                seen[key] = book.id
            }
        }
    }

    @Test
    fun languageFollowsTheLocale() {
        assertEquals(BookLanguage.PORTUGUESE, BookLanguage.of(Locale("en", "BR")))
        assertEquals(BookLanguage.PORTUGUESE, BookLanguage.of(Locale("es", "BR")))
        assertEquals(BookLanguage.PORTUGUESE, BookLanguage.of(Locale("pt", "BR")))
        assertEquals(BookLanguage.PORTUGUESE, BookLanguage.of(Locale("pt", "PT")))
        assertEquals(BookLanguage.ENGLISH, BookLanguage.of(Locale.US))
        assertEquals(BookLanguage.ENGLISH, BookLanguage.of(Locale("es", "ES")))
    }

    @Test
    fun formattedReferencesFollowTheLanguage() {
        val ref = PassageReference("John", 3, 16..18)
        assertEquals("John 3:16-18", ref.formatted(BookLanguage.ENGLISH))
        assertEquals("João 3:16-18", ref.formatted(BookLanguage.PORTUGUESE))
        assertEquals("Poesia e Sabedoria", Division.POETRY.localizedTitle(BookLanguage.PORTUGUESE))
    }
}
