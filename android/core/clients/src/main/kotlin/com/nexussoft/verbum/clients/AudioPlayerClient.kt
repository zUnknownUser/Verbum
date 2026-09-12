package com.nexussoft.verbum.clients

import kotlinx.coroutines.flow.Flow

/** What the player reports back. Remote commands (lock screen, headphones) arrive here too, so the feature decides. */
sealed interface AudioPlayerEvent {
    data class Ready(val durationSeconds: Double) : AudioPlayerEvent
    data class Time(val seconds: Double) : AudioPlayerEvent
    data class Playing(val isPlaying: Boolean) : AudioPlayerEvent
    data object Ended : AudioPlayerEvent
    data object Failed : AudioPlayerEvent
}

/** What the notification and lock screen show. */
data class NowPlayingInfo(val title: String, val subtitle: String)

/** Streams one recording at a time. ExoPlayer lives behind this; features and views never see it. */
interface AudioPlayerClient {
    suspend fun load(url: String, nowPlaying: NowPlayingInfo)
    suspend fun play()
    suspend fun pause()
    suspend fun seek(seconds: Double)
    suspend fun setRate(rate: Float)
    suspend fun stop()
    val events: Flow<AudioPlayerEvent>
}
