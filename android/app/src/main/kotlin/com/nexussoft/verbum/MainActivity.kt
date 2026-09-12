package com.nexussoft.verbum

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.nexussoft.verbum.designsystem.VerbumTheme

class MainActivity : ComponentActivity() {
    /** The POST_NOTIFICATIONS dialog; answered through [NotificationPermission]. */
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        NotificationPermission.complete(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationPermission.launcher = notificationPermission
        if (savedInstanceState == null) openVerse(intent)
        setContent {
            VerbumTheme {
                RootScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openVerse(intent)
    }

    override fun onDestroy() {
        if (NotificationPermission.launcher === notificationPermission) NotificationPermission.launcher = null
        super.onDestroy()
    }

    /** A tapped verse notification carries its reference; hand it to the shell. */
    private fun openVerse(intent: Intent?) {
        VerseTapRelay.reference(intent)?.let { VerseTapRelay.offer(it) }
    }
}
