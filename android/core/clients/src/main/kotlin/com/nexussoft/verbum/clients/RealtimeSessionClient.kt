package com.nexussoft.verbum.clients

/**
 * The short-lived credential a voice session starts with (`POST /v1/realtime/session`). It is
 * OpenAI's ephemeral client secret, minted by our backend so the real key never ships in the
 * app (§56). Never logged, never persisted; it can only *start* a session until [expiresAt].
 */
data class RealtimeSession(val clientSecret: String, /** Unix seconds. */ val expiresAt: Long, val model: String)

/** Throws [VoiceException]. */
fun interface RealtimeSessionClient {
    suspend fun create(): RealtimeSession
}
