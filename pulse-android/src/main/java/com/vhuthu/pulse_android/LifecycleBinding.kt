package com.vhuthu.pulse_android

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.vhuthu.pulse_core.PulseRealtime

/**
 * Binds a [PulseRealtime] instance to an Android [LifecycleOwner].
 *
 * When bound:
 * - **Foreground (onStart)** → calls [PulseRealtime.connect]
 * - **Background (onStop)**  → calls [PulseRealtime.disconnect] temporarily
 * - **Destroyed (onDestroy)**→ calls [PulseRealtime.disconnect] permanently
 *
 * This means the SDK automatically manages the connection based on
 * whether the user is actively using the app — no battery or network
 * wasted while the app is in the background.
 *
 * Subscriptions are preserved across background/foreground cycles.
 * The SDK replays them automatically when the socket reconnects.
 *
 * Usage:
 * ```kotlin
 * // In your Activity or Fragment
 * pulse.bindToLifecycle(this)
 * ```
 *
 * Or via the extension function on [PulseRealtime]:
 * ```kotlin
 * pulse.bindToLifecycle(lifecycleOwner)
 * ```
 */
class LifecycleBinding(
    private val pulse: PulseRealtime,
    private val owner: LifecycleOwner,
) : DefaultLifecycleObserver {

    init {
        owner.lifecycle.addObserver(this)
    }

    /**
     * App came to the foreground — open the connection.
     * Safe to call even if already connected (ConnectionManager guards this).
     */
    override fun onStart(owner: LifecycleOwner) {
        pulse.connect()
    }

    /**
     * App went to the background — close the connection temporarily.
     * Subscriptions remain in the desired set and will be replayed
     * when [onStart] fires again.
     */
    override fun onStop(owner: LifecycleOwner) {
        pulse.disconnectTemporary()
    }

    /**
     * Activity/Fragment is destroyed — remove the observer and
     * disconnect permanently so no resources are leaked.
     */
    override fun onDestroy(owner: LifecycleOwner) {
        owner.lifecycle.removeObserver(this)
        pulse.disconnect()
    }
}

/**
 * Binds this [PulseRealtime] instance to the given [LifecycleOwner].
 *
 * The connection is automatically managed based on the lifecycle:
 * - Foreground → connect
 * - Background → disconnect temporarily (subscriptions preserved)
 * - Destroyed  → disconnect permanently
 *
 * Example:
 * ```kotlin
 * class MainActivity : ComponentActivity() {
 *
 *     private lateinit var pulse: PulseRealtime
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *
 *         pulse = PulseRealtime.Builder()
 *             .url("wss://api.example.com/socket")
 *             .build()
 *             .bindToLifecycle(this)  // ← one line, fully managed
 *
 *         val sub = pulse.subscribe("payments.updates")
 *         lifecycleScope.launch {
 *             sub.events.collect { event -> ... }
 *         }
 *     }
 * }
 * ```
 *
 * @param owner The [LifecycleOwner] to bind to (Activity, Fragment, etc.)
 * @return This [PulseRealtime] instance for chaining.
 */
fun PulseRealtime.bindToLifecycle(owner: LifecycleOwner): PulseRealtime {
    LifecycleBinding(pulse = this, owner = owner)
    return this
}