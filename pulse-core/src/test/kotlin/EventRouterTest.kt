import com.vhuthu.pulse_core.model.InternalEvent
import com.vhuthu.pulse_core.model.PulseEvent
import com.vhuthu.pulse_core.router.EventRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test


class EventRouterTest {

    private lateinit var eventBus: MutableSharedFlow<InternalEvent>
    private lateinit var scope: CoroutineScope
    private lateinit var router: EventRouter

    @Before
    fun setUp() {
        eventBus = MutableSharedFlow(replay = 1, extraBufferCapacity = 16)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        router = EventRouter(scope = scope, eventBus = eventBus)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }


    private suspend fun emitFrame(raw: String) {
        eventBus.emit(InternalEvent.FrameReceived(raw = raw))
    }

    private fun frame(
        topic: String,
        event: String,
        ref: String? = null,
        payload: String = "{}",
    ): String {
        val refPart = if (ref != null) """"ref":"$ref",""" else ""
        return """{"topic":"$topic","event":"$event",$refPart"payload":"$payload"}"""
    }

    /**
     * Collects the first event from [flow] using async so collection starts
     * BEFORE the frame is emitted — avoiding the race where tryEmit fires
     * into a SharedFlow with no active subscribers yet.
     */
    private fun <T> CoroutineScope.awaitFirst(
        flow: SharedFlow<T>,
    ) = async(Dispatchers.IO) {
        withTimeout(2_000) { flow.first() }
    }


    @Test
    fun `registerTopic returns a SharedFlow that emits matching events`() = runBlocking {
        val flow = router.registerTopic("payments.updates")
        val deferred = awaitFirst(flow)

        emitFrame(frame(topic = "payments.updates", event = "transfer_completed", ref = "r1"))

        val event = deferred.await()
        assertEquals("payments.updates", event.topic)
        assertEquals("transfer_completed", event.event)
        assertEquals("r1", event.ref)
    }

    @Test
    fun `events for unregistered topics are silently dropped`() = runBlocking {
        val flowA = router.registerTopic("topic.a")

        var received = false
        val collector = launch(Dispatchers.IO) {
            flowA.first()
            received = true
        }

        // Give collector time to start, then emit a frame for a different topic
        delay(100)
        emitFrame(frame(topic = "topic.b", event = "some_event"))
        delay(500)

        collector.cancel()
        assertFalse("topic.a should not receive a topic.b frame", received)
    }

    @Test
    fun `two subscribers on same topic both receive the event`() = runBlocking {
        val flow1 = router.registerTopic("chat.messages")
        val flow2 = router.registerTopic("chat.messages")

        // Start both collectors before emitting
        val deferred1 = awaitFirst(flow1)
        val deferred2 = awaitFirst(flow2)

        emitFrame(frame(topic = "chat.messages", event = "new_message"))

        val event1 = deferred1.await()
        val event2 = deferred2.await()
        assertEquals(event1.event, event2.event)
    }

    @Test
    fun `unregisterTopic stops delivery to that topic`() = runBlocking {
        val flow = router.registerTopic("payments.updates")
        router.unregisterTopic("payments.updates")

        var received = false
        val collector = launch(Dispatchers.IO) {
            flow.first()
            received = true
        }

        delay(100)
        emitFrame(frame(topic = "payments.updates", event = "transfer_completed"))
        delay(500)

        collector.cancel()
        assertFalse("Unregistered topic should not receive events", received)
    }

    @Test
    fun `completely invalid JSON is silently dropped`() = runBlocking {
        val flow = router.registerTopic("payments.updates")

        var received = false
        val collector = launch(Dispatchers.IO) {
            flow.first()
            received = true
        }

        delay(100)
        eventBus.emit(InternalEvent.FrameReceived(raw = "not json at all }{"))
        delay(500)

        collector.cancel()
        assertFalse("Malformed JSON should be silently dropped", received)
    }

    @Test
    fun `frame missing topic field is silently dropped`() = runBlocking {
        val flow = router.registerTopic("payments.updates")

        var received = false
        val collector = launch(Dispatchers.IO) {
            flow.first()
            received = true
        }

        delay(100)
        eventBus.emit(InternalEvent.FrameReceived(
            raw = """{"event":"transfer_completed","payload":"{}"}"""
        ))
        delay(500)

        collector.cancel()
        assertFalse("Frame missing topic should be silently dropped", received)
    }

    @Test
    fun `frame missing event field is silently dropped`() = runBlocking {
        val flow = router.registerTopic("payments.updates")

        var received = false
        val collector = launch(Dispatchers.IO) {
            flow.first()
            received = true
        }

        delay(100)
        eventBus.emit(InternalEvent.FrameReceived(
            raw = """{"topic":"payments.updates","payload":"{}"}"""
        ))
        delay(500)

        collector.cancel()
        assertFalse("Frame missing event should be silently dropped", received)
    }

    @Test
    fun `ref field is null when absent from the frame`() = runBlocking {
        val flow = router.registerTopic("payments.updates")
        val deferred = awaitFirst(flow)

        emitFrame(frame(topic = "payments.updates", event = "transfer_completed"))

        val event = deferred.await()
        assertNull("ref should be null when not present in frame", event.ref)
    }

    @Test
    fun `ref field is populated when present in the frame`() = runBlocking {
        val flow = router.registerTopic("payments.updates")
        val deferred = awaitFirst(flow)

        emitFrame(frame(topic = "payments.updates", event = "transfer_completed", ref = "msg-42"))

        val event = deferred.await()
        assertEquals("msg-42", event.ref)
    }


    @Test
    fun `SocketOpened event on bus does not affect topic delivery`() = runBlocking {
        val flow = router.registerTopic("payments.updates")
        val deferred = awaitFirst(flow)

        // Non-frame event — router should ignore it
        eventBus.emit(InternalEvent.SocketOpened)

        // Valid frame should still arrive
        emitFrame(frame(topic = "payments.updates", event = "transfer_completed"))

        val event = deferred.await()
        assertEquals("transfer_completed", event.event)
    }
}