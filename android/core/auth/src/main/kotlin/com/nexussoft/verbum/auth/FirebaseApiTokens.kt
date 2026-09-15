package com.nexussoft.verbum.auth

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** Firebase owns persistence and token refresh. Public browsing never creates a user. */
object FirebaseApiTokens {
    private val signIn = Mutex()

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
