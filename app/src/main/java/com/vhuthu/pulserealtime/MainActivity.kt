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
import com.vhuthu.pulse_core.PulseRealtime
import com.vhuthu.pulserealtime.ui.theme.PulseRealTimeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var pulse: PulseRealtime

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        //Sample demo to test , was using a mock websocket server I created using Node.js

        // 1. INIT SDK
        pulse = PulseRealtime.Builder()
            .url("ws://10.0.2.2:8080")
            .build()

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

        // 2. CONNECT SDK
        pulse.connect()

        // 3. COLLECT CONNECTION STATE
        lifecycleScope.launch {
            pulse.connectionState.collect { state ->
                Log.d("PULSE", "Connection State = $state")
            }
        }

        // 4. COLLECT EVENTS
        val subscription = pulse.subscribe("payments")

        lifecycleScope.launch {
            subscription.events.collect { event ->
                Log.d(
                    "PULSE",
                    "Event = ${event.event}, payload = ${event.payload}"
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::pulse.isInitialized) {
            pulse.disconnect()
        }
    }
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