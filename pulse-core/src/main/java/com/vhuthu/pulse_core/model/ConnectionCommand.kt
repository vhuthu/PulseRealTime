package com.vhuthu.pulse_core.model

/**
 * Commands that can be sent to [ConnectionManager] via its command channel.
 *
 * Nothing outside ConnectionManager writes [ConnectionState] directly —
 * everything goes through one of these commands instead.
 *
 * Internal to the SDK. Never exposed in the public API.
 */
internal sealed interface ConnectionCommand {
    /** Open the socket. Ignored if already Connected or Connecting. */
    data object Connect : ConnectionCommand

    /**
     * Close the socket intentionally.
     * Clears the auto-reconnect flag — no retry will happen until
     * [Connect] is sent again explicitly.
     */
    data object Disconnect : ConnectionCommand

    /**
     * Close the socket temporarily (lifecycle-driven — app went to background).
     * Preserves the auto-reconnect flag so that when [Connect] arrives
     * again (foreground resume) everything restores automatically.
     */
    data object DisconnectTemporary : ConnectionCommand

    /**
     * Triggered internally when the socket drops unexpectedly.
     * Starts (or continues) the reconnect loop.
     * @param attempt The attempt number to start from (1-based).
     */
    data class ForceReconnect(val attempt: Int = 1) : ConnectionCommand
}