package com.vhuthu.pulse_core.model

/**
 * A typed event delivered to the caller via [TopicSubscription.events].
 *
 * This is the deserialized form of one incoming WebSocket frame after
 * EventRouter has parsed the JSON envelope and dispatched by topic.
 *
 * @param T The type of the payload, as specified when calling
 *           [PulseRealtime.subscribe].
 * @param topic   The topic this event was delivered on.
 * @param event   The specific event name within the topic
 *                (e.g. "transfer_completed").
 * @param ref     Optional message ID. Present when the server includes one —
 *                used for ack correlation in Phase 3.
 * @param payload The deserialized payload of type [T].
 */
data class PulseEvent<T>(
    val topic: String,
    val event: String,
    val ref: String?,
    val payload: T
)
