package com.nexussoft.verbum

import android.content.Context
import com.nexussoft.verbum.clients.PreferencesClient

/** Live [PreferencesClient] over SharedPreferences. */
class SharedPreferencesClient(context: Context, name: String = "verbum") : PreferencesClient {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun string(key: String): String? = prefs.getString(key, null)
    override fun setString(key: String, value: String) {
        check(prefs.edit().putString(key, value).commit()) { "Could not persist local data" }
    }
}

/** Android storage adapters; account ownership and migration are tested in core clients. */
class AccountPreferencesClient(context: Context, uid: String?) : PreferencesClient by
    com.nexussoft.verbum.clients.ScopedAccountPreferences(SharedPreferencesClient(context), personal(context,uid), scope(context,uid)) {
    companion object {
        private fun scope(context: Context, uid: String?) = com.nexussoft.verbum.clients.ScopedAccountPreferences.scope(uid,SharedPreferencesClient(context).string("guestGeneration") ?: "initial")
        fun promote(context: Context, uid: String) = com.nexussoft.verbum.clients.ScopedAccountPreferences.promote(SharedPreferencesClient(context),personal(context,null),personal(context,uid),scope(context,null))
        private fun personal(context: Context, uid: String?) = SharedPreferencesClient(context,"account."+scope(context,uid))
        fun prepare(context: Context, uid: String?) {
            val global = SharedPreferencesClient(context)
            val owner = global.string("migrationOwner") ?: scope(context,uid).also { global.setString("migrationOwner",it) }
            com.nexussoft.verbum.clients.ScopedAccountPreferences.prepare(global,SharedPreferencesClient(context,"account.$owner"))
        }
        fun delete(context: Context, uid: String) = com.nexussoft.verbum.clients.ScopedAccountPreferences.delete(SharedPreferencesClient(context),personal(context,uid),scope(context,uid))
    }
}
