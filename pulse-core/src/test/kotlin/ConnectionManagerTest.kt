import com.vhuthu.pulse_core.engine.WebSocketEngine
import com.vhuthu.pulse_core.manager.ConnectionManager
import com.vhuthu.pulse_core.model.ConnectionState
import com.vhuthu.pulse_core.model.ExponentialBackoff
import com.vhuthu.pulse_core.model.InternalEvent
import com.vhuthu.pulse_core.model.NoReconnect
import com.vhuthu.pulse_core.model.ReconnectPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var eventBus: MutableSharedFlow<InternalEvent>
    private lateinit var scope: CoroutineScope
    private lateinit var engine: WebSocketEngine
    private lateinit var manager: ConnectionManager

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

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

    private fun buildManager(
        policy: ReconnectPolicy = NoReconnect,
    ): ConnectionManager = ConnectionManager(
        scope = scope,
        engine = engine,
        policy = policy,
        eventBus = eventBus,
    )


    @Test
    fun `initial state is Disconnected`() {
        val manager = buildManager()
        assertEquals(ConnectionState.Disconnected, manager.state.value)
    }


    @Test
    fun `connect transitions to Connecting then Connected on success`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(NoOpServerListener()))
        val manager = buildManager()

        manager.connect()

        // Should pass through Connecting and settle on Connected
        withTimeout(3_000) {
            manager.state.first { it is ConnectionState.Connected }
        }
        assertEquals(ConnectionState.Connected, manager.state.value)
    }

    @Test
    fun `calling connect twice does not open two sockets`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(NoOpServerListener()))
        val manager = buildManager()

        manager.connect()
        manager.connect() // second call — should be ignored

        withTimeout(3_000) {
            manager.state.first { it is ConnectionState.Connected }
        }

        // Only one request should have reached the server
        assertEquals(1, server.requestCount)
    }


    @Test
    fun `disconnect transitions to Disconnected`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(NoOpServerListener()))
        val manager = buildManager()

        manager.connect()
        withTimeout(3_000) { manager.state.first { it is ConnectionState.Connected } }

        manager.disconnect()

        withTimeout(2_000) { manager.state.first { it is ConnectionState.Disconnected } }
        assertEquals(ConnectionState.Disconnected, manager.state.value)
    }

    @Test
    fun `disconnect while Connecting still results in Disconnected`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(NoOpServerListener()))
        val manager = buildManager()

        manager.connect()   // starts connecting
        manager.disconnect() // immediately cancel

        withTimeout(2_000) { manager.state.first { it is ConnectionState.Disconnected } }
        assertEquals(ConnectionState.Disconnected, manager.state.value)
    }


    @Test
    fun `socket failure with NoReconnect transitions to Failed`() = runBlocking {
        val manager = buildManager(policy = NoReconnect)

        // Point engine at a bad URL so it fails immediately
        val badEngine = WebSocketEngine(
            url = "ws://localhost:1",
            httpClient = OkHttpClient(),
            eventBus = eventBus,
            scope = scope,
        )
        val badManager = ConnectionManager(
            scope = scope,
            engine = badEngine,
            policy = NoReconnect,
            eventBus = eventBus,
        )

        badManager.connect()

        withTimeout(5_000) {
            badManager.state.first { it is ConnectionState.Failed }
        }
        assertTrue(badManager.state.value is ConnectionState.Failed)
    }

    @Test
    fun `socket failure with ExponentialBackoff transitions to Reconnecting`() = runBlocking {
        val badEngine = WebSocketEngine(
            url = "ws://localhost:1",
            httpClient = OkHttpClient(),
            eventBus = eventBus,
            scope = scope,
        )
        val manager = ConnectionManager(
            scope = scope,
            engine = badEngine,
            policy = ExponentialBackoff(maxAttempts = 2, initialDelayMillis = 500),
            eventBus = eventBus,
        )

        manager.connect()

        withTimeout(5_000) {
            manager.state.first { it is ConnectionState.Reconnecting }
        }
        assertTrue(manager.state.value is ConnectionState.Reconnecting)
    }


    @Test
    fun `disconnectTemporary transitions to Disconnected`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(NoOpServerListener()))
        val manager = buildManager()

        manager.connect()
        withTimeout(3_000) { manager.state.first { it is ConnectionState.Connected } }

        manager.disconnectTemporary()

        withTimeout(2_000) { manager.state.first { it is ConnectionState.Disconnected } }
        assertEquals(ConnectionState.Disconnected, manager.state.value)
    }
}

private class NoOpServerListener : okhttp3.WebSocketListener()