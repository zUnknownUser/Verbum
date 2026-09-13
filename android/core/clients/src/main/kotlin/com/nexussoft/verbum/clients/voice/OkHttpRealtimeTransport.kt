package com.nexussoft.verbum.clients.voice

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import java.util.concurrent.TimeUnit

/** OkHttp: one socket, text frames in and out. */
class OkHttpRealtimeTransport(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS).build(),
) : RealtimeTransport {
    @Volatile private var socket: WebSocket? = null

    override suspend fun connect(url: String, headers: Map<String, String>): Flow<String> {
        val frames = Channel<String>(Channel.UNLIMITED)
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { frames.trySend(text) }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { frames.trySend(bytes.utf8()) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { frames.close() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // A close initiated by us ends the flow quietly; anything else is a failure.
                if (socket == null) frames.close() else frames.close(IOException(t))
            }
        })
        return frames.receiveAsFlow()
    }

    override suspend fun send(text: String) {
        socket?.send(text)
    }

    override suspend fun close() {
        val current = socket
        socket = null
        current?.close(1000, null)
    }
}
