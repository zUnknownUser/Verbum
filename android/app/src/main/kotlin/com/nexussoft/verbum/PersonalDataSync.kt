package com.nexussoft.verbum

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nexussoft.verbum.auth.FirebaseApiTokens
import com.nexussoft.verbum.auth.FirebaseAppAttestation
import com.nexussoft.verbum.clients.api.VerbumApi
import com.nexussoft.verbum.clients.sync.PersonalDataStorage
import com.nexussoft.verbum.clients.sync.PersonalSyncClient
import com.nexussoft.verbum.feature.scripture.ui.LocalPersonalSync
import com.nexussoft.verbum.feature.scripture.ui.PersonalSyncPresentation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun active(uid: String) = FirebaseApiTokens.isRegistered(uid)
internal fun personalApi(uid: String) = VerbumApi(BuildConfig.VERBUM_API_BASE_URL,
    tokenProvider = { FirebaseApiTokens.tokenForAccount(uid) }, appCheckProvider=FirebaseAppAttestation::token)

@Composable internal fun PersonalDataSync(uid: String?, content: @Composable () -> Unit) {
    val context=LocalContext.current.applicationContext
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var presentation by remember(uid) { mutableStateOf(PersonalSyncPresentation()) }
    LaunchedEffect(uid,lifecycle) {
        if(uid==null) return@LaunchedEffect
        val preferences=AccountPreferencesClient(context,uid)
        val storage=PersonalDataStorage(preferences)
        val api=personalApi(uid)
        val sync=PersonalSyncClient(preferences,storage::snapshot,storage::apply,api::syncPersonalData) { active(uid) }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var wait=5_000L
            while(active(uid)) {
                presentation=presentation.copy(status=PersonalSyncPresentation.Status.SYNCING)
                try {
                    val complete=withContext(Dispatchers.IO) { sync.sync() }
                    presentation=PersonalSyncPresentation(if(complete) PersonalSyncPresentation.Status.SYNCED else PersonalSyncPresentation.Status.PENDING,presentation.revision+1)
                    wait=if(complete) 30_000L else 5_000L
                } catch(error: CancellationException) { throw error }
                catch(_: Exception) { presentation=presentation.copy(status=PersonalSyncPresentation.Status.PENDING);wait=(wait*2).coerceAtMost(120_000) }
                delay(wait)
            }
        }
    }
    CompositionLocalProvider(LocalPersonalSync provides presentation) { content() }
}
