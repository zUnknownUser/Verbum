package com.nexussoft.verbum.auth

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** Firebase owns persistence and token refresh. Public browsing never creates a user. */
object FirebaseApiTokens {
    private val signIn = Mutex()

    fun isRegistered(uid: String): Boolean = FirebaseAuth.getInstance().currentUser?.let { it.uid == uid && !it.isAnonymous } == true

    suspend fun tokenForAccount(uid: String): String? {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        check(user?.uid == uid) { "Account changed" }
        val token = user.getIdToken(false).await().token
        check(auth.currentUser?.uid == uid) { "Account changed" }
        return token
    }

    suspend fun token(createIfNeeded: Boolean): String? {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            if (!createIfNeeded) return null
            signIn.withLock {
                if (auth.currentUser == null) auth.signInAnonymously().await()
            }
        }
        val user = requireNotNull(auth.currentUser)
        val token = requireNotNull(user.getIdToken(false).await().token)
        check(auth.currentUser?.uid == user.uid) { "Account changed" }
        return token
    }
}
