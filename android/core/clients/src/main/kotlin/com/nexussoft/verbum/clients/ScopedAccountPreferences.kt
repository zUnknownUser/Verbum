package com.nexussoft.verbum.clients

/** Immutable account binding: an old request cannot write into the next account. */
class ScopedAccountPreferences(private val global: PreferencesClient, private val personal: PreferencesClient, private val scope: String) : PreferencesClient {
    companion object {
        private val keys = setOf("readerAnnotations", "readingActivity", "lastRead")
        private val lock = Any()
        fun scope(uid: String?, generation: String = "initial"): String = java.security.MessageDigest.getInstance("SHA-256")
            .digest((uid?.let { "uid:$it" } ?: "local-guest:$generation").toByteArray()).joinToString("") { "%02x".format(it) }
        fun prepare(global: PreferencesClient, personal: PreferencesClient) = synchronized(lock) {
            if (global.string("accountsMigrated") != "true") {
                keys.forEach { key -> global.string(key)?.takeIf { it.isNotEmpty() }?.let { if(personal.string(key).isNullOrEmpty()) personal.setString(key,it) } }
                global.setString("accountsMigrated","true")
                keys.forEach { global.setString(it, "") }
            }
        }
        fun promote(global: PreferencesClient, guest: PreferencesClient, destination: PreferencesClient, oldScope: String) = synchronized(lock) {
            keys.forEach { key -> guest.string(key)?.takeIf { it.isNotEmpty() }?.let { if(destination.string(key).isNullOrEmpty()) destination.setString(key,it) } }
            global.setString("deleted.$oldScope", "true")
            global.setString("guestGeneration", java.util.UUID.randomUUID().toString())
            keys.forEach { guest.setString(it, "") }
        }
        fun delete(global: PreferencesClient, personal: PreferencesClient, scope: String) = synchronized(lock) {
            global.setString("deleted.$scope","true")
            keys.forEach { personal.setString(it, "") }
        }
    }
    override fun string(key: String): String? = synchronized(lock) {
        if(key in keys) personal.string(key)?.takeIf { it.isNotEmpty() } else global.string(key)
    }
    override fun setString(key: String,value: String) = synchronized(lock) {
        if(key in keys) check(global.string("deleted.$scope") != "true") { "Account deleted" }
        (if(key in keys) personal else global).setString(key,value)
    }
}
