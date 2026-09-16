package com.nexussoft.verbum.auth

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

object FirebaseAppAttestation {
    fun configure() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
    suspend fun token(): String? = try { FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) { null } // Backend rollout controls enforcement; no client-side bypass of it.
}
