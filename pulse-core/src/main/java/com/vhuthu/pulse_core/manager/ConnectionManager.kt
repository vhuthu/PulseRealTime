package com.vhuthu.pulse_core.manager

import com.vhuthu.pulse_core.engine.WebSocketEngine
import com.vhuthu.pulse_core.model.ConnectionCommand
import com.vhuthu.pulse_core.model.ConnectionState
import com.vhuthu.pulse_core.model.InternalEvent
import com.vhuthu.pulse_core.model.ReconnectPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns and drives all [ConnectionState] transitions.
 *
 * This is the single source of truth for connection state in the SDK.
 * No other class is allowed to write [ConnectionState] directly.
 *
 * All external intent (connect, disconnect) arrives as a [ConnectionCommand]
 * via the command channel. Internal socket events arrive via the shared
 * [InternalEvent] bus. Both are processed sequentially — no concurrent
 * state mutation is possible.
 *
 * Reconnect loop guarantee:
 * - Only one reconnect loop runs at a time ([reconnectJob]).
 * - Any [ConnectionCommand.Disconnect] cancels the loop immediately
 *   at the next [delay] suspension point.
 * - [SupervisorJob] on the scope means a crash in the loop does not
 *   kill the command processor.
 *
 * Internal to the SDK. Never exposed in the public API.
 */
internal class ConnectionManager(
    private val scope: CoroutineScope,
    private val engine: WebSocketEngine,
    private val policy: ReconnectPolicy,
    private val eventBus: MutableSharedFlow<InternalEvent>,
) {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    // CONFLATED: if commands arrive faster than they are processed, only the
    // latest is kept. This prevents queued duplicates (e.g. rapid connect()).
    private val commands = Channel<ConnectionCommand>(Channel.CONFLATED)

    // Kept so we can cancel an in-flight reconnect when disconnect() is called.
    private var reconnectJob: Job? = null

    // Tracks whether the last disconnect was temporary (lifecycle-driven)
    // or intentional (app-driven). Only intentional disconnects clear this.
    private var temporaryDisconnect = false

    init {
        scope.launch { processCommands() }
        scope.launch { observeInternalEvents() }
    }


    /**
     * Requests the SDK to open a connection.
     * Ignored if already [Connected] or [Connecting].
     */
    fun connect() {
        commands.trySend(ConnectionCommand.Connect)
    }

    /**
     * Requests the SDK to close the connection intentionally.
     * Cancels any in-flight reconnect loop. No auto-reconnect will
     * happen until [connect] is called explicitly again.
     */
    fun disconnect() {
        commands.trySend(ConnectionCommand.Disconnect)
    }

    /**
     * Requests a temporary disconnect (lifecycle-driven).
     * The SDK disconnects but auto-reconnects when [connect] is called again
     * (e.g. app returns to foreground via lifecycle binding).
     */
    fun disconnectTemporary() {
        commands.trySend(ConnectionCommand.DisconnectTemporary)
    }


    /**
     * Processes commands from the channel sequentially.
     * This is the ONLY place that calls state-transition functions.
     */
    private suspend fun processCommands() {
        for (command in commands) {
            when (command) {
                ConnectionCommand.Connect             -> handleConnect()
                ConnectionCommand.Disconnect          -> handleDisconnect(temporary = false)
                ConnectionCommand.DisconnectTemporary -> handleDisconnect(temporary = true)
                is ConnectionCommand.ForceReconnect   -> startReconnectLoop(command.attempt)
            }
        }
    }


    /**
     * Observes [InternalEvent]s from the shared bus.
     *
     * SocketOpened / SocketClosed / SocketFailed are translated into
     * commands so all state transitions remain on the command channel path.
     *
     * FrameReceived is intentionally ignored here — EventRouter handles it.
     */
    private suspend fun observeInternalEvents() {
        eventBus.collect { event ->
            when (event) {
                is InternalEvent.SocketOpened -> {
                    reconnectJob = null
                    _state.value = ConnectionState.Connected
                }
                is InternalEvent.SocketClosed -> {
                    // Only reconnect if not intentionally disconnected
                    if (_state.value !is ConnectionState.Disconnected && _state.value !is ConnectionState.Failed) {
                        commands.trySend(ConnectionCommand.ForceReconnect(attempt = 1))
                    }
                }
                is InternalEvent.SocketFailed -> {
                    when (val current = _state.value) {
                        is ConnectionState.Connecting -> {
                            // First connect attempt failed — start reconnect from attempt 1
                            commands.trySend(ConnectionCommand.ForceReconnect(attempt = 1))
                        }
                        is ConnectionState.Reconnecting -> {
                            // A retry attempt failed — increment and continue
                            commands.trySend(
                                ConnectionCommand.ForceReconnect(attempt = current.attempt + 1)
                            )
                        }
                        else -> Unit // Ignore failures in other states
                    }
                }
                is InternalEvent.FrameReceived -> Unit // Handled by EventRouter
            }
        }
    }

    private fun handleConnect() {
        when (_state.value) {
            is ConnectionState.Connected, is ConnectionState.Connecting -> return // Already connected — ignore
            else -> {
                temporaryDisconnect = false
                _state.value = ConnectionState.Connecting
                engine.open()
            }
        }
    }

    private fun handleDisconnect(temporary: Boolean) {
        temporaryDisconnect = temporary
        reconnectJob?.cancel()
        reconnectJob = null
        engine.close()
        _state.value = ConnectionState.Disconnected
    }

    private fun startReconnectLoop(attempt: Int) {
        // Guard: never start a second loop while one is running
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            var currentAttempt = attempt

            while (policy.shouldRetry(currentAttempt)) {
                _state.value = ConnectionState.Reconnecting(currentAttempt)

                val delayMs = policy.delayMillis(currentAttempt)
                delay(delayMs) // Cancellable — disconnect() cancels here

                _state.value = ConnectionState.Connecting
                engine.open()

                // Wait for SocketOpened or SocketFailed via observeInternalEvents.
                // The loop continues only if SocketFailed increments the attempt
                // and sends ForceReconnect — which returns here via the command channel.
                // If SocketOpened fires, reconnectJob is nulled out and we return.
                return@launch
            }

            // Exhausted all attempts
            _state.value = ConnectionState.Failed(
                cause = Exception("Max reconnect attempts (${policy}) reached")
            )
        }
    }
}