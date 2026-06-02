package com.vhuthu.pulse_core.logger

import com.vhuthu.pulse_logging.LogLevel
import com.vhuthu.pulse_logging.NoOpLogger
import com.vhuthu.pulse_logging.PulseLogger

/**
 * Internal logging facade used by all PulseRealtime components.
 *
 * Instead of passing a [com.vhuthu.pulse_logging.PulseLogger] reference into every class,
 * components call this singleton. The active logger is set once
 *
 * Internal to the SDK. Never exposed in the public API.
 *
 * Usage inside any SDK class:
 * ```kotlin
 * PulseLog.d(TAG, "Socket opened")
 * PulseLog.e(TAG, "Connection failed", throwable)
 * ```
 */
object PulseLog {

    @Volatile
    private var logger: PulseLogger = NoOpLogger

    /**
     * Sets the active logger. Called once during
     */
    fun setLogger(logger: PulseLogger) {
        this.logger = logger
    }

    fun v(tag: String, message: String) =
        logger.log(LogLevel.VERBOSE, tag, message)

    fun d(tag: String, message: String) =
        logger.log(LogLevel.DEBUG, tag, message)

    fun i(tag: String, message: String) =
        logger.log(LogLevel.INFO, tag, message)

    fun w(tag: String, message: String) =
        logger.log(LogLevel.WARN, tag, message)

    fun e(tag: String, message: String) =
        logger.log(LogLevel.ERROR, tag, message)

    fun e(tag: String, message: String, throwable: Throwable) =
        logger.log(LogLevel.ERROR, tag, message, throwable)

    fun w(tag: String, message: String, throwable: Throwable) =
        logger.log(LogLevel.WARN, tag, message, throwable)
}