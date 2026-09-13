package com.nexussoft.verbum

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexussoft.verbum.clients.api.LiveContextClient
import com.nexussoft.verbum.clients.api.LiveGraphClient
import com.nexussoft.verbum.clients.api.LiveSearchClient
import com.nexussoft.verbum.clients.api.LiveTimelineClient
import com.nexussoft.verbum.clients.api.ResponseCache
import com.nexussoft.verbum.clients.api.VerbumApi
import com.nexussoft.verbum.clients.helloao.ChapterCache
import com.nexussoft.verbum.audio.Media3AudioPlayerClient
import com.nexussoft.verbum.clients.helloao.HelloAOBibleClient
import com.nexussoft.verbum.clients.helloao.HelloAOScriptureAudioClient
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
@Composable
fun RootScreen() {
    val context = LocalContext.current.applicationContext
    val viewModel: AppViewModel = viewModel {
        val preferences = SharedPreferencesClient(context)
        val api = VerbumApi(BuildConfig.VERBUM_API_BASE_URL, cache = ResponseCache(File(context.cacheDir, "verbum-api")))
        val bible = LiveBibleClient(
            language = BookLanguage.current,
            remote = HelloAOBibleClient(HelloAOTranslation.id(BookLanguage.current), cache = ChapterCache(File(context.cacheDir, "scripture"))),
        )
        AppViewModel(
            AppFeature.Dependencies(
                bibleClient = bible,
                clipboard = AndroidClipboardClient(context),
                preferences = preferences,
                searchClient = LiveSearchClient(api),
                graphClient = LiveGraphClient(api),
                contextClient = LiveContextClient(api),
                timelineClient = LiveTimelineClient(api),
                // Recordings only (BSB English, labelled, when the reading translation has none).
                // NativeScriptureAudioClient (TTS rendered to a file) is parked until spoken audio is a live player.
                audioClient = HelloAOScriptureAudioClient(BookLanguage.ENGLISH),
                player = Media3AudioPlayerClient(context),
                initialTextScale = { ReaderTextScale.fromPreference(preferences.string(ReaderTextScale.PREFERENCE_KEY)) },
                notifications = AndroidNotificationClient(context),
                dailyVerseTitle = { context.getString(R.string.notification_channel_daily_verse) },
            ),
        )
    }
    AppScreen(viewModel.store)
}
