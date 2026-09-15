package com.nexussoft.verbum.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.nexussoft.verbum.clients.AccountClient
import com.nexussoft.verbum.models.AccountException
import com.nexussoft.verbum.models.AccountFailure
import com.nexussoft.verbum.models.AuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Android-only Firebase adapter; domain and feature modules have no SDK dependency. */
class FirebaseAccountClient : AccountClient {
    private fun auth(): FirebaseAuth = try {
        FirebaseAuth.getInstance().also { it.useAppLanguage() }
    } catch (_: IllegalStateException) { throw AccountException(AccountFailure.configuration) }

    override fun sessions(): Flow<AuthSession?> = callbackFlow {
        val auth = try { auth() } catch (_: AccountException) { trySend(null); close(); return@callbackFlow }
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.snapshot()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }
    override suspend fun signIn(email: String, password: String) = mapped {
        requireNotNull(auth().signInWithEmailAndPassword(email, password).await().user).snapshot()
    }
    override suspend fun register(email: String, password: String) = mapped {
        val auth = auth()
        val guest = auth.currentUser?.takeIf { it.isAnonymous }
        val result = if (guest != null) guest.linkWithCredential(EmailAuthProvider.getCredential(email, password)).await()
            else auth.createUserWithEmailAndPassword(email, password).await()
        requireNotNull(result.user).snapshot()
    }
    override suspend fun anonymous() = mapped {
        FirebaseApiTokens.token(createIfNeeded = true)
        val auth = auth()
        requireNotNull(auth.currentUser).snapshot()
    }
    override suspend fun resetPassword(email: String) {
        try { mapped { auth().sendPasswordResetEmail(email).await(); Unit } }
        catch (error: AccountException) {
            // Neutral success regardless of whether the address exists.
            if (error.failure != AccountFailure.credentials) throw error
        }
    }
    override suspend fun sendVerification() = mapped {
        val user = auth().currentUser ?: throw AccountException(AccountFailure.credentials)
        user.sendEmailVerification().await(); Unit
    }
    override suspend fun refresh(): AuthSession? = mapped {
        auth().currentUser?.let { it.reload().await(); it.snapshot() }
    }
    override suspend fun signOut() = mapped { auth().signOut() }
    override suspend fun deleteAccount(password: String) = mapped {
        val user = auth().currentUser ?: throw AccountException(AccountFailure.credentials)
        if (!user.isAnonymous) {
            if (password.isEmpty()) throw AccountException(AccountFailure.passwordRequired)
            val email = user.email ?: throw AccountException(AccountFailure.credentials)
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        }
        user.delete().await(); Unit
    }
}
private fun FirebaseUser.snapshot() = AuthSession(uid, email, isAnonymous, isEmailVerified)
private suspend fun <T> mapped(block: suspend () -> T): T = try { block() } catch (error: Exception) {
    if (error is CancellationException) throw error
    if (error is AccountException) throw error
    val failure = when (error) {
        is FirebaseNetworkException -> AccountFailure.network
        is FirebaseTooManyRequestsException -> AccountFailure.tooManyRequests
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_INVALID_EMAIL" -> AccountFailure.invalidEmail
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS", "ERROR_USER_NOT_FOUND" -> AccountFailure.credentials
            "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_CREDENTIAL_ALREADY_IN_USE" -> AccountFailure.emailInUse
            "ERROR_WEAK_PASSWORD" -> AccountFailure.passwordTooShort
            "ERROR_USER_DISABLED" -> AccountFailure.disabled
            "ERROR_OPERATION_NOT_ALLOWED", "ERROR_INVALID_API_KEY", "ERROR_APP_NOT_AUTHORIZED", "ERROR_CONFIGURATION_NOT_FOUND" -> AccountFailure.configuration
            "ERROR_REQUIRES_RECENT_LOGIN" -> AccountFailure.recentLoginRequired
            else -> AccountFailure.unexpected
        }
        else -> AccountFailure.unexpected
    }
    throw AccountException(failure)
}
