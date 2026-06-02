package com.vhuthu.pulse_logging

/**
 * A [PulseLogger] that silently discards all log entries.
 *
 * This is the default logger used by PulseRealtime when no logger
 * is configured — meaning the SDK produces zero log output in
 * production unless you explicitly opt in.
 *
 * This is intentional: SDK logs should never appear in a consumer's
 * production app without their knowledge.
 */
object NoOpLogger : PulseLogger {
    override fun log(level: LogLevel, tag: String, message: String) = Unit
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable) = Unit
}