package com.vhuthu.pulse_logging

/**
 * Log severity levels used by PulseRealtime internally.
 *
 * Maps to standard Android log levels so [AndroidLogger] can
 * forward them directly to [android.util.Log].
 */
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

/**
 * Pluggable logging interface for PulseRealtime.
 *
 * Implement this interface to route SDK logs to your own logging
 * infrastructure (Timber, Firebase Crashlytics, your own logger, etc.).
 *
 *
 * ```kotlin
 * val pulse = PulseRealtime.Builder()
 *     .url("wss://api.example.com/socket")
 *     .logger(AndroidLogger)   // built-in
 *     .build()
 * ```
 *
 * The default logger is [NoOpLogger] — silent in production.
 * Enable [AndroidLogger] during development to see all SDK activity.
 */
interface PulseLogger {

    /**
     * Logs a message at the given [level].
     *
     * @param level   The severity of the log entry.
     * @param tag     The source tag (e.g. "PulseConnection", "PulseRouter").
     * @param message The log message.
     */
    fun log(level: LogLevel, tag: String, message: String)

    /**
     * Logs a message with an associated [throwable] at the given [level].
     *
     * @param level     The severity of the log entry.
     * @param tag       The source tag.
     * @param message   The log message.
     * @param throwable The exception or error to log.
     */
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable)
}