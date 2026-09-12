package com.nexussoft.verbum.clients

/**
 * Small key/value preferences (reader text size and the like). Mirrors the iOS
 * `@Shared(.appStorage)` role: features read on start and write on change.
 */
interface PreferencesClient {
    fun string(key: String): String?
    fun setString(key: String, value: String)
}

/** Fixture / preview implementation. */
class InMemoryPreferencesClient(initial: Map<String, String> = emptyMap()) : PreferencesClient {
    private val values = initial.toMutableMap()
    override fun string(key: String): String? = values[key]
    override fun setString(key: String, value: String) {
        values[key] = value
    }
}
