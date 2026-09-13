package com.nexussoft.verbum.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import com.nexussoft.verbum.clients.voice.VoiceAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * `AudioRecord` in and `AudioTrack` out at PCM16 mono 24 kHz, on the voice-communication
 * source and usage so the platform's echo cancellation applies and the companion can be
 * heard through the speaker without hearing itself. Twin of iOS `AVAudioEngineVoiceAudio`.
 * The runtime permission prompt is the app's ([permission]); this only checks and reports.
 */
class AndroidVoiceAudio(
    private val context: Context,
    /** Asks the user for RECORD_AUDIO; the app wires it to an activity result launcher. */
    private val permission: suspend () -> Boolean,
) : VoiceAudio {
    private companion object {
        const val RATE = 24_000
        const val CHUNK_BYTES = RATE / 10 * 2 // 100 ms of PCM16 mono
    }

    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var captureThread: Thread? = null
    private var playbackThread: Thread? = null
    private val playbackQueue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var capturing = false
    @Volatile private var playing = false
    private var echo: AcousticEchoCanceler? = null
    private var noise: NoiseSuppressor? = null
    private var previousMode = AudioManager.MODE_NORMAL

    override suspend fun requestPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true
        return permission()
    }

    override suspend fun startCapture(onChunk: (ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(AudioManager::class.java)
        previousMode = manager.mode
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        manager.isSpeakerphoneOn = true

        val minIn = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minIn, CHUNK_BYTES * 4))
        if (recorder.state != AudioRecord.STATE_INITIALIZED) { recorder.release(); throw IllegalStateException("microphone unavailable") }
        if (AcousticEchoCanceler.isAvailable()) echo = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true }
        if (NoiseSuppressor.isAvailable()) noise = NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true }

        val minOut = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val player = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(minOut, CHUNK_BYTES * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        record = recorder
        track = player
        capturing = true
        playing = true
        recorder.startRecording()
        player.play()

        captureThread = thread(name = "verbum-voice-capture") {
            val buffer = ByteArray(CHUNK_BYTES)
            while (capturing) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) onChunk(buffer.copyOf(read))
            }
        }
        playbackThread = thread(name = "verbum-voice-playback") {
            while (playing) {
                val pcm = playbackQueue.take()
                if (pcm.isEmpty()) continue // a flush marker
                var offset = 0
                while (offset < pcm.size && playing) {
                    val written = player.write(pcm, offset, pcm.size - offset)
                    if (written <= 0) break
                    offset += written
                }
            }
        }
    }

    override suspend fun stopCapture() {
        capturing = false
        captureThread?.join(500)
        captureThread = null
        runCatching { record?.stop() }
    }

    override suspend fun play(pcm: ByteArray) {
        playbackQueue.put(pcm)
    }

    override suspend fun stopPlayback() {
        playbackQueue.clear()
        runCatching { track?.pause(); track?.flush(); track?.play() }
    }

    override suspend fun finish() = withContext(Dispatchers.IO) {
        capturing = false
        playing = false
        playbackQueue.clear()
        playbackQueue.put(ByteArray(0))
        captureThread?.join(500); captureThread = null
        playbackThread?.join(500); playbackThread = null
        runCatching { echo?.release() }; echo = null
        runCatching { noise?.release() }; noise = null
        runCatching { record?.stop(); record?.release() }; record = null
        runCatching { track?.stop(); track?.release() }; track = null
        val manager = context.getSystemService(AudioManager::class.java)
        @Suppress("DEPRECATION")
        manager.isSpeakerphoneOn = false
        manager.mode = previousMode
    }
}
