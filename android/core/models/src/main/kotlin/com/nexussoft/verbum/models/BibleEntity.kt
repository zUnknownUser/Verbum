package com.nexussoft.verbum.models

/** docs/PRODUCT.md §22.1. `wireValue` is the string contract shared with iOS and the backend. */
enum class BibleEntityType(val wireValue: String) {
    PERSON("person"),
    PLACE("place"),
    EVENT("event"),
    THEME("theme"),
    PASSAGE("passage"),
    BOOK("book"),
    PROPHECY("prophecy"),
    ORIGINAL_TERM("originalTerm"),
    HISTORICAL_PERIOD("historicalPeriod");

    companion object {
        fun fromWireValue(value: String): BibleEntityType? = entries.firstOrNull { it.wireValue == value }
    }
}

/** docs/PRODUCT.md §22.1. */
data class BibleEntity(
    val id: String,
    val type: BibleEntityType,
    val name: String,
    val summary: String?,
)
