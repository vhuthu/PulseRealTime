package com.vhuthu.pulse_testing

import com.vhuthu.pulse_core.model.ConnectionState
import com.vhuthu.pulse_core.model.PulseEvent
import com.vhuthu.pulse_core.model.TopicSubscription
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A test double for [PulseRealtime].
 *
 * Use this in unit tests to simulate connection behaviour and incoming
 * events without a real WebSocket server or network.
 *
 * Example:
 * ```kotlin
 * val fake = FakePulseRealtime()
 *
 * // In your ViewModel test
 * val viewModel = PaymentsViewModel(pulseRealtime = fake)
 *
 * // Simulate an incoming event
 * fake.simulateMessage(
 *     topic   = "payments.updates",
 *     event   = "transfer_completed",
 *     payload = """{"amount": 500}""",
 * )
 *
 * // Assert your ViewModel reacted correctly
 * assertEquals("transfer_completed", viewModel.latestEvent.value?.event)
 * ```
 */
class FakePulseRealtime {

    // ── State ─────────────────────────────────────────────────────────────────

    private val _connectionState =
        MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    /**
     * The current connection state. Starts as [ConnectionState.Disconnected].
     * Drive it via [simulateConnected], [simulateDisconnected],
     * [simulateReconnecting], and [simulateFailed].
     */
    val connectionState: StateFlow<ConnectionState> =
        _connectionState.asStateFlow()

    // ── Recorded calls ────────────────────────────────────────────────────────

    private val _connectCalls = mutableListOf<Unit>()
    private val _disconnectCalls = mutableListOf<Unit>()
    private val _subscribedTopics = mutableListOf<String>()
    private val _cancelledTopics = mutableListOf<String>()

    /** How many times [connect] was called. */
    val connectCallCount: Int get() = _connectCalls.size

    /** How many times [disconnect] was called. */
    val disconnectCallCount: Int get() = _disconnectCalls.size

    /** Topics subscribed to in the order they were subscribed. */
    val subscribedTopics: List<String> get() = _subscribedTopics.toList()

    /** Topics whose subscriptions were cancelled, in order. */
    val cancelledTopics: List<String> get() = _cancelledTopics.toList()

    // ── Topic sinks ───────────────────────────────────────────────────────────

    // topic → MutableSharedFlow that FakeTopicSubscription exposes
    private val topicSinks =
        mutableMapOf<String, MutableSharedFlow<PulseEvent<String>>>()

    // ── Public API (mirrors PulseRealtime) ────────────────────────────────────

    /** Records the call. Does NOT automatically change [connectionState]. */
    fun connect() {
        _connectCalls.add(Unit)
    }

    /** Records the call. Does NOT automatically change [connectionState]. */
    fun disconnect() {
        _disconnectCalls.add(Unit)
    }

    /**
     * Subscribes to a topic and returns a [TopicSubscription].
     *
     * Creates an internal sink for this topic if one doesn't exist.
     * Deliver events to it via [simulateMessage].
     */
    fun subscribe(topic: String): TopicSubscription<String> {
        _subscribedTopics.add(topic)
        val sink = topicSinks.getOrPut(topic) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        }
        return FakeTopicSubscription(topic = topic, events = sink.asSharedFlow())
    }

    /** Removes a topic from the active subscriptions. */
    fun unsubscribe(topic: String) {
        topicSinks.remove(topic)
        if (!_cancelledTopics.contains(topic)) {
            _cancelledTopics.add(topic)
        }
    }

    // ── Simulation controls ───────────────────────────────────────────────────

    /**
     * Emits a [PulseEvent] to all active subscribers of [topic].
     *
     * Call this to simulate an incoming server message in your tests.
     *
     * @param topic   The topic to deliver the event to.
     * @param event   The event name (e.g. "transfer_completed").
     * @param payload The raw payload string (e.g. a JSON string).
     * @param ref     Optional message ID.
     */
    suspend fun simulateMessage(
        topic:   String,
        event:   String,
        payload: String = "{}",
        ref:     String? = null,
    ) {
        val sink = topicSinks[topic] ?: return
        sink.emit(
            PulseEvent(
                topic   = topic,
                event   = event,
                ref     = ref,
                payload = payload,
            )
        )
    }

    /**
     * Sets [connectionState] to [ConnectionState.Connected].
     * Use this to simulate a successful connection in your test setup.
     */
    fun simulateConnected() {
        _connectionState.value = ConnectionState.Connected
    }

    /**
     * Sets [connectionState] to [ConnectionState.Disconnected].
     * Use this to simulate the socket closing cleanly.
     */
    fun simulateDisconnected() {
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Sets [connectionState] to [ConnectionState.Reconnecting].
     * Use this to simulate a reconnect cycle in your test.
     *
     * @param attempt The attempt number shown in the state (1-based).
     */
    fun simulateReconnecting(attempt: Int = 1) {
        _connectionState.value = ConnectionState.Reconnecting(attempt)
    }

    /**
     * Sets [connectionState] to [ConnectionState.Failed].
     * Use this to simulate exhausted reconnect attempts.
     *
     * @param cause The exception to surface as the failure cause.
     */
    fun simulateFailed(cause: Throwable = Exception("Simulated failure")) {
        _connectionState.value = ConnectionState.Failed(cause)
    }

    /**
     * Resets all recorded calls and active subscriptions.
     * Call this in your test [org.junit.Before] or between test cases.
     */
    fun reset() {
        _connectCalls.clear()
        _disconnectCalls.clear()
        _subscribedTopics.clear()
        _cancelledTopics.clear()
        topicSinks.clear()
        _connectionState.value = ConnectionState.Disconnected
    }

    // ── FakeTopicSubscription ─────────────────────────────────────────────────

    private inner class FakeTopicSubscription(
        override val topic: String,
        override val events: SharedFlow<PulseEvent<String>>,
    ) : TopicSubscription<String> {

        override fun cancel() {
            _cancelledTopics.add(topic)
            topicSinks.remove(topic)
        }
    }
}