package com.nexussoft.verbum.models

enum class Testament { OLD, NEW }

/**
 * A book of the Bible and the facts about it that never change per translation.
 *
 * [id] is the OSIS book abbreviation (`"Gen"`, `"1Sam"`, `"Rev"`), the de facto
 * interchange id across Bible APIs and datasets. [abbreviations] are common
 * human short forms the reference parser will accept (docs/PRODUCT.md §60 Task 3:
 * `"Jn 3:16"`). [name] is the English display name.
 */
data class BibleBook(
    val id: BookId,
    val name: String,
    val abbreviations: List<String>,
    val testament: Testament,
    /** 1-based position in the canon. */
    val order: Int,
    val chapterCount: Int,
) {
    companion object {
        /** Lookup by OSIS id. `null` when the id is not part of [canon]. */
        fun book(id: BookId): BibleBook? = canonById[id]

        /**
         * The 66-book Protestant canon in canonical order.
         *
         * Deuterocanonical books are not included yet; adding them is a product
         * decision tied to translation licensing (docs/PRODUCT.md §34), not a code
         * change here. Chapter counts follow the standard English versification.
         */
        val canon: List<BibleBook> = listOf(
        BibleBook("Gen", "Genesis", listOf("Gn", "Ge", "Gen"), Testament.OLD, 1, 50),
        BibleBook("Exod", "Exodus", listOf("Ex", "Exo", "Exod"), Testament.OLD, 2, 40),
        BibleBook("Lev", "Leviticus", listOf("Lv", "Le", "Lev"), Testament.OLD, 3, 27),
        BibleBook("Num", "Numbers", listOf("Nm", "Nu", "Num"), Testament.OLD, 4, 36),
        BibleBook("Deut", "Deuteronomy", listOf("Dt", "De", "Deu", "Deut"), Testament.OLD, 5, 34),
        BibleBook("Josh", "Joshua", listOf("Jos", "Josh"), Testament.OLD, 6, 24),
        BibleBook("Judg", "Judges", listOf("Jdg", "Jgs", "Judg"), Testament.OLD, 7, 21),
        BibleBook("Ruth", "Ruth", listOf("Ru", "Rth"), Testament.OLD, 8, 4),
        BibleBook("1Sam", "1 Samuel", listOf("1 Sam", "1 Sm", "1Sam", "1Sm", "I Samuel"), Testament.OLD, 9, 31),
        BibleBook("2Sam", "2 Samuel", listOf("2 Sam", "2 Sm", "2Sam", "2Sm", "II Samuel"), Testament.OLD, 10, 24),
        BibleBook("1Kgs", "1 Kings", listOf("1 Kgs", "1 Ki", "1Kgs", "1Ki", "I Kings"), Testament.OLD, 11, 22),
        BibleBook("2Kgs", "2 Kings", listOf("2 Kgs", "2 Ki", "2Kgs", "2Ki", "II Kings"), Testament.OLD, 12, 25),
        BibleBook("1Chr", "1 Chronicles", listOf("1 Chr", "1 Ch", "1Chr", "1Ch", "I Chronicles"), Testament.OLD, 13, 29),
        BibleBook("2Chr", "2 Chronicles", listOf("2 Chr", "2 Ch", "2Chr", "2Ch", "II Chronicles"), Testament.OLD, 14, 36),
        BibleBook("Ezra", "Ezra", listOf("Ezr"), Testament.OLD, 15, 10),
        BibleBook("Neh", "Nehemiah", listOf("Ne", "Neh"), Testament.OLD, 16, 13),
        BibleBook("Esth", "Esther", listOf("Es", "Est", "Esth"), Testament.OLD, 17, 10),
        BibleBook("Job", "Job", listOf("Jb"), Testament.OLD, 18, 42),
        BibleBook("Ps", "Psalms", listOf("Ps", "Psa", "Psalm", "Pss"), Testament.OLD, 19, 150),
        BibleBook("Prov", "Proverbs", listOf("Pr", "Prv", "Pro", "Prov"), Testament.OLD, 20, 31),
        BibleBook("Eccl", "Ecclesiastes", listOf("Ec", "Ecc", "Eccl", "Qoheleth"), Testament.OLD, 21, 12),
        BibleBook("Song", "Song of Solomon", listOf("Sg", "Song", "Song of Songs", "SoS", "Canticles"), Testament.OLD, 22, 8),
        BibleBook("Isa", "Isaiah", listOf("Is", "Isa"), Testament.OLD, 23, 66),
        BibleBook("Jer", "Jeremiah", listOf("Je", "Jer"), Testament.OLD, 24, 52),
        BibleBook("Lam", "Lamentations", listOf("La", "Lam"), Testament.OLD, 25, 5),
        BibleBook("Ezek", "Ezekiel", listOf("Eze", "Ezk", "Ezek"), Testament.OLD, 26, 48),
        BibleBook("Dan", "Daniel", listOf("Da", "Dn", "Dan"), Testament.OLD, 27, 12),
        BibleBook("Hos", "Hosea", listOf("Ho", "Hos"), Testament.OLD, 28, 14),
        BibleBook("Joel", "Joel", listOf("Jl", "Joe"), Testament.OLD, 29, 3),
        BibleBook("Amos", "Amos", listOf("Am"), Testament.OLD, 30, 9),
        BibleBook("Obad", "Obadiah", listOf("Ob", "Oba", "Obad"), Testament.OLD, 31, 1),
        BibleBook("Jonah", "Jonah", listOf("Jon", "Jnh"), Testament.OLD, 32, 4),
        BibleBook("Mic", "Micah", listOf("Mi", "Mic"), Testament.OLD, 33, 7),
        BibleBook("Nah", "Nahum", listOf("Na", "Nah"), Testament.OLD, 34, 3),
        BibleBook("Hab", "Habakkuk", listOf("Hb", "Hab"), Testament.OLD, 35, 3),
        BibleBook("Zeph", "Zephaniah", listOf("Zp", "Zep", "Zeph"), Testament.OLD, 36, 3),
        BibleBook("Hag", "Haggai", listOf("Hg", "Hag"), Testament.OLD, 37, 2),
        BibleBook("Zech", "Zechariah", listOf("Zc", "Zec", "Zech"), Testament.OLD, 38, 14),
        BibleBook("Mal", "Malachi", listOf("Ml", "Mal"), Testament.OLD, 39, 4),
        BibleBook("Matt", "Matthew", listOf("Mt", "Mat", "Matt"), Testament.NEW, 40, 28),
        BibleBook("Mark", "Mark", listOf("Mk", "Mr", "Mrk"), Testament.NEW, 41, 16),
        BibleBook("Luke", "Luke", listOf("Lk", "Lu", "Luk"), Testament.NEW, 42, 24),
        BibleBook("John", "John", listOf("Jn", "Jhn", "Joh"), Testament.NEW, 43, 21),
        BibleBook("Acts", "Acts", listOf("Ac", "Act"), Testament.NEW, 44, 28),
        BibleBook("Rom", "Romans", listOf("Ro", "Rm", "Rom"), Testament.NEW, 45, 16),
        BibleBook("1Cor", "1 Corinthians", listOf("1 Cor", "1 Co", "1Cor", "1Co", "I Corinthians"), Testament.NEW, 46, 16),
        BibleBook("2Cor", "2 Corinthians", listOf("2 Cor", "2 Co", "2Cor", "2Co", "II Corinthians"), Testament.NEW, 47, 13),
        BibleBook("Gal", "Galatians", listOf("Ga", "Gal"), Testament.NEW, 48, 6),
        BibleBook("Eph", "Ephesians", listOf("Ep", "Eph"), Testament.NEW, 49, 6),
        BibleBook("Phil", "Philippians", listOf("Php", "Phil", "Philip"), Testament.NEW, 50, 4),
        BibleBook("Col", "Colossians", listOf("Co", "Col"), Testament.NEW, 51, 4),
        BibleBook("1Thess", "1 Thessalonians", listOf("1 Thess", "1 Th", "1Thess", "1Th", "I Thessalonians"), Testament.NEW, 52, 5),
        BibleBook("2Thess", "2 Thessalonians", listOf("2 Thess", "2 Th", "2Thess", "2Th", "II Thessalonians"), Testament.NEW, 53, 3),
        BibleBook("1Tim", "1 Timothy", listOf("1 Tim", "1 Ti", "1Tim", "1Ti", "I Timothy"), Testament.NEW, 54, 6),
        BibleBook("2Tim", "2 Timothy", listOf("2 Tim", "2 Ti", "2Tim", "2Ti", "II Timothy"), Testament.NEW, 55, 4),
        BibleBook("Titus", "Titus", listOf("Tit"), Testament.NEW, 56, 3),
        BibleBook("Phlm", "Philemon", listOf("Phm", "Phlm", "Philem"), Testament.NEW, 57, 1),
        BibleBook("Heb", "Hebrews", listOf("He", "Heb"), Testament.NEW, 58, 13),
        BibleBook("Jas", "James", listOf("Ja", "Jm", "Jas"), Testament.NEW, 59, 5),
        BibleBook("1Pet", "1 Peter", listOf("1 Pet", "1 Pe", "1Pet", "1Pe", "I Peter"), Testament.NEW, 60, 5),
        BibleBook("2Pet", "2 Peter", listOf("2 Pet", "2 Pe", "2Pet", "2Pe", "II Peter"), Testament.NEW, 61, 3),
        BibleBook("1John", "1 John", listOf("1 Jn", "1 Jo", "1John", "1Jn", "I John"), Testament.NEW, 62, 5),
        BibleBook("2John", "2 John", listOf("2 Jn", "2 Jo", "2John", "2Jn", "II John"), Testament.NEW, 63, 1),
        BibleBook("3John", "3 John", listOf("3 Jn", "3 Jo", "3John", "3Jn", "III John"), Testament.NEW, 64, 1),
        BibleBook("Jude", "Jude", listOf("Jud", "Jde"), Testament.NEW, 65, 1),
        BibleBook("Rev", "Revelation", listOf("Re", "Rv", "Rev", "Apocalypse"), Testament.NEW, 66, 22),
        )

        private val canonById: Map<BookId, BibleBook> = canon.associateBy { it.id }
    }
}
