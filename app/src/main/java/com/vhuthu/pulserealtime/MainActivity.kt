package com.vhuthu.pulserealtime

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.vhuthu.pulse_android.bindToLifecycle
import com.vhuthu.pulse_core.PulseRealtime
import com.vhuthu.pulse_core.model.ConnectionState
import com.vhuthu.pulse_core.model.ExponentialBackoff
import com.vhuthu.pulse_logging.PrintLogger
import com.vhuthu.pulserealtime.ui.theme.PulseRealTimeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var pulse: PulseRealtime

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        //Sample demo to test , was using a mock websocket server I created using Node.js

        // INIT SDK
        // Build once — bind to lifecycle — done
        // No manual connect/disconnect needed anywhere else
        pulse = PulseRealtime.Builder()
            .url("ws://10.0.2.2:8080")
            .logger(PrintLogger)
            .reconnectPolicy(ExponentialBackoff(maxAttempts = 5))
            .build()
            .bindToLifecycle(this) // ← manages connect/disconnect automatically

        setContent {
            PulseRealTimeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    Greeting(
                        name = "Pulse SDK Test",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        // Observe connection state
        lifecycleScope.launch {
            pulse.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Disconnected  -> Log.d("PULSE", "State: Disconnected")
                    is ConnectionState.Connecting    -> Log.d("PULSE", "State: Connecting...")
                    is ConnectionState.Connected     -> Log.d("PULSE", "State: Connected ✓")
                    is ConnectionState.Reconnecting  -> Log.d("PULSE", "State: Reconnecting (attempt ${state.attempt})")
                    is ConnectionState.Failed        -> Log.e("PULSE", "State: Failed — ${state.cause.message}")
                }
            }
        }

        // Subscribe to a topic
        val sub = pulse.subscribe("payments")

        lifecycleScope.launch {
            sub.events.collect { event ->
                Log.d("PULSE", "─────────────────────────────")
                Log.d("PULSE", "Topic:   ${event.topic}")
                Log.d("PULSE", "Event:   ${event.event}")
                Log.d("PULSE", "Ref:     ${event.ref}")
                Log.d("PULSE", "Payload: ${event.payload}")
                Log.d("PULSE", "─────────────────────────────")
            }
        }
    }

    // No onDestroy needed — lifecycle binding handles disconnect automatically
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    PulseRealTimeTheme {
        Greeting("Android")
    }
}