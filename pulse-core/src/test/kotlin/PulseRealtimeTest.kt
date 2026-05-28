import com.vhuthu.pulse_core.PulseRealtime
import com.vhuthu.pulse_core.model.ConnectionState
import com.vhuthu.pulse_core.model.NoReconnect
import com.vhuthu.pulse_core.model.ReconnectPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * End-to-end integration tests for [PulseRealtime].
 *
 * These tests exercise the full public API surface — Builder, connect,
 * disconnect, subscribe, unsubscribe — against a real local WebSocket
 * server provided by MockWebServer.
 *
 * No internal classes are referenced here. Everything goes through
 * the public API exactly as a consumer would use it.
 */
class PulseRealtimeIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var pulse: PulseRealtime

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        if (::pulse.isInitialized) {
            pulse.disconnect()
        }
        server.shutdown()
    }

    private fun buildPulse(
        policy: ReconnectPolicy = NoReconnect,
    ): PulseRealtime = PulseRealtime.Builder()
        .url(server.url("/socket").toString())
        .reconnectPolicy(policy)
        .build()
        .also { pulse = it }


    @Test(expected = IllegalStateException::class)
    fun `build without url throws IllegalStateException`() {
        PulseRealtime.Builder().build()
    }

    @Test
    fun `initial connectionState is Disconnected`() {
        pulse = buildPulse()
        assertEquals(ConnectionState.Disconnected, pulse.connectionState.value)
    }

    @Test
    fun `connect transitions to Connected on successful handshake`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(NoOpListener()))
        pulse = buildPulse()

        pulse.connect()

        withTimeout(3_000) {
            pulse.connectionState.first { it is ConnectionState.Connected }
        }

        assertEquals(ConnectionState.Connected, pulse.connectionState.value)
    }

    @Test
    fun `disconnect transitions to Disconnected`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(NoOpListener()))
        pulse = buildPulse()

        pulse.connect()
        withTimeout(3_000) { pulse.connectionState.first { it is ConnectionState.Connected } }

        pulse.disconnect()
        withTimeout(2_000) { pulse.connectionState.first { it is ConnectionState.Disconnected } }

        assertEquals(ConnectionState.Disconnected, pulse.connectionState.value)
    }

    @Test
    fun `subscribe delivers incoming events for the correct topic`() = runBlocking {
        val serverListener = EchoWebSocketListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        pulse = buildPulse()

        pulse.connect()
        withTimeout(3_000) { pulse.connectionState.first { it is ConnectionState.Connected } }

        val sub = pulse.subscribe("payments.updates")

        val deferred = async(Dispatchers.IO) {
            withTimeout(3_000) { sub.events.first() }
        }
        delay(100)

        // Server pushes an event for this topic
        val frame = """{"topic":"payments.updates","event":"transfer_completed","ref":"r1","payload":"{}"}"""
        serverListener.awaitWebSocket().send(frame)

        val event = deferred.await()
        assertEquals("payments.updates", event.topic)
        assertEquals("transfer_completed", event.event)
        assertEquals("r1", event.ref)
    }

    @Test
    fun `subscribe does not deliver events for a different topic`() = runBlocking {
        val serverListener = EchoWebSocketListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        pulse = buildPulse()

        pulse.connect()
        withTimeout(3_000) { pulse.connectionState.first { it is ConnectionState.Connected } }

        val sub = pulse.subscribe("payments.updates")

        var received = false
        val collector = async(Dispatchers.IO) {
            sub.events.first()
            received = true
        }
        delay(100)

        // Server pushes an event for a DIFFERENT topic
        val frame = """{"topic":"chat.messages","event":"new_message","payload":"{}"}"""
        serverListener.awaitWebSocket().send(frame)

        delay(500)
        collector.cancel()

        assertFalse("Should not receive events from a different topic", received)
    }

    @Test
    fun `cancel stops event delivery`() = runBlocking {
        val serverListener = EchoWebSocketListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        pulse = buildPulse()

        pulse.connect()
        withTimeout(3_000) { pulse.connectionState.first { it is ConnectionState.Connected } }

        val sub = pulse.subscribe("payments.updates")
        sub.cancel()

        var received = false
        val collector = async(Dispatchers.IO) {
            sub.events.first()
            received = true
        }
        delay(100)

        val frame = """{"topic":"payments.updates","event":"transfer_completed","payload":"{}"}"""
        serverListener.awaitWebSocket().send(frame)

        delay(500)
        collector.cancel()

        assertFalse("Cancelled subscription should not receive events", received)
    }

    @Test
    fun `connection to bad URL with NoReconnect transitions to Failed`() = runBlocking {
        pulse = PulseRealtime.Builder()
            .url("ws://localhost:1")
            .reconnectPolicy(NoReconnect)
            .build()

        pulse.connect()

        withTimeout(5_000) {
            pulse.connectionState.first { it is ConnectionState.Failed }
        }

        assertTrue(pulse.connectionState.value is ConnectionState.Failed)
    }
}

private class NoOpListener : WebSocketListener()

private class EchoWebSocketListener : WebSocketListener() {
    private val queue = LinkedBlockingQueue<WebSocket>()

    override fun onOpen(webSocket: WebSocket, response: Response) {
        queue.offer(webSocket)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(code, reason)
    }

    fun awaitWebSocket(timeoutSeconds: Long = 3): WebSocket =
        queue.poll(timeoutSeconds, TimeUnit.SECONDS)
            ?: error("Server WebSocket did not open within ${timeoutSeconds}s")
}