package com.nexussoft.verbum

import android.Manifest
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.CompletableDeferred

/** The RECORD_AUDIO runtime prompt, awaited by the voice audio client. Same shape as [NotificationPermission]. */
object MicrophonePermission {
    var launcher: ActivityResultLauncher<String>? = null
    private var pending: CompletableDeferred<Boolean>? = null

    suspend fun request(): Boolean {
        val launcher = launcher ?: return false
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        launcher.launch(Manifest.permission.RECORD_AUDIO)
        return deferred.await()
    }

    fun complete(granted: Boolean) {
        pending?.complete(granted)
        pending = null
    }
}
