package com.vhuthu.pulse_core.model

import kotlinx.coroutines.flow.SharedFlow

/**
 * A handle to an active topic subscription.
 *
 * Returned by [PulseRealtime.subscribe]. The caller collects [events]
 * in their own coroutine scope and calls [cancel] when done.
 *
 * Example usage:
 * ```kotlin
 * val sub = pulse.subscribe("payments.updates", PaymentEvent.serializer())
 * sub.events.collect { event ->
 *     println("${event.event}: ${event.payload}")
 * }
 * // Later:
 * sub.cancel()
 * ```
 *
 * @param T The deserialized payload type.
 */
interface TopicSubscription<T> {
    /** The topic string this subscription is bound to. */
    val topic: String

    /**
     * A hot [SharedFlow] that emits each [PulseEvent] as it arrives.
     *
     * - Use [SharedFlow] (not StateFlow) because events are one-shot —
     *   there is no meaningful "current value".
     * - Collect this in the caller's own CoroutineScope so lifecycle
     *   is controlled by the caller, not the SDK.
     */
    val events: SharedFlow<PulseEvent<T>>

    /**
     * Cancels this subscription.
     *
     * Sends an unsubscribe frame to the server and removes the topic
     * from SubscriptionManager's desired set. After calling this,
     * [events] will emit no further values.
     */
    fun cancel()
}