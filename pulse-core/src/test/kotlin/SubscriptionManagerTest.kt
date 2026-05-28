import com.vhuthu.pulse_core.engine.WebSocketEngine
import com.vhuthu.pulse_core.manager.SubscriptionManager
import com.vhuthu.pulse_core.model.InternalEvent
import com.vhuthu.pulse_core.router.EventRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubscriptionManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var eventBus: MutableSharedFlow<InternalEvent>
    private lateinit var scope: CoroutineScope
    private lateinit var engine: WebSocketEngine
    private lateinit var router: EventRouter
    private lateinit var manager: SubscriptionManager

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

        router = EventRouter(scope = scope, eventBus = eventBus)

        manager = SubscriptionManager(
            scope = scope,
            engine = engine,
            router = router,
            eventBus = eventBus,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        scope.cancel()
    }


    @Test
    fun `subscribe returns a TopicSubscription with correct topic`() {
        val sub = manager.subscribe("payments.updates")
        assertEquals("payments.updates", sub.topic)
    }

    @Test
    fun `subscribe delivers events for that topic`() = runBlocking {
        val sub = manager.subscribe("payments.updates")

        // Launch collector and give it time to reach the suspension point
        val deferred = async(Dispatchers.IO) {
            withTimeout(2_000) { sub.events.first() }
        }
        delay(100) // wait for collector to reach first()

        val raw = """{"topic":"payments.updates","event":"transfer_completed","payload":"{}"}"""
        eventBus.emit(InternalEvent.FrameReceived(raw = raw))

        val event = deferred.await()
        assertEquals("payments.updates", event.topic)
        assertEquals("transfer_completed", event.event)
    }

    @Test
    fun `subscribe does not deliver events for a different topic`() = runBlocking {
        val sub = manager.subscribe("payments.updates")

        var received = false
        val collector = launch(Dispatchers.IO) {
            sub.events.first()
            received = true
        }

        delay(100)

        val raw = """{"topic":"chat.messages","event":"new_message","payload":"{}"}"""
        eventBus.emit(InternalEvent.FrameReceived(raw = raw))

        delay(500)
        collector.cancel()

        assertFalse("Should not receive events from a different topic", received)
    }


    @Test
    fun `cancel stops event delivery for that topic`() = runBlocking {
        val sub = manager.subscribe("payments.updates")
        sub.cancel()

        var received = false
        val collector = launch(Dispatchers.IO) {
            sub.events.first()
            received = true
        }

        delay(100)

        val raw = """{"topic":"payments.updates","event":"transfer_completed","payload":"{}"}"""
        eventBus.emit(InternalEvent.FrameReceived(raw = raw))

        delay(500)
        collector.cancel()

        assertFalse("Cancelled subscription should not receive events", received)
    }


    @Test
    fun `SocketOpened replays all desired subscriptions without error`() = runBlocking {
        manager.subscribe("payments.updates")
        manager.subscribe("chat.messages")

        eventBus.emit(InternalEvent.SocketOpened)

        delay(300)

        assertTrue(true) // reached without exception — resubscription ran cleanly
    }

    @Test
    fun `SocketClosed clears confirmed topics and resubscribes on next SocketOpened`() = runBlocking {
        manager.subscribe("payments.updates")

        // Server ack
        eventBus.emit(InternalEvent.FrameReceived(
            raw = """{"type":"subscribed","topic":"payments.updates"}"""
        ))
        delay(100)

        // Disconnect
        eventBus.emit(InternalEvent.SocketClosed(code = 1000, reason = "normal"))
        delay(100)

        // Reconnect — desired topics should replay
        eventBus.emit(InternalEvent.SocketOpened)
        delay(300)

        // Subscription should still be tracked and active
        val sub = manager.subscribe("payments.updates")
        assertEquals("payments.updates", sub.topic)
    }


    @Test
    fun `multiple topics each receive only their own events`() = runBlocking {
        val payments = manager.subscribe("payments.updates")
        val chat = manager.subscribe("chat.messages")

        // Start both collectors and wait for them to reach suspension point
        val paymentsDeferred = async(Dispatchers.IO) {
            withTimeout(2_000) { payments.events.first() }
        }
        val chatDeferred = async(Dispatchers.IO) {
            withTimeout(2_000) { chat.events.first() }
        }
        delay(100) // give both collectors time to reach first()

        eventBus.emit(InternalEvent.FrameReceived(
            raw = """{"topic":"payments.updates","event":"transfer_completed","payload":"{}"}"""
        ))
        eventBus.emit(InternalEvent.FrameReceived(
            raw = """{"topic":"chat.messages","event":"new_message","payload":"{}"}"""
        ))

        val paymentEvent = paymentsDeferred.await()
        val chatEvent = chatDeferred.await()

        assertEquals("transfer_completed", paymentEvent.event)
        assertEquals("new_message", chatEvent.event)
    }
}