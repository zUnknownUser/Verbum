package com.nexussoft.verbum

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.nexussoft.verbum.clients.ClipboardClient
import com.nexussoft.verbum.clients.PreferencesClient

/** Live [ClipboardClient] over the system clipboard. */
class AndroidClipboardClient(context: Context) : ClipboardClient {
    private val manager = context.getSystemService(ClipboardManager::class.java)
    override fun copy(text: String) {
        manager.setPrimaryClip(ClipData.newPlainText("Scripture", text))
    }
}

/** Live [PreferencesClient] over SharedPreferences. */
class SharedPreferencesClient(context: Context) : PreferencesClient {
    private val prefs = context.getSharedPreferences("verbum", Context.MODE_PRIVATE)
    override fun string(key: String): String? = prefs.getString(key, null)
    override fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
