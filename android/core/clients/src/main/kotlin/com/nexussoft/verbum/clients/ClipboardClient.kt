package com.nexussoft.verbum.clients

/** Writes to the system clipboard. An interface so features never touch Android APIs and tests can assert what was copied. */
fun interface ClipboardClient {
    fun copy(text: String)
}

/** Fixture: records the last copy. */
class RecordingClipboardClient : ClipboardClient {
    var lastCopied: String? = null
        private set

    override fun copy(text: String) {
        lastCopied = text
    }
}
