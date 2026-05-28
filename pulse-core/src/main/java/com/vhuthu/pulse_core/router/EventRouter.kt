package com.vhuthu.pulse_core.router

import com.vhuthu.pulse_core.model.InternalEvent
import com.vhuthu.pulse_core.model.PulseEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

/**
 * Listens to [InternalEvent.FrameReceived] on the shared bus,
 * parses each raw JSON frame into a [RawEnvelope], and dispatches
 * it to the correct topic sink.
 *
 * Topic sinks are registered by [SubscriptionManager] via [registerTopic]
 * and removed via [unregisterTopic]. Each sink is a [MutableSharedFlow]
 * that the corresponding [TopicSubscription] exposes to the caller.
 *
 * Serialization strategy (Phase 1):
 * The router parses only the envelope fields (topic, event, ref).
 * The payload is kept as a raw JSON string and passed downstream.
 * Typed deserialization (Kotlinx Serialization) is a :pulse-serialization
 * concern and will be plugged in via a transformer in a later phase.
 *
 * Internal to the SDK. Never exposed in the public API.
 */
internal class EventRouter(
    private val scope: CoroutineScope,
    private val eventBus: MutableSharedFlow<InternalEvent>,
) {

    private val topicSinks =
        mutableMapOf<String, MutableSharedFlow<PulseEvent<String>>>()


    init {
        scope.launch { observeFrames() }
    }

    /**
     * Registers a new topic and returns the [SharedFlow] that will emit
     * events for it. Called by [SubscriptionManager] when the app subscribes.
     *
     * If the topic is already registered the existing sink is returned —
     * multiple callers can share the same topic flow.
     *
     * @param topic The topic string (e.g. "payments.updates").
     * @return A [SharedFlow] that emits [PulseEvent] for this topic.
     */
    fun registerTopic(topic: String): SharedFlow<PulseEvent<String>> {
        return topicSinks.getOrPut(topic) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        }.asSharedFlow()
    }

    /**
     * Unregisters a topic. Future frames for this topic are silently dropped.
     * Called by [SubscriptionManager] when the app unsubscribes.
     *
     * @param topic The topic string to remove.
     */
    fun unregisterTopic(topic: String) {
        topicSinks.remove(topic)
    }

    private suspend fun observeFrames() {
        eventBus.collect { event ->
            if (event is InternalEvent.FrameReceived) {
                handleFrame(event.raw)
            }
        }
    }

    /**
     * Parses a raw JSON frame and dispatches it to the matching topic sink.
     *
     * Malformed frames are silently dropped — a bad server message should
     * never crash the SDK or disconnect the caller.
     *
     * Expected envelope shape:
     * {
     *   "topic":   "payments.updates",
     *   "event":   "transfer_completed",
     *   "ref":     "abc-123",
     *   "payload": { }
     * }
     */
    private fun handleFrame(raw: String) {
        val envelope = parseEnvelope(raw) ?: return

        val sink = topicSinks[envelope.topic] ?: return

        val pulseEvent = PulseEvent(
            topic = envelope.topic,
            event = envelope.event,
            ref = envelope.ref,
            payload = envelope.payload,
        )

        // tryEmit is safe here: extraBufferCapacity = 64 means we never block.
        // If the buffer is full the event is dropped rather than suspending
        // the collection loop.
        sink.tryEmit(pulseEvent)
    }


    /**
     * Parses a raw JSON string into a [RawEnvelope].
     * Returns null if the JSON is malformed or required fields are missing.
     */
    private fun parseEnvelope(raw: String): RawEnvelope? {
        return try {
            val json    = JSONObject(raw)
            val topic   = json.optString("topic").takeIf { it.isNotBlank() } ?: return null
            val event   = json.optString("event").takeIf { it.isNotBlank() } ?: return null
            val ref     = json.optString("ref").takeIf { it.isNotBlank() }
            val payload = json.optString("payload").ifBlank { "{}" }

            RawEnvelope(topic = topic, event = event, ref = ref, payload = payload)
        } catch (e: JSONException) {
            null
        }
    }

    /**
     * Intermediate representation of a parsed JSON envelope.
     * Payload is kept as a raw string until typed deserialization
     * is applied by the :pulse-serialization layer.
     */
    private data class RawEnvelope(
        val topic:   String,
        val event:   String,
        val ref:     String?,
        val payload: String,
    )
}