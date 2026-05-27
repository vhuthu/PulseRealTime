import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A WebSocketListener that does nothing.
 * Used when the server side of the connection is irrelevant to the test.
 */
internal class NoOpWebSocketListener : WebSocketListener()

/**
 * A WebSocketListener that captures the server-side WebSocket reference
 * so tests can push messages to the client.
 *
 * Usage:
 * ```kotlin
 * val listener = RecordingWebSocketListener()
 * server.enqueue(MockResponse().withWebSocketUpgrade(listener))
 * val serverSocket = listener.awaitWebSocket()
 * serverSocket.send("hello")
 * ```
 */
internal class RecordingWebSocketListener : WebSocketListener() {

    private val webSocketQueue = LinkedBlockingQueue<WebSocket>()

    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
        webSocketQueue.offer(webSocket)
    }

    /**
     * Blocks until the server-side WebSocket is available (handshake complete).
     * Times out after 2 seconds to prevent tests hanging indefinitely.
     */
    fun awaitWebSocket(timeoutSeconds: Long = 2): WebSocket {
        return webSocketQueue.poll(timeoutSeconds, TimeUnit.SECONDS)
            ?: error("WebSocket did not open within ${timeoutSeconds}s")
    }
}