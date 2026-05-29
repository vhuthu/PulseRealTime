package com.vhuthu.pulse_testing

import com.vhuthu.pulse_core.model.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakePulseRealtimeTest {

    private lateinit var fake: FakePulseRealtime

    @Before
    fun setUp() {
        fake = FakePulseRealtime()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial connectionState is Disconnected`() {
        assertEquals(ConnectionState.Disconnected, fake.connectionState.value)
    }

    @Test
    fun `initial connectCallCount is zero`() {
        assertEquals(0, fake.connectCallCount)
    }

    // ── connect / disconnect recording ────────────────────────────────────────

    @Test
    fun `connect increments connectCallCount`() {
        fake.connect()
        fake.connect()
        assertEquals(2, fake.connectCallCount)
    }

    @Test
    fun `disconnect increments disconnectCallCount`() {
        fake.disconnect()
        assertEquals(1, fake.disconnectCallCount)
    }

    // ── subscribe recording ───────────────────────────────────────────────────

    @Test
    fun `subscribe records topic in subscribedTopics`() {
        fake.subscribe("payments.updates")
        fake.subscribe("chat.messages")
        assertEquals(listOf("payments.updates", "chat.messages"), fake.subscribedTopics)
    }

    @Test
    fun `subscribe returns TopicSubscription with correct topic`() {
        val sub = fake.subscribe("payments.updates")
        assertEquals("payments.updates", sub.topic)
    }

    // ── cancel recording ──────────────────────────────────────────────────────

    @Test
    fun `cancel records topic in cancelledTopics`() {
        val sub = fake.subscribe("payments.updates")
        sub.cancel()
        assertTrue(fake.cancelledTopics.contains("payments.updates"))
    }

    // ── simulateMessage ───────────────────────────────────────────────────────

    @Test
    fun `simulateMessage delivers event to subscriber`() = runBlocking {
        val sub = fake.subscribe("payments.updates")

        val deferred = async(Dispatchers.IO) {
            withTimeout(2_000) { sub.events.first() }
        }
        delay(100)

        fake.simulateMessage(
            topic   = "payments.updates",
            event   = "transfer_completed",
            payload = """{"amount":500}""",
            ref     = "ref-001",
        )

        val event = deferred.await()
        assertEquals("payments.updates", event.topic)
        assertEquals("transfer_completed", event.event)
        assertEquals("""{"amount":500}""", event.payload)
        assertEquals("ref-001", event.ref)
    }

    @Test
    fun `simulateMessage for unsubscribed topic does nothing`() = runBlocking {
        // No subscription registered — should not throw
        fake.simulateMessage(topic = "ghost.topic", event = "some_event")
        assertTrue(true) // reached without exception
    }

    @Test
    fun `simulateMessage does not deliver to wrong topic`() = runBlocking {
        val sub = fake.subscribe("payments.updates")

        var received = false
        val collector = launch(Dispatchers.IO) {
            sub.events.first()
            received = true
        }

        delay(100)
        fake.simulateMessage(topic = "chat.messages", event = "new_message")
        delay(500)
        collector.cancel()

        assertFalse("Should not receive event for a different topic", received)
    }

    // ── simulateConnected / simulateDisconnected ──────────────────────────────

    @Test
    fun `simulateConnected sets state to Connected`() {
        fake.simulateConnected()
        assertEquals(ConnectionState.Connected, fake.connectionState.value)
    }

    @Test
    fun `simulateDisconnected sets state to Disconnected`() {
        fake.simulateConnected()
        fake.simulateDisconnected()
        assertEquals(ConnectionState.Disconnected, fake.connectionState.value)
    }

    @Test
    fun `simulateReconnecting sets state with correct attempt`() {
        fake.simulateReconnecting(attempt = 3)
        val state = fake.connectionState.value
        assertTrue(state is ConnectionState.Reconnecting)
        assertEquals(3, (state as ConnectionState.Reconnecting).attempt)
    }

    @Test
    fun `simulateFailed sets state to Failed with cause`() {
        val cause = RuntimeException("network gone")
        fake.simulateFailed(cause)
        val state = fake.connectionState.value
        assertTrue(state is ConnectionState.Failed)
        assertEquals(cause, (state as ConnectionState.Failed).cause)
    }

    // ── connectionState flow ──────────────────────────────────────────────────

    @Test
    fun `connectionState flow emits state changes`() = runBlocking {
        val deferred = async(Dispatchers.IO) {
            withTimeout(2_000) {
                fake.connectionState.first { it is ConnectionState.Connected }
            }
        }
        delay(100)
        fake.simulateConnected()

        val state = deferred.await()
        assertTrue(state is ConnectionState.Connected)
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    fun `reset clears all recorded state`() {
        fake.connect()
        fake.disconnect()
        fake.subscribe("payments.updates").cancel()
        fake.simulateConnected()

        fake.reset()

        assertEquals(0, fake.connectCallCount)
        assertEquals(0, fake.disconnectCallCount)
        assertTrue(fake.subscribedTopics.isEmpty())
        assertTrue(fake.cancelledTopics.isEmpty())
        assertEquals(ConnectionState.Disconnected, fake.connectionState.value)
    }
}