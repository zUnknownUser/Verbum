package com.nexussoft.verbum.models

/** Traditional grouping of the Protestant canon by literary kind. Used to lay the canon out as a map: a shelf per division. */
enum class Division(val title: String, val testament: Testament) {
    LAW("Law", Testament.OLD),
    HISTORY("History", Testament.OLD),
    POETRY("Poetry & Wisdom", Testament.OLD),
    PROPHETS("Prophets", Testament.OLD),
    GOSPELS_AND_ACTS("Gospels & Acts", Testament.NEW),
    LETTERS_OF_PAUL("Letters of Paul", Testament.NEW),
    GENERAL_LETTERS("General Letters", Testament.NEW),
    REVELATION("Revelation", Testament.NEW);

    /** Books in canonical order. */
    val books: List<BibleBook> get() = BibleBook.canon.filter { it.division == this }
}

val BibleBook.division: Division
    get() = when (order) {
        in 1..5 -> Division.LAW
        in 6..17 -> Division.HISTORY
        in 18..22 -> Division.POETRY
        in 23..39 -> Division.PROPHETS
        in 40..44 -> Division.GOSPELS_AND_ACTS
        in 45..57 -> Division.LETTERS_OF_PAUL
        in 58..65 -> Division.GENERAL_LETTERS
        else -> Division.REVELATION
    }
