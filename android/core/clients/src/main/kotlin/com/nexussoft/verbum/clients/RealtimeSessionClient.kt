package com.nexussoft.verbum.clients

/**
 * The short-lived credential a voice session starts with (`POST /v1/realtime/session`). It is
 * a one-use Verbum relay ticket; the provider key stays on the backend. Never logged, never persisted; it can only *start* a session until [expiresAt].
 */
data class RealtimeSession(val clientSecret: String, /** Unix seconds. */ val expiresAt: Long, val model: String, val relayUrl: String? = null, val maxDurationSeconds: Int? = null)

/** Throws [VoiceException]. */
fun interface RealtimeSessionClient {
    suspend fun create(): RealtimeSession
}
