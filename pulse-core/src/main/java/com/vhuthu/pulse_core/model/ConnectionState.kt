package com.vhuthu.pulse_core.model

/**
 * Represents the current state of the WebSocket connection.
 *
 * Only [ConnectionManager] is allowed to write this state.
 * All other classes observe it — they never mutate it directly.
 */
sealed interface ConnectionState {
    /** No active connection. Either never connected, or intentionally disconnected. */
    data object Disconnected : ConnectionState

    /** A connection attempt is in progress. */
    data object Connecting : ConnectionState

    /** Socket is open and healthy. */
    data object Connected : ConnectionState

    /**
     * A previous connection was lost and the SDK is waiting before retrying.
     * @param attempt The current retry attempt number (1-based).
     */
    data class Reconnecting(val attempt: Int) : ConnectionState

    /**
     * All reconnect attempts have been exhausted. The SDK will not retry
     * automatically. The caller must explicitly call [PulseRealtime.connect].
     * @param cause The last exception that caused the failure.
     */
    data class Failed(val cause: Throwable) : ConnectionState
}