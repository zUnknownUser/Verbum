package com.nexussoft.verbum.models

/** Spec §22.5. How sure the dating is; the timeline shows this, never a bare year. */
enum class TimelineDatePrecision(val wireValue: String) {
    EXACT("exact"),
    APPROXIMATE("approximate"),
    DEBATED("debated"),
    UNKNOWN("unknown");

    companion object {
        fun fromWireValue(value: String): TimelineDatePrecision? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * Spec §22.5. A period or event on the timeline. Years are astronomical-style integers:
 * negative for BC (`-1010` = 1010 BC), positive for AD; `null` when unknown. `endYear == null`
 * with a `startYear` means a point in time. `sourceReferenceIds` is added to the spec's shape so
 * dating claims stay traceable (§33), like relationships. Twin of iOS `TimelineEvent`.
 */
data class TimelineEvent(
    val id: String,
    val title: String,
    val startYear: Int?,
    val endYear: Int?,
    val datePrecision: TimelineDatePrecision,
    val summary: String?,
    /** Graph entities this event is about (people, places, events, themes). */
    val entityIds: List<String>,
    val sourceReferenceIds: List<String> = emptyList(),
) {
    /** A span rather than a moment. */
    val isPeriod: Boolean get() = startYear != null && endYear != null && startYear != endYear
}
