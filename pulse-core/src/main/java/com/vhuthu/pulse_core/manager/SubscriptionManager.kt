package com.vhuthu.pulse_core.manager

import com.vhuthu.pulse_core.engine.WebSocketEngine
import com.vhuthu.pulse_core.model.InternalEvent
import com.vhuthu.pulse_core.model.PulseEvent
import com.vhuthu.pulse_core.model.TopicSubscription
import com.vhuthu.pulse_core.router.EventRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Manages the lifecycle of topic subscriptions.
 *
 * Responsibilities:
 * - Track desired subscriptions (what the app has asked for)
 * - Track confirmed subscriptions (what the server has acknowledged)
 * - Send subscribe/unsubscribe frames to the server via [WebSocketEngine]
 * - Replay all desired subscriptions after a reconnect (SocketOpened)
 * - Register/unregister topics with [EventRouter]
 *
 * The app never needs to resubscribe manually after a reconnect —
 * this class handles it automatically by observing [InternalEvent.SocketOpened].
 *
 * Internal to the SDK. Never exposed in the public API.
 */
internal class SubscriptionManager(
    private val scope: CoroutineScope,
    private val engine: WebSocketEngine,
    private val router: EventRouter,
    private val eventBus: MutableSharedFlow<InternalEvent>,
) {
    private val desiredTopics = mutableSetOf<String>()

    private val confirmedTopics = mutableSetOf<String>()


    init {
        scope.launch { observeInternalEvents() }
    }


    /**
     * Subscribes to a topic.
     *
     * - Adds the topic to [desiredTopics]
     * - Registers it with [EventRouter] to start receiving frames
     * - Sends a subscribe frame to the server if connected
     * - Returns a [TopicSubscription] the caller holds onto
     *
     * @param topic The topic string (e.g. "payments.updates").
     * @return A [TopicSubscription] whose [TopicSubscription.events] emits
     *         incoming [PulseEvent]s for this topic.
     */
    fun subscribe(topic: String): TopicSubscription<String> {
        desiredTopics.add(topic)
        val flow = router.registerTopic(topic)
        sendSubscribeFrame(topic)
        return PulseTopicSubscription(topic = topic, events = flow)
    }

    /**
     * Unsubscribes from a topic.
     *
     * - Removes the topic from both [desiredTopics] and [confirmedTopics]
     * - Unregisters it from [EventRouter]
     * - Sends an unsubscribe frame to the server if connected
     *
     * @param topic The topic string to unsubscribe from.
     */
    fun unsubscribe(topic: String) {
        desiredTopics.remove(topic)
        confirmedTopics.remove(topic)
        router.unregisterTopic(topic)
        sendUnsubscribeFrame(topic)
    }


    private suspend fun observeInternalEvents() {
        eventBus.collect { event ->
            when (event) {
                is InternalEvent.SocketOpened -> onSocketOpened()
                is InternalEvent.SocketClosed -> onSocketClosed()
                is InternalEvent.SocketFailed -> onSocketClosed()
                is InternalEvent.FrameReceived -> onFrameReceived(event.raw)
            }
        }
    }

    /**
     * Called when the socket opens (including after a reconnect).
     * Replays all desired subscriptions to the server so the app
     * never needs to resubscribe manually.
     */
    private fun onSocketOpened() {
        confirmedTopics.clear()
        desiredTopics.forEach { topic ->
            sendSubscribeFrame(topic)
        }
    }

    /**
     * Called when the socket closes (clean or failure).
     * Marks all confirmed subscriptions as unconfirmed — they will be
     * replayed when [onSocketOpened] fires after reconnect.
     */
    private fun onSocketClosed() {
        confirmedTopics.clear()
    }

    /**
     * Called when a raw frame arrives. Checks if it is a subscription
     * acknowledgement and promotes the topic to [confirmedTopics].
     */
    private fun onFrameReceived(raw: String) {
        try {
            val json = JSONObject(raw)
            val type = json.optString("type")
            val topic = json.optString("topic").takeIf { it.isNotBlank() } ?: return

            when (type) {
                "subscribed"   -> confirmedTopics.add(topic)
                "unsubscribed" -> confirmedTopics.remove(topic)
            }
        } catch (e: Exception) {
            // Malformed ack — ignore silently
        }
    }


    private fun sendSubscribeFrame(topic: String) {
        val frame = JSONObject()
            .put("type", "subscribe")
            .put("topic", topic)
            .toString()
        engine.send(frame)
    }

    private fun sendUnsubscribeFrame(topic: String) {
        val frame = JSONObject()
            .put("type", "unsubscribe")
            .put("topic", topic)
            .toString()
        engine.send(frame)
    }


    /**
     * Internal implementation of [TopicSubscription].
     * Holds the topic string and the flow of events.
     * [cancel] delegates to [SubscriptionManager.unsubscribe].
     */
    private inner class PulseTopicSubscription(
        override val topic: String,
        override val events: SharedFlow<PulseEvent<String>>,
    ) : TopicSubscription<String> {

        override fun cancel() {
            unsubscribe(topic)
        }
    }
}