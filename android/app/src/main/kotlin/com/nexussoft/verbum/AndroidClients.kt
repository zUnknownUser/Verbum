package com.nexussoft.verbum

import android.content.Context
import com.nexussoft.verbum.clients.PreferencesClient

/** Live [PreferencesClient] over SharedPreferences. */
class SharedPreferencesClient(context: Context) : PreferencesClient {
    private val prefs = context.getSharedPreferences("verbum", Context.MODE_PRIVATE)
    override fun string(key: String): String? = prefs.getString(key, null)
    override fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
