package com.nexussoft.verbum.models

import java.util.Locale

/** A book's name and accepted abbreviations in one language. */
data class LocalizedBookName(val name: String, val abbreviations: List<String>)

/**
 * Which naming table applies to a locale. English is the base ([BibleBook.name] and
 * [BibleBook.abbreviations]); other languages overlay it. Any Portuguese locale gets the
 * Brazilian table. Mirrors iOS `BookLanguage`.
 */
enum class BookLanguage(val tag: String) {
    ENGLISH("en"),
    PORTUGUESE("pt");

    companion object {
        fun of(locale: Locale): BookLanguage = if (locale.language == "pt") PORTUGUESE else ENGLISH
        val current: BookLanguage get() = of(Locale.getDefault())
    }
}

/** Display name in the device language (`João`, `1 Samuel`, `Gênesis`). */
val BibleBook.localizedName: String get() = localizedName(BookLanguage.current)

fun BibleBook.localizedName(language: BookLanguage): String =
    LocalizedBookNames.tables[language.tag]?.get(id)?.name ?: name

/** Abbreviations in that language, in addition to the English ones the parser always accepts. Empty for English. */
fun BibleBook.localizedAbbreviations(language: BookLanguage): List<String> =
    LocalizedBookNames.tables[language.tag]?.get(id)?.abbreviations ?: emptyList()

val Division.localizedTitle: String get() = localizedTitle(BookLanguage.current)

fun Division.localizedTitle(language: BookLanguage): String = when (language) {
    BookLanguage.ENGLISH -> title
    BookLanguage.PORTUGUESE -> LocalizedBookNames.portugueseDivisions[this] ?: title
}

/** Human-readable reference in the given language. */
fun PassageReference.formatted(language: BookLanguage): String {
    val bookName = BibleBook.book(bookId)?.localizedName(language) ?: bookId
    val range = verses ?: return "$bookName $chapter"
    return if (range.first == range.last) "$bookName $chapter:${range.first}" else "$bookName $chapter:${range.first}-${range.last}"
}

