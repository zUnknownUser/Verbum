package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.AuthSession
import kotlinx.coroutines.flow.Flow

/** Credentials belong to the SDK, never preferences or saved UI state. */
interface AccountClient {
    fun sessions(): Flow<AuthSession?>
    suspend fun signIn(email: String, password: String): AuthSession
    suspend fun register(email: String, password: String): AuthSession
    suspend fun anonymous(): AuthSession
    suspend fun resetPassword(email: String)
    suspend fun sendVerification()
    suspend fun refresh(): AuthSession?
    suspend fun signOut()
    suspend fun deleteAccount(password: String)
}
