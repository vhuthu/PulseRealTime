import com.vhuthu.pulse_core.engine.WebSocketEngine
import com.vhuthu.pulse_core.model.InternalEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for [WebSocketEngine] using OkHttp's MockWebServer.
 *
 * MockWebServer runs a real local HTTP/WebSocket server on a random port,
 * so these tests exercise actual socket behaviour without any external network.
 *
 * Note: add mockwebserver to pulse-core test dependencies:
 *   testImplementation("com.squareup.okhttp3:mockwebserver:<version>")
 */
/**
 * Tests for [WebSocketEngine] using OkHttp's MockWebServer.
 *
 * MockWebServer runs a real local HTTP/WebSocket server on a random port,
 * so these tests exercise actual socket behaviour without any external network.
 *
 * Note: add mockwebserver to pulse-core test dependencies:
 *   testImplementation("com.squareup.okhttp3:mockwebserver:<version>")
 */
class WebSocketEngineTest {

    private lateinit var server: MockWebServer
    private lateinit var eventBus: MutableSharedFlow<InternalEvent>
    private lateinit var scope: CoroutineScope
    private lateinit var engine: WebSocketEngine

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        // replay = 1 so that a late collector can still see the most recent event
        // even if it was emitted just before collection started
        eventBus = MutableSharedFlow(replay = 1, extraBufferCapacity = 16)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        engine = WebSocketEngine(
            url = server.url("/socket").toString(),
            httpClient = OkHttpClient(),
            eventBus = eventBus,
            scope = scope,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        scope.cancel()
    }

    // ── open ─────────────────────────────────────────────────────────────────

    @Test
    fun `open emits SocketOpened when server accepts the handshake`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(NoOpWebSocketListener()))

        engine.open()

        val event = withTimeout(2_000) { eventBus.first() }
        assertTrue("Expected SocketOpened but got $event", event is InternalEvent.SocketOpened)
    }

    // ── onMessage ─────────────────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `incoming text frame emits FrameReceived with correct raw content`() = runBlocking {
        val serverListener = RecordingWebSocketListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        engine.open()

        // Wait for handshake on IO so we don't block the runBlocking event loop
        val serverSocket = withContext(Dispatchers.IO) {
            serverListener.awaitWebSocket()
        }

        // Reset replay cache so SocketOpened doesn't satisfy our FrameReceived check
        eventBus.resetReplayCache()

        val payload = """{"topic":"payments","event":"transfer","ref":"r1","payload":{}}"""
        serverSocket.send(payload)

        val event = withTimeout(4_000) {
            eventBus.first { it is InternalEvent.FrameReceived }
        } as InternalEvent.FrameReceived

        assertEquals(payload, event.raw)
    }

    // ── close ─────────────────────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `close emits SocketClosed after clean shutdown`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(NoOpWebSocketListener()))

        engine.open()

        withTimeout(2_000) {
            eventBus.first { it is InternalEvent.SocketOpened }
        }

        eventBus.resetReplayCache()

        engine.close()

        val event = withTimeout(2_000) {
            eventBus.first { it is InternalEvent.SocketClosed }
        }
        assertTrue(event is InternalEvent.SocketClosed)
    }

    // ── send ──────────────────────────────────────────────────────────────────

    @Test
    fun `send returns true when socket is open`() = runBlocking {
        val serverListener = RecordingWebSocketListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        engine.open()
        withTimeout(2_000) { eventBus.first { it is InternalEvent.SocketOpened } }

        val result = engine.send("""{"type":"subscribe","topic":"payments","ref":"r1"}""")
        assertTrue(result)
    }

    @Test
    fun `send returns false when socket is not open`() {
        val result = engine.send("""{"type":"subscribe","topic":"payments","ref":"r1"}""")
        assertFalse(result)
    }

    // ── failure ───────────────────────────────────────────────────────────────

    @Test
    fun `connection failure emits SocketFailed`() = runBlocking {
        val badEngine = WebSocketEngine(
            url = "ws://localhost:1",
            httpClient = OkHttpClient(),
            eventBus = eventBus,
            scope = scope,
        )

        badEngine.open()

        val event = withTimeout(5_000) {
            eventBus.first { it is InternalEvent.SocketFailed }
        }
        assertTrue(event is InternalEvent.SocketFailed)
    }
}