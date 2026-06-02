package com.vhuthu.pulse_core

import com.vhuthu.pulse_core.engine.WebSocketEngine
import com.vhuthu.pulse_core.manager.ConnectionManager
import com.vhuthu.pulse_core.manager.SubscriptionManager
import com.vhuthu.pulse_core.model.ConnectionState
import com.vhuthu.pulse_core.model.ExponentialBackoff
import com.vhuthu.pulse_core.model.InternalEvent
import com.vhuthu.pulse_core.model.ReconnectPolicy
import com.vhuthu.pulse_core.model.TopicSubscription
import com.vhuthu.pulse_core.router.EventRouter
import com.vhuthu.pulse_logging.NoOpLogger
import com.vhuthu.pulse_core.logger.PulseLog
import com.vhuthu.pulse_logging.PulseLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

/**
 * The main entry point for the PulseRealtime SDK.
 *
 * Create an instance via [Builder] and hold onto it for the lifetime
 * of your connection. All internal managers are wired together here.
 *
 * Example usage:
 * ```kotlin
 * val pulse = PulseRealtime.Builder()
 *     .url("wss://example.com/socket")
 *     .reconnectPolicy(ExponentialBackoff(maxAttempts = 5))
 *     .build()
 *
 * pulse.connect()
 *
 * val sub = pulse.subscribe("payments.updates")
 * sub.events.collect { event ->
 *     println("${event.event}: ${event.payload}")
 * }
 *
 * // Later:
 * pulse.disconnect()
 * ```
 */
class PulseRealtime private constructor(
    private val connectionManager: ConnectionManager,
    private val subscriptionManager: SubscriptionManager,
) {

    // ── Public state ──────────────────────────────────────────────────────────

    /**
     * The current connection state as a [StateFlow].
     *
     * Collect this in your ViewModel or UI layer to react to connection
     * changes (e.g. show a reconnecting indicator).
     *
     * Always emits the latest state immediately to new collectors —
     * no event is ever missed.
     */
    val connectionState: StateFlow<ConnectionState>
        get() = connectionManager.state

    // ── Connection control ────────────────────────────────────────────────────

    /**
     * Opens the WebSocket connection.
     *
     * Safe to call multiple times — ignored if already [ConnectionState.Connected]
     * or [ConnectionState.Connecting].
     */
    fun connect() {
        connectionManager.connect()
    }

    /**
     * Closes the WebSocket connection intentionally.
     *
     * Cancels any in-flight reconnect loop. The SDK will not attempt
     * to reconnect automatically until [connect] is called again.
     */
    fun disconnect() {
        connectionManager.disconnect()
    }

    /**
     * Closes the connection temporarily (lifecycle-driven).
     * The SDK will auto-reconnect when [connect] is called again.
     * Subscriptions are preserved and replayed on reconnect.
     *
     * Prefer [bindToLifecycle] over calling this directly.
     */
    fun disconnectTemporary() {
        connectionManager.disconnectTemporary()
    }

    // ── Subscriptions ─────────────────────────────────────────────────────────

    /**
     * Subscribes to a topic and returns a [TopicSubscription].
     *
     * The subscription is automatically restored after a reconnect —
     * no manual resubscription is needed.
     *
     * Collect [TopicSubscription.events] in your own [CoroutineScope]
     * (e.g. `viewModelScope`) so the lifecycle is controlled by the caller.
     *
     * Call [TopicSubscription.cancel] when you no longer need the subscription.
     *
     * @param topic The topic string to subscribe to (e.g. "payments.updates").
     * @return A [TopicSubscription] whose events flow is ready to collect.
     */
    fun subscribe(topic: String): TopicSubscription<String> {
        return subscriptionManager.subscribe(topic)
    }

    /**
     * Unsubscribes from a topic by name.
     *
     * Equivalent to calling [TopicSubscription.cancel] on the subscription
     * returned by [subscribe]. Prefer using [TopicSubscription.cancel] directly
     * when you have the reference.
     *
     * @param topic The topic string to unsubscribe from.
     */
    fun unsubscribe(topic: String) {
        subscriptionManager.unsubscribe(topic)
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /**
     * Constructs a [PulseRealtime] instance.
     *
     * Required:
     * - [url] — the WebSocket server URL (must be ws:// or wss://)
     *
     * Optional:
     * - [reconnectPolicy] — defaults to [ExponentialBackoff] with 5 attempts
     * - [okHttpClient] — provide your own if you need custom timeouts or interceptors
     */
    class Builder {

        private var url: String? = null
        private var reconnectPolicy: ReconnectPolicy = ExponentialBackoff()
        private var okHttpClient: OkHttpClient = OkHttpClient()

        private var logger: PulseLogger = NoOpLogger

        /**
         * Sets the WebSocket server URL.
         * Must start with ws:// or wss://.
         */
        fun url(url: String): Builder = apply {
            this.url = url
        }

        /**
         * Sets the reconnect policy.
         * Defaults to [ExponentialBackoff] with 5 attempts and 1s initial delay.
         */
        fun reconnectPolicy(policy: ReconnectPolicy): Builder = apply {
            this.reconnectPolicy = policy
        }

        /**
         * Provides a custom [OkHttpClient].
         * Use this to configure timeouts, interceptors, or certificates.
         * If not set, a default client with no customisation is used.
         */
        fun okHttpClient(client: OkHttpClient): Builder = apply {
            this.okHttpClient = client
        }

        /**
         * Sets the logger for the SDK.
         *
         * During development use [PrintLogger] for JVM or AndroidLogger
         * from :pulse-android for Logcat output.
         *
         * Defaults to [NoOpLogger] — silent in production.
         *
         * ```kotlin
         * PulseRealtime.Builder()
         *     .url("wss://api.example.com/socket")
         *     .logger(PrintLogger)   // see all SDK logs
         *     .build()
         * ```
         */
        fun logger(logger: PulseLogger): Builder = apply {
            this.logger = logger
        }

        /**
         * Builds the [PulseRealtime] instance.
         *
         * @throws IllegalStateException if [url] was not set.
         */
        fun build(): PulseRealtime {
            val resolvedUrl = checkNotNull(url) {
                "PulseRealtime.Builder: url() must be called before build()"
            }

            // Wire up the logger before anything else runs
            PulseLog.setLogger(logger)

            // Shared coroutine scope — SupervisorJob so child failures are isolated
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            // Shared internal event bus — all managers communicate through this
            val eventBus = MutableSharedFlow<InternalEvent>(
                replay = 1,
                extraBufferCapacity = 64,
            )

            val engine = WebSocketEngine(
                url = resolvedUrl,
                httpClient = okHttpClient,
                eventBus = eventBus,
                scope = scope,
            )

            val router = EventRouter(
                scope = scope,
                eventBus = eventBus,
            )

            val connectionManager = ConnectionManager(
                scope = scope,
                engine = engine,
                policy = reconnectPolicy,
                eventBus = eventBus,
            )

            val subscriptionManager = SubscriptionManager(
                scope = scope,
                engine = engine,
                router = router,
                eventBus = eventBus,
            )

            return PulseRealtime(
                connectionManager = connectionManager,
                subscriptionManager = subscriptionManager,
            )
        }
    }
}