package com.vhuthu.pulse_core.model

/**
 * Events emitted on the internal SharedFlow bus.
 *
 * This is the only channel through which ConnectionManager, EventRouter,
 * and SubscriptionManager communicate. They never hold direct references
 * to each other — they only observe and emit these events.
 *
 * Internal to the SDK. Never exposed in the public API.
 */
internal sealed interface InternalEvent {
    /** The WebSocket handshake completed successfully. Socket is ready for use. */
    data object SocketOpened : InternalEvent

    /**
     * The server (or network) closed the connection cleanly.
     * @param code WebSocket close code (e.g. 1000 = normal closure).
     * @param reason Human-readable close reason from the server.
     */
    data class SocketClosed(
        val code: Int,
        val reason: String,
    ) : InternalEvent

    /**
     * The connection failed with an exception (e.g. network unreachable,
     * TLS error, timeout). Different from [SocketClosed] — no clean handshake.
     * @param cause The underlying exception.
     */
    data class SocketFailed(val cause: Throwable) : InternalEvent

    /**
     * A raw text frame arrived from the server.
     * EventRouter is responsible for deserializing and routing it.
     * @param raw The raw JSON string exactly as received.
     */
    data class FrameReceived(val raw: String) : InternalEvent
}