/** Generated from one table shared with iOS. Brazilian Portuguese follows the Almeida convention (Gn, Êx, Sl, Jo, Ap…). */
internal object LocalizedBookNames {
    val tables: Map<String, Map<BookId, LocalizedBookName>> = mapOf(
        "pt" to mapOf(
            "Gen" to LocalizedBookName("Gênesis", listOf("Gn", "Gên", "Gen")),
            "Exod" to LocalizedBookName("Êxodo", listOf("Êx", "Ex", "Êxo")),
            "Lev" to LocalizedBookName("Levítico", listOf("Lv", "Lev")),
            "Num" to LocalizedBookName("Números", listOf("Nm", "Núm", "Num")),
            "Deut" to LocalizedBookName("Deuteronômio", listOf("Dt", "Deut")),
            "Josh" to LocalizedBookName("Josué", listOf("Js", "Jos")),
            "Judg" to LocalizedBookName("Juízes", listOf("Jz", "Juí", "Jui")),
            "Ruth" to LocalizedBookName("Rute", listOf("Rt", "Rut")),
            "1Sam" to LocalizedBookName("1 Samuel", listOf("1Sm", "1 Sm", "1 Sam")),
            "2Sam" to LocalizedBookName("2 Samuel", listOf("2Sm", "2 Sm", "2 Sam")),
            "1Kgs" to LocalizedBookName("1 Reis", listOf("1Rs", "1 Rs", "1 Reis")),
            "2Kgs" to LocalizedBookName("2 Reis", listOf("2Rs", "2 Rs", "2 Reis")),
            "1Chr" to LocalizedBookName("1 Crônicas", listOf("1Cr", "1 Cr", "1 Crô")),
            "2Chr" to LocalizedBookName("2 Crônicas", listOf("2Cr", "2 Cr", "2 Crô")),
            "Ezra" to LocalizedBookName("Esdras", listOf("Ed", "Esd")),
            "Neh" to LocalizedBookName("Neemias", listOf("Ne", "Nee")),
            "Esth" to LocalizedBookName("Ester", listOf("Et", "Est")),
            "Job" to LocalizedBookName("Jó", listOf("Jó", "Job")),
            "Ps" to LocalizedBookName("Salmos", listOf("Sl", "Sal", "Salmo")),
            "Prov" to LocalizedBookName("Provérbios", listOf("Pv", "Pr", "Prov")),
            "Eccl" to LocalizedBookName("Eclesiastes", listOf("Ec", "Ecl")),
            "Song" to LocalizedBookName("Cântico dos Cânticos", listOf("Ct", "Cânticos", "Cantares", "Cânt")),
            "Isa" to LocalizedBookName("Isaías", listOf("Is", "Isa")),
            "Jer" to LocalizedBookName("Jeremias", listOf("Jr", "Jer")),
            "Lam" to LocalizedBookName("Lamentações", listOf("Lm", "Lam")),
            "Ezek" to LocalizedBookName("Ezequiel", listOf("Ez", "Eze")),
            "Dan" to LocalizedBookName("Daniel", listOf("Dn", "Dan")),
            "Hos" to LocalizedBookName("Oseias", listOf("Os", "Ose")),
            "Joel" to LocalizedBookName("Joel", listOf("Jl")),
            "Amos" to LocalizedBookName("Amós", listOf("Am")),
            "Obad" to LocalizedBookName("Obadias", listOf("Ob", "Oba")),
            "Jonah" to LocalizedBookName("Jonas", listOf("Jn", "Jon")),
            "Mic" to LocalizedBookName("Miqueias", listOf("Mq", "Miq")),
            "Nah" to LocalizedBookName("Naum", listOf("Na")),
            "Hab" to LocalizedBookName("Habacuque", listOf("Hc", "Hab")),
            "Zeph" to LocalizedBookName("Sofonias", listOf("Sf", "Sof")),
            "Hag" to LocalizedBookName("Ageu", listOf("Ag")),
            "Zech" to LocalizedBookName("Zacarias", listOf("Zc", "Zac")),
            "Mal" to LocalizedBookName("Malaquias", listOf("Ml", "Mal")),
            "Matt" to LocalizedBookName("Mateus", listOf("Mt", "Mat")),
            "Mark" to LocalizedBookName("Marcos", listOf("Mc", "Mr", "Mar")),
            "Luke" to LocalizedBookName("Lucas", listOf("Lc", "Luc")),
            "John" to LocalizedBookName("João", listOf("Jo", "Joao")),
            "Acts" to LocalizedBookName("Atos", listOf("At", "Atos")),
            "Rom" to LocalizedBookName("Romanos", listOf("Rm", "Ro", "Rom")),
            "1Cor" to LocalizedBookName("1 Coríntios", listOf("1Co", "1 Co", "1 Cor")),
            "2Cor" to LocalizedBookName("2 Coríntios", listOf("2Co", "2 Co", "2 Cor")),
            "Gal" to LocalizedBookName("Gálatas", listOf("Gl", "Gál")),
            "Eph" to LocalizedBookName("Efésios", listOf("Ef", "Efé")),
            "Phil" to LocalizedBookName("Filipenses", listOf("Fp", "Fl", "Fil")),
            "Col" to LocalizedBookName("Colossenses", listOf("Cl", "Col")),
            "1Thess" to LocalizedBookName("1 Tessalonicenses", listOf("1Ts", "1 Ts", "1 Tes")),
            "2Thess" to LocalizedBookName("2 Tessalonicenses", listOf("2Ts", "2 Ts", "2 Tes")),
            "1Tim" to LocalizedBookName("1 Timóteo", listOf("1Tm", "1 Tm", "1 Tim")),
            "2Tim" to LocalizedBookName("2 Timóteo", listOf("2Tm", "2 Tm", "2 Tim")),
            "Titus" to LocalizedBookName("Tito", listOf("Tt", "Tit")),
            "Phlm" to LocalizedBookName("Filemom", listOf("Fm", "Flm")),
            "Heb" to LocalizedBookName("Hebreus", listOf("Hb", "Heb")),
            "Jas" to LocalizedBookName("Tiago", listOf("Tg", "Tia")),
            "1Pet" to LocalizedBookName("1 Pedro", listOf("1Pe", "1 Pe", "1 Pd")),
            "2Pet" to LocalizedBookName("2 Pedro", listOf("2Pe", "2 Pe", "2 Pd")),
            "1John" to LocalizedBookName("1 João", listOf("1Jo", "1 Jo")),
            "2John" to LocalizedBookName("2 João", listOf("2Jo", "2 Jo")),
            "3John" to LocalizedBookName("3 João", listOf("3Jo", "3 Jo")),
            "Jude" to LocalizedBookName("Judas", listOf("Jd", "Jud")),
            "Rev" to LocalizedBookName("Apocalipse", listOf("Ap", "Apoc")),
        ),
    )

    val portugueseDivisions: Map<Division, String> = mapOf(
            Division.LAW to "Lei",
            Division.HISTORY to "História",
            Division.POETRY to "Poesia e Sabedoria",
            Division.PROPHETS to "Profetas",
            Division.GOSPELS_AND_ACTS to "Evangelhos e Atos",
            Division.LETTERS_OF_PAUL to "Cartas de Paulo",
            Division.GENERAL_LETTERS to "Cartas Gerais",
            Division.REVELATION to "Apocalipse",
    )
}
