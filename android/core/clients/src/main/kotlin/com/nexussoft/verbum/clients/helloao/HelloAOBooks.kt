package com.nexussoft.verbum.clients.helloao

import com.nexussoft.verbum.models.BookId
import com.nexussoft.verbum.models.BookLanguage

/** bible.helloao.org addresses books by USFM code; the app uses OSIS ids. Mirrors iOS. */
internal object HelloAOBooks {
    val usfmByOsis: Map<BookId, String> = mapOf(
        "Gen" to "GEN", "Exod" to "EXO", "Lev" to "LEV", "Num" to "NUM", "Deut" to "DEU", "Josh" to "JOS", "Judg" to "JDG", "Ruth" to "RUT",
        "1Sam" to "1SA", "2Sam" to "2SA", "1Kgs" to "1KI", "2Kgs" to "2KI", "1Chr" to "1CH", "2Chr" to "2CH", "Ezra" to "EZR", "Neh" to "NEH",
        "Esth" to "EST", "Job" to "JOB", "Ps" to "PSA", "Prov" to "PRO", "Eccl" to "ECC", "Song" to "SNG", "Isa" to "ISA", "Jer" to "JER",
        "Lam" to "LAM", "Ezek" to "EZK", "Dan" to "DAN", "Hos" to "HOS", "Joel" to "JOL", "Amos" to "AMO", "Obad" to "OBA", "Jonah" to "JON",
        "Mic" to "MIC", "Nah" to "NAM", "Hab" to "HAB", "Zeph" to "ZEP", "Hag" to "HAG", "Zech" to "ZEC", "Mal" to "MAL",
        "Matt" to "MAT", "Mark" to "MRK", "Luke" to "LUK", "John" to "JHN", "Acts" to "ACT", "Rom" to "ROM", "1Cor" to "1CO", "2Cor" to "2CO",
        "Gal" to "GAL", "Eph" to "EPH", "Phil" to "PHP", "Col" to "COL", "1Thess" to "1TH", "2Thess" to "2TH", "1Tim" to "1TI", "2Tim" to "2TI",
        "Titus" to "TIT", "Phlm" to "PHM", "Heb" to "HEB", "Jas" to "JAS", "1Pet" to "1PE", "2Pet" to "2PE", "1John" to "1JN", "2John" to "2JN",
        "3John" to "3JN", "Jude" to "JUD", "Rev" to "REV",
    )
    val osisByUsfm: Map<String, BookId> = usfmByOsis.entries.associate { (k, v) -> v to k }
}

/** Which helloao translation to read, by device language. All open-licensed. */
object HelloAOTranslation {
    fun id(language: BookLanguage): String = when (language) {
        BookLanguage.ENGLISH -> "BSB"
        BookLanguage.PORTUGUESE -> "por_blj"
    }

    fun name(id: String): String = when (id) {
        "BSB" -> "Berean Standard Bible"
        "por_blj" -> "Bíblia Livre"
        "web" -> "World English Bible"
        else -> id
    }
}
