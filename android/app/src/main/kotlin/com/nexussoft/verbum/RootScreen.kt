package com.nexussoft.verbum

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexussoft.verbum.designsystem.VerbumTheme
import com.nexussoft.verbum.feature.scripture.ProfileFeature
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexussoft.verbum.auth.FirebaseAccountClient
import com.nexussoft.verbum.auth.FirebaseApiTokens
import com.nexussoft.verbum.feature.scripture.AccountViewModel
import com.nexussoft.verbum.feature.scripture.ui.AccountHost
import com.nexussoft.verbum.clients.api.LiveAskScriptureClient
import com.nexussoft.verbum.clients.api.LiveContextClient
import com.nexussoft.verbum.clients.api.LiveRealtimeSessionClient
import com.nexussoft.verbum.clients.voice.OkHttpRealtimeTransport
import com.nexussoft.verbum.clients.voice.RealtimeConversation
import com.nexussoft.verbum.audio.AndroidVoiceAudio
import com.nexussoft.verbum.clients.api.LiveGraphClient
import com.nexussoft.verbum.clients.api.LiveSearchClient
import com.nexussoft.verbum.clients.api.LiveTimelineClient
import com.nexussoft.verbum.clients.api.ResponseCache
import com.nexussoft.verbum.clients.api.VerbumApi
import com.nexussoft.verbum.clients.helloao.ChapterCache
import com.nexussoft.verbum.audio.Media3AudioPlayerClient
import com.nexussoft.verbum.clients.helloao.HelloAOBibleClient
import com.nexussoft.verbum.audio.LiveScriptureAudioClient
import com.nexussoft.verbum.clients.helloao.HelloAOTranslation
import com.nexussoft.verbum.clients.helloao.LiveBibleClient
import com.nexussoft.verbum.feature.scripture.AppFeature
import com.nexussoft.verbum.feature.scripture.AppViewModel
import com.nexussoft.verbum.feature.scripture.ReaderTextScale
import com.nexussoft.verbum.feature.scripture.ui.AppScreen
import com.nexussoft.verbum.models.BookLanguage
import java.io.File

/**
 * The app shell (docs/PRODUCT.md §6). Dependencies are assembled here, explicitly:
 * Scripture from bible.helloao.org (cached, WEB offline fallback); everything else from the Verbum
 * backend (`BuildConfig.VERBUM_API_BASE_URL`, cached on disk for offline re-reading).
 */
private data class LocalIdentity(val uid: String?)

@Composable
fun RootScreen() {
    val context = LocalContext.current.applicationContext
    val accountViewModel: AccountViewModel = viewModel { AccountViewModel(FirebaseAccountClient(onDeleted = { AccountPreferencesClient.delete(context,it) }, onRegistered = { AccountPreferencesClient.promote(context,it) })) }
    var retry by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val identity by produceState<LocalIdentity?>(null, retry) {
        FirebaseAccountClient().sessions().collect {
            val uid = it?.takeUnless { session -> session.isAnonymous }?.id
            if(value == null) {
                try { AccountPreferencesClient.prepare(context,uid) }
                catch(_: Exception) { failed = true; return@collect }
            }
            value = LocalIdentity(uid)
        }
    }
    if(identity == null) {
        if(failed) Button(onClick = { failed = false; retry++ }) {
            Text(if(BookLanguage.current == BookLanguage.PORTUGUESE) "Não foi possível abrir seus dados. Tentar novamente" else "Could not open saved data. Try again")
        } else CircularProgressIndicator()
        return
    }
    val uid = identity!!.uid
    val owner = remember(uid) { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { AccountRootScreen(uid, accountViewModel) }
}
@Composable
private fun AccountRootScreen(uid: String?, accountViewModel: AccountViewModel) {
    val context = LocalContext.current.applicationContext
    val viewModel: AppViewModel = viewModel {
        val preferences = AccountPreferencesClient(context, uid)
        val installation = preferences.string("verbumInstallation") ?: java.util.UUID.randomUUID().toString().also { preferences.setString("verbumInstallation", it) }
        val api = VerbumApi(BuildConfig.VERBUM_API_BASE_URL, cache = ResponseCache(File(context.cacheDir, "verbum-api")), tokenProvider = FirebaseApiTokens::token, installationId = installation, appCheckProvider = com.nexussoft.verbum.auth.FirebaseAppAttestation::token)
        val bible = LiveBibleClient(
            language = BookLanguage.current,
            remote = HelloAOBibleClient(HelloAOTranslation.id(BookLanguage.current), cache = ChapterCache(File(context.cacheDir, "scripture"))),
        )
        AppViewModel(
            AppFeature.Dependencies(
                usageStatus = { api.usageStatus() },
                bibleClient = bible,
                preferences = preferences,
                searchClient = LiveSearchClient(api),
                graphClient = LiveGraphClient(api),
                contextClient = LiveContextClient(api),
                timelineClient = LiveTimelineClient(api),
                askClient = LiveAskScriptureClient(api),
                realtimeSessionClient = LiveRealtimeSessionClient(api),
                voiceClient = RealtimeConversation(OkHttpRealtimeTransport(), AndroidVoiceAudio(context) { MicrophonePermission.request() }),
                // Portuguese: the backend's Google Cloud voice reads the translation on screen; English: helloao recordings.
                audioClient = LiveScriptureAudioClient(BookLanguage.current, context, bible, api),
                player = Media3AudioPlayerClient(context),
                initialTextScale = { ReaderTextScale.fromPreference(preferences.string(ReaderTextScale.PREFERENCE_KEY)) },
                notifications = AndroidNotificationClient(context),
                dailyVerseTitle = { context.getString(R.string.notification_channel_daily_verse) },
            ),
        )
    }
    val appState by viewModel.store.state.collectAsStateWithLifecycle()
    val dark = when(appState.profile.appearance) {
        ProfileFeature.Appearance.AUTOMATIC -> isSystemInDarkTheme()
        ProfileFeature.Appearance.DARK -> true
        ProfileFeature.Appearance.LIGHT -> false
    }
    VerbumTheme(darkTheme = dark) {
        AccountHost(accountViewModel.store, viewModel.store) { AppScreen(viewModel.store) }
    }
}
