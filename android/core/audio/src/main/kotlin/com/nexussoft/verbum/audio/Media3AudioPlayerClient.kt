package com.nexussoft.verbum.audio

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nexussoft.verbum.clients.AudioPlayerClient
import com.nexussoft.verbum.clients.AudioPlayerEvent
import com.nexussoft.verbum.clients.NowPlayingInfo
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [AudioPlayerClient] over a [MediaController] bound to [PlaybackService]. */
class Media3AudioPlayerClient(private val context: Context) : AudioPlayerClient {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private suspend fun controller(): MediaController = withContext(Dispatchers.Main) {
        val future = controllerFuture ?: MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync().also { controllerFuture = it }
        future.await()
    }

    override suspend fun load(url: String, nowPlaying: NowPlayingInfo) {
        val c = controller()
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(nowPlaying.title).setArtist(nowPlaying.subtitle).build())
            .build()
        c.setMediaItem(item, 0L) // Event narration always starts at the first verse, not the live edge.
        c.prepare()
    }

    override suspend fun play() { controller().play() }
    override suspend fun pause() { controller().pause() }
    override suspend fun seek(seconds: Double) { controller().seekTo((seconds * 1000).toLong()) }
    override suspend fun setRate(rate: Float) { controller().setPlaybackSpeed(rate) }
    override suspend fun stop() { controller().run { stop(); clearMediaItems() } }

    override val events: Flow<AudioPlayerEvent> = callbackFlow {
        val c = controller()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> trySend(AudioPlayerEvent.Ready(c.duration.coerceAtLeast(0) / 1000.0))
                    Player.STATE_ENDED -> trySend(AudioPlayerEvent.Ended)
                    else -> Unit
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { trySend(AudioPlayerEvent.Playing(isPlaying)) }
            override fun onPlayerError(error: PlaybackException) { trySend(AudioPlayerEvent.Failed) }
        }
        c.addListener(listener)
        // A collector can attach after prepare/play. Replay the current state
        // instead of waiting for a transition that has already happened.
        if (c.playbackState == Player.STATE_READY) {
            trySend(AudioPlayerEvent.Ready(c.duration.coerceAtLeast(0) / 1000.0))
        }
        if (c.playerError != null) trySend(AudioPlayerEvent.Failed)
        trySend(AudioPlayerEvent.Playing(c.isPlaying))
        trySend(AudioPlayerEvent.Time(c.currentPosition.coerceAtLeast(0) / 1000.0))
        var reportedDuration = -1L
        val ticker = launch {
            while (isActive) {
                if (c.duration > 0 && c.duration != reportedDuration) {
                    reportedDuration = c.duration
                    trySend(AudioPlayerEvent.Ready(c.duration / 1000.0))
                }
                if (c.isPlaying) trySend(AudioPlayerEvent.Time(c.currentPosition / 1000.0))
                delay(1000)
            }
        }
        awaitClose {
            ticker.cancel()
            c.removeListener(listener)
        }
    }
}
