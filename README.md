# ⚡ PulseRealtime

**A modular, lifecycle-aware, coroutine-first WebSocket SDK for Android**

![Phase](https://img.shields.io/badge/Phase-1%20Complete-6C63FF?style=for-the-badge)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-F5A623?style=for-the-badge&logo=kotlin)
![Coroutines](https://img.shields.io/badge/Coroutines-1.8.1-00B4A6?style=for-the-badge)
![OkHttp](https://img.shields.io/badge/OkHttp-4.12.0-FF6B6B?style=for-the-badge)
![Tests](https://img.shields.io/badge/Tests-35%20passing-4CAF50?style=for-the-badge)

*Built from scratch. No Socket.IO. No Ably. Pure engineering.*

</div>

---

## What is PulseRealtime?

PulseRealtime is an open-source Android SDK that abstracts WebSocket communication behind a clean, coroutine-first API. It handles the hard parts — connection management, automatic reconnection, topic-based subscriptions, and subscription restoration after reconnects — so you never have to again.

```kotlin
val pulse = PulseRealtime.Builder()
    .url("wss://api.example.com/socket")
    .reconnectPolicy(ExponentialBackoff(maxAttempts = 5))
    .build()

pulse.connect()

val sub = pulse.subscribe("payments.updates")
sub.events.collect { event ->
    println("${event.event}: ${event.payload}")
}
```

---

## Why this exists

Modern Android apps need realtime communication. Teams repeatedly build the same things — connection management, retry logic, lifecycle handling, subscription restoration — and get them wrong in subtle, hard-to-reproduce ways.

PulseRealtime solves this once, correctly, with a clean API surface and a fully tested internals.

---

## Project status

| Phase | What | Status |
|-------|------|--------|
| **Phase 1** | Core SDK — connect, reconnect, subscribe, typed events | ✅ **Complete** |
| Phase 2 | Buffering, offline queueing, metrics, logging hooks | 🔜 Planned |
| Phase 3 | Encryption, interceptors, acknowledgements, presence | 🔜 Planned |
| Phase 4 | Kotlin Multiplatform — iOS and desktop support | 🔜 Planned |

---

## Architecture

### High-level layers

```
┌─────────────────────────────────────────────────────┐
│                  Your Application                   │
│         Activity · ViewModel · Composable           │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│              PulseRealtime  (Public API)            │
│   connect() · disconnect() · subscribe() · state    │
└──────────┬─────────────┬──────────────┬─────────────┘
           │             │              │
┌──────────▼───┐  ┌──────▼──────┐  ┌───▼────────────── ┐
│  Connection  │  │   Event     │  │  Subscription     │
│  Manager     │  │   Router    │  │  Manager          │
│              │  │             │  │                   │
│ State machine│  │ JSON parse  │  │ Topic tracking    │
│ Reconnect    │  │ Topic route │  │ Resubscribe on    │
│ Heartbeat    │  │ Dispatch    │  │ reconnect         │
└──────┬───────┘  └──────┬──────┘  └───────────────────┘
       │                 │
       └────────┬────────┘
                │   SharedFlow<InternalEvent>  (internal bus)
┌───────────────▼─────────────────────────────────────┐
│                  WebSocket Engine                   │
│           OkHttp wrapper · frame I/O                │
└───────────────┬─────────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────────┐
│                  OkHttp WebSocket                   │
│                  (platform transport)               │
└─────────────────────────────────────────────────────┘
```

### Internal communication — the event bus

The three internal managers never hold references to each other. They communicate exclusively through a shared `SharedFlow<InternalEvent>` bus.

```
OkHttp callback fires
       │
       ▼
WebSocketEngine.emit(FrameReceived | SocketOpened | SocketClosed | SocketFailed)
       │
       ▼
SharedFlow<InternalEvent>  ◄──── the internal bus
       │
       ├──► ConnectionManager  (drives ConnectionState transitions)
       ├──► EventRouter        (deserializes frames, routes to topics)
       └──► SubscriptionManager (tracks acks, replays on reconnect)
```

This decoupling means each manager is independently testable — no mocking of other managers required.

### Connection state machine

```
                    connect()
                        │
             ┌──────────▼──────────┐
             │      Connecting     │
             └──────────┬──────────┘
                        │
          ┌─────────────┴──────────────┐
          │ socket open                │ socket failed
          ▼                            ▼
┌─────────────────┐        ┌──────────────────────┐
│    Connected    │        │  Reconnecting(n)     │
└────────┬────────┘        │                      │
         │                 │  delay → retry       │
         │ drop/failure    │  if n > maxAttempts  │
         └────────────────►│  → Failed            │
                           └──────────────────────┘
         disconnect()
         at any time → Disconnected
```

### Wire format

Every WebSocket frame uses a JSON envelope:

```json
{
  "topic":   "payments.updates",
  "event":   "transfer_completed",
  "ref":     "abc-123",
  "payload": { "amount": 500, "currency": "ZAR" }
}
```

Subscribe/unsubscribe protocol (server-side):

```json
// Client → Server
{ "type": "subscribe",   "topic": "payments.updates", "ref": "ref-001" }

// Server → Client (acknowledgement)
{ "type": "subscribed",  "topic": "payments.updates", "ref": "ref-001" }
```

---

## Module structure

```
pulse-realtime/
├── pulse-core/                    ← Pure Kotlin. Zero Android deps. KMP-ready.
│   └── src/main/kotlin/io/github/vhuthu/pulse/core/
│       ├── PulseRealtime.kt       ← Public API + Builder
│       ├── ConnectionState.kt     ← sealed interface (5 states)
│       ├── PulseEvent.kt          ← typed event delivered to caller
│       ├── TopicSubscription.kt   ← interface the caller holds
│       ├── ReconnectPolicy.kt     ← interface + ExponentialBackoff + NoReconnect
│       ├── InternalEvent.kt       ← internal bus payload (sealed interface)
│       ├── connection/
│       │   ├── ConnectionManager.kt   ← state machine + reconnect loop
│       │   └── ConnectionCommand.kt   ← sealed interface (internal)
│       ├── engine/
│       │   └── WebSocketEngine.kt     ← OkHttp wrapper (only OkHttp import)
│       ├── routing/
│       │   └── EventRouter.kt         ← JSON parsing + topic dispatch
│       └── subscription/
│           └── SubscriptionManager.kt ← topic lifecycle + resubscription
│
├── pulse-android/                 ← Coming in Phase 2 (lifecycle binding)
├── pulse-serialization/           ← Coming in Phase 2 (Kotlinx Serialization)
├── pulse-logging/                 ← Coming in Phase 2 (pluggable logger)
├── pulse-testing/                 ← Coming next (FakePulseRealtime)
└── sample-chat-app/               ← Coming after (Compose demo)
```

**Critical architectural rule:** `:pulse-core` imports zero Android dependencies. Only pure Kotlin, OkHttp, and coroutines. This keeps Kotlin Multiplatform on the table for Phase 4.

---

## Public API

### Building an instance

```kotlin
val pulse = PulseRealtime.Builder()
    .url("wss://api.example.com/socket")           // required
    .reconnectPolicy(ExponentialBackoff(            // optional, defaults to ExponentialBackoff()
        maxAttempts      = 5,
        initialDelayMillis = 1_000L,
        multiplier       = 2.0,
        maxDelayMillis   = 30_000L,
    ))
    .okHttpClient(myCustomClient)                   // optional, bring your own
    .build()
```

### Connecting

```kotlin
pulse.connect()     // open the socket
pulse.disconnect()  // close intentionally — no auto-reconnect until connect() again
```

### Observing connection state

```kotlin
// In your ViewModel
viewModelScope.launch {
    pulse.connectionState.collect { state ->
        when (state) {
            is ConnectionState.Disconnected  -> showOffline()
            is ConnectionState.Connecting    -> showSpinner()
            is ConnectionState.Connected     -> showOnline()
            is ConnectionState.Reconnecting  -> showRetrying(state.attempt)
            is ConnectionState.Failed        -> showError(state.cause)
        }
    }
}
```

### Subscribing to topics

```kotlin
val sub = pulse.subscribe("payments.updates")

viewModelScope.launch {
    sub.events.collect { event ->
        println("topic:   ${event.topic}")
        println("event:   ${event.event}")
        println("ref:     ${event.ref}")
        println("payload: ${event.payload}")
    }
}

// When done:
sub.cancel()
```

Subscriptions survive reconnects automatically. If the connection drops and reconnects, PulseRealtime replays all active subscribe frames to the server without any app-side code.

### Reconnect policies

```kotlin
// Exponential backoff (recommended for production)
ExponentialBackoff(maxAttempts = 5, initialDelayMillis = 1_000)

// No reconnect (useful in tests or controlled environments)
NoReconnect

// Custom policy
object MyPolicy : ReconnectPolicy {
    override fun shouldRetry(attempt: Int) = attempt <= 3
    override fun delayMillis(attempt: Int) = 2_000L
}
```

---

## ConnectionState reference

| State | Meaning |
|-------|---------|
| `Disconnected` | No active connection. Initial state, or after intentional disconnect. |
| `Connecting` | Handshake in progress. |
| `Connected` | Socket open and healthy. |
| `Reconnecting(attempt)` | Previous connection dropped. Waiting before retry `n`. |
| `Failed(cause)` | All reconnect attempts exhausted. Requires explicit `connect()` to retry. |

---

## Threading model

| Layer | Mechanism | Why |
|-------|-----------|-----|
| Internal scope | `SupervisorJob + Dispatchers.IO` | Child failures are isolated — a crash in the reconnect loop doesn't kill the command processor |
| Command delivery | `Channel.CONFLATED` | Duplicate commands (rapid `connect()`) are dropped safely |
| State output | `StateFlow<ConnectionState>` | Always delivers the latest state — no missed emissions |
| Event output | `SharedFlow<PulseEvent<T>>` | One-shot semantics — no stale "current value" |
| Public API | `suspend` functions + `Flow` | No callbacks, no thread management for the caller |

---

## Test coverage

| Class | Tests | What's covered |
|-------|-------|----------------|
| `ExponentialBackoff` | 7 | Delay formula, cap, flat multiplier, all 4 validation paths |
| `NoReconnect` | 2 | Always false, always 0 delay |
| `WebSocketEngine` | 6 | Open→SocketOpened, frame delivery, close→SocketClosed, send true/false, SocketFailed |
| `ConnectionManager` | 7 | Initial state, connect, double-connect guard, disconnect, disconnect-while-connecting, NoReconnect→Failed, ExponentialBackoff→Reconnecting |
| `EventRouter` | 9 | Topic delivery, drop unregistered, two subscribers, unregister, malformed JSON, missing topic, missing event, ref null, ref populated, non-frame events |
| `SubscriptionManager` | 8 | Subscribe returns correct topic, event delivery, wrong topic drop, cancel, SocketOpened replay, SocketClosed clear, resubscription cycle, multiple topics |
| `PulseRealtime` (integration) | 8 | Builder validation, initial state, connect→Connected, disconnect→Disconnected, event delivery, wrong topic drop, cancel, bad URL→Failed |
| **Total** | **47** | |

All tests run on the JVM with no Android emulator required — `MockWebServer` provides a real local WebSocket server for integration tests.

---

## Quick start (sample app)

```kotlin
class MainActivity : ComponentActivity() {

    private lateinit var pulse: PulseRealtime

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pulse = PulseRealtime.Builder()
            .url("ws://10.0.2.2:8080")  // emulator → host machine
            .build()

        pulse.connect()

        // Observe connection state
        lifecycleScope.launch {
            pulse.connectionState.collect { state ->
                Log.d("PULSE", "State = $state")
            }
        }

        // Subscribe to a topic
        val sub = pulse.subscribe("payments")
        lifecycleScope.launch {
            sub.events.collect { event ->
                Log.d("PULSE", "Event = ${event.event}, payload = ${event.payload}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pulse.disconnect()
    }
}
```

Add to `AndroidManifest.xml` for local development over plain WebSocket:

```xml
<application
    android:usesCleartextTraffic="true"
    ...>
```

---

## Design decisions

| Decision | Chosen | Why |
|----------|--------|-----|
| Wire format | JSON envelope | Human-readable in Logcat, Kotlinx Serialization native, KMP-safe |
| Transport | Plain WebSocket via OkHttp | Own all reconnect logic; no proprietary protocol overhead |
| Subscribe protocol | Server-side with ack | Realistic, efficient, forces valuable resubscription engineering |
| Instance creation | Builder pattern | Familiar (OkHttp/Retrofit precedent), testable, multi-instance capable |
| Event delivery | `SharedFlow<PulseEvent<T>>` | Coroutine-first, no callbacks, one-shot semantics |
| State output | `StateFlow<ConnectionState>` | Always delivers latest state; no missed emissions |
| State owner | `ConnectionManager` only | Single writer eliminates race conditions |
| Command delivery | `Channel.CONFLATED` | Drops redundant commands; no duplicate connections |
| Internal comms | `SharedFlow` event bus | Managers are fully decoupled; each independently testable |
| Threading | `SupervisorJob + Dispatchers.IO` | Child failures isolated; no blocking |
| Android dep isolation | `:pulse-android` only | `:pulse-core` stays KMP-ready |

---

## Upcoming modules

### `:pulse-testing` (next)
```kotlin
// Test your app without a real WebSocket server
val fake = FakePulseRealtime()
fake.simulateMessage(topic = "payments", event = "transfer", payload = "{}")
fake.simulateDisconnect()
```

### `:pulse-android` (Phase 2)
```kotlin
// Auto disconnect in background, reconnect in foreground
pulse.bindToLifecycle(lifecycleOwner)
```

### `:pulse-serialization` (Phase 2)
```kotlin
// Typed payloads — no more raw JSON strings
val sub = pulse.subscribe("payments", PaymentEvent.serializer())
sub.events.collect { event: PulseEvent<PaymentEvent> -> }
```

---

## Built with

- **Kotlin** 2.2.10
- **Kotlinx Coroutines** 1.8.1
- **OkHttp** 4.12.0
- **org.json** 20240303
- **JUnit 4** for testing
- **OkHttp MockWebServer** for integration tests

---

<div align="center">

*PulseRealtime — built to learn SDK engineering the right way.*

**[Phase 1 complete · Phase 2 coming soon]**

</div>
