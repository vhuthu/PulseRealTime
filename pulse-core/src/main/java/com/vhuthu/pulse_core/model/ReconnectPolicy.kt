package com.vhuthu.pulse_core.model

import kotlin.math.min
import kotlin.math.pow

/**
 * Controls if and how the SDK retries a dropped connection.
 *
 * Pass a policy to [PulseRealtime.Builder.reconnectPolicy].
 * The SDK ships two built-in implementations:
 * - [ExponentialBackoff] — retries with increasing delays (recommended).
 * - [NoReconnect] — never retries; useful in tests or controlled environments.
 *
 */
interface ReconnectPolicy {
    /**
     * Returns true if the SDK should attempt another connection.
     * Called before each retry attempt.
     * @param attempt The upcoming attempt number (1-based).
     */
    fun shouldRetry(attempt: Int): Boolean

    /**
     * Returns how long to wait (in milliseconds) before the given attempt.
     * Only called when [shouldRetry] returns true.
     * @param attempt The upcoming attempt number (1-based).
     */
    fun delayMillis(attempt: Int): Long
}

/**
 * Retries with exponentially increasing delays, capped at [maxDelayMillis].
 *
 * Delay formula: min(initialDelayMillis * multiplier^(attempt-1), maxDelayMillis)
 *
 * Example with defaults:
 * - Attempt 1 → 1 000 ms
 * - Attempt 2 → 2 000 ms
 * - Attempt 3 → 4 000 ms
 * - Attempt 4 → 8 000 ms
 * - Attempt 5 → 16 000 ms
 * - Attempt 6 → max attempts reached → Failed
 *
 * @param maxAttempts      Maximum number of retry attempts before giving up.
 * @param initialDelayMillis Delay before the first retry in milliseconds.
 * @param multiplier       Factor by which the delay grows each attempt.
 * @param maxDelayMillis   Hard ceiling on delay regardless of multiplier.
 */
class ExponentialBackoff(
    val maxAttempts: Int = 5,
    val initialDelayMillis: Long = 1_000L,
    val multiplier: Double = 2.0,
    val maxDelayMillis: Long = 30_000L,
) : ReconnectPolicy {

    init {
        require(maxAttempts > 0) { "maxAttempts must be > 0, was $maxAttempts" }
        require(initialDelayMillis > 0) { "initialDelayMillis must be > 0, was $initialDelayMillis" }
        require(multiplier >= 1.0) { "multiplier must be >= 1.0, was $multiplier" }
        require(maxDelayMillis >= initialDelayMillis) {
            "maxDelayMillis ($maxDelayMillis) must be >= initialDelayMillis ($initialDelayMillis)"
        }
    }

    override fun shouldRetry(attempt: Int): Boolean = attempt <= maxAttempts

    override fun delayMillis(attempt: Int): Long {
        val raw = initialDelayMillis * multiplier.pow(attempt - 1)
        return min(raw, maxDelayMillis.toDouble()).toLong()
    }
}

/**
 * Never retries. The SDK moves immediately to [ConnectionState.Failed]
 * on any socket drop.
 *
 * Useful for:
 * - Unit tests where you want to control reconnect behaviour explicitly.
 * - Environments where the caller manages reconnection itself.
 */
object NoReconnect : ReconnectPolicy {
    override fun shouldRetry(attempt: Int): Boolean = false
    override fun delayMillis(attempt: Int): Long = 0L
}