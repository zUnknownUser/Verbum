package com.nexussoft.verbum.feature.scripture

/** User-chosen Scripture size, multiplied on top of system font scaling (docs/PRODUCT.md §42). */
enum class ReaderTextScale(val factor: Float, val titleRes: Int) {
    SMALL(0.85f, R.string.size_small),
    STANDARD(1.0f, R.string.size_default),
    LARGE(1.15f, R.string.size_large),
    EXTRA_LARGE(1.3f, R.string.size_extra_large);

    companion object {
        const val PREFERENCE_KEY = "readerTextScale"
        fun fromPreference(value: String?): ReaderTextScale = entries.firstOrNull { it.name == value } ?: STANDARD
    }
}
