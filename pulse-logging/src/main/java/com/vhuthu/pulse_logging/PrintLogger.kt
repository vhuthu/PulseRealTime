package com.vhuthu.pulse_logging

/**
 * A [PulseLogger] that prints all log entries to standard output.
 *
 * Use this in pure JVM environments — unit tests, backend services,
 * or desktop apps — where [android.util.Log] is not available.
 *
 * Output format:
 * ```
 * [DEBUG] PulseConnection: Socket opened successfully
 * [ERROR] PulseRouter: Failed to parse frame — invalid JSON
 *   java.lang.Exception: Unexpected token at position 0
 * ```
 *
 * For Android apps use the AndroidLogger from :pulse-android instead,
 * which routes output to Logcat.
 */
object PrintLogger : PulseLogger {

    override fun log(level: LogLevel, tag: String, message: String) {
        println("[${level.name}] $tag: $message")
    }

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable) {
        println("[${level.name}] $tag: $message")
        throwable.printStackTrace()
    }
}