package com.vhuthu.pulse_core.engine

import com.sun.tools.javac.util.Log
import com.vhuthu.pulse_core.model.InternalEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Thin wrapper around OkHttp's WebSocket.
 *
 * Responsibilities:
 * - Open and close the raw socket
 * - Translate OkHttp callbacks into [InternalEvent] emissions on the shared bus
 * - Send raw text frames to the server
 *
 * This is the ONLY class in :pulse-core that imports OkHttp.
 * Nothing above this layer touches OkHttp types directly.
 *
 * Internal to the SDK. Never exposed in the public API.
 */
internal class WebSocketEngine(
    private val url: String,
    private val httpClient: OkHttpClient,
    private val eventBus: MutableSharedFlow<InternalEvent>,
    private val scope: CoroutineScope,
) {
    // The active socket. Null when disconnected.
    @Volatile
    private var socket: WebSocket? = null

    // Guards against emitting SocketClosed more than once per connection.
    // Set to true when close() is called intentionally so onFailure
    // (which OkHttp may fire after cancel()) doesn't emit a second event.
    @Volatile
    private var closeInitiated = false

    /**
     * Opens a new WebSocket connection.
     *
     * OkHttp manages the connection on its own thread pool.
     * All callbacks are translated to [InternalEvent] and emitted
     * onto the shared bus so the rest of the SDK stays coroutine-native.
     */
    fun open() {
        closeInitiated = false
        val request = Request.Builder()
            .url(url)
            .build()
        socket = httpClient.newWebSocket(request, PulseWebSocketListener())
    }

    /**
     * Closes the socket.
     *
     * We emit [InternalEvent.SocketClosed] immediately here rather than
     * waiting on OkHttp's close handshake, which is unreliable in test
     * environments and adds unnecessary latency in production.
     *
     * We then call [WebSocket.cancel] to force-close the underlying TCP
     * connection immediately without waiting for the server's CLOSE frame.
     *
     * @param code   WebSocket close code. 1000 = normal closure.
     * @param reason Human-readable reason sent to the server.
     */
    fun close(code: Int = 1000, reason: String = "Client disconnect") {
        closeInitiated = true
        socket?.cancel()
        socket = null
        emit(InternalEvent.SocketClosed(code = code, reason = reason))
    }

    /**
     * Sends a raw JSON string to the server.
     *
     * Returns true if the message was enqueued successfully.
     * Returns false if the socket is null or closed.
     */
    fun send(text: String): Boolean {
        return socket?.send(text) ?: false
    }

    private inner class PulseWebSocketListener : WebSocketListener() {

        /**
         * Called by OkHttp when the WebSocket handshake completes.
         * Emits [InternalEvent.SocketOpened] onto the bus.
         */
        override fun onOpen(webSocket: WebSocket, response: Response) {
            emit(InternalEvent.SocketOpened)
        }

        /**
         * Called by OkHttp when a text frame arrives.
         * Emits [InternalEvent.FrameReceived] so EventRouter can deserialize it.
         */
        override fun onMessage(webSocket: WebSocket, text: String) {
            emit(InternalEvent.FrameReceived(raw = text))
        }

        /**
         * Called by OkHttp when the SERVER initiates a clean close.
         * We acknowledge it and emit [InternalEvent.SocketClosed].
         */
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
            if (!closeInitiated) {
                socket = null
                emit(InternalEvent.SocketClosed(code = code, reason = reason))
            }
        }

        /**
         * Called by OkHttp on connection failure OR after cancel().
         * Only emit [InternalEvent.SocketFailed] if this wasn't an
         * intentional close — cancel() also triggers onFailure.
         */
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!closeInitiated) {
                socket = null
                emit(InternalEvent.SocketFailed(cause = t))
            }
        }
    }


    private fun emit(event: InternalEvent) {
        scope.launch {
            eventBus.emit(event)
        }
    }
}