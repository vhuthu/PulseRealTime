<div align="center">

# ⚡ PulseRealtime

**A modular, lifecycle-aware, coroutine-first WebSocket SDK for Android**

![Phase](https://img.shields.io/badge/Phase-1%20Complete-6C63FF?style=for-the-badge)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-F5A623?style=for-the-badge&logo=kotlin)
![Coroutines](https://img.shields.io/badge/Coroutines-1.8.1-00B4A6?style=for-the-badge)
![OkHttp](https://img.shields.io/badge/OkHttp-4.12.0-FF6B6B?style=for-the-badge)
![Maven Central](https://img.shields.io/badge/Maven%20Central-0.1.2-4CAF50?style=for-the-badge)

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

## Installation

Add to your `app/build.gradle.kts`:

```kotlin
dependencies {
    // Single dependency — includes core, Android lifecycle binding, and logging
    implementation("io.github.vhuthu:pulse-realtime:0.1.2")

    // For unit testing your code that uses PulseRealtime
    testImplementation("io.github.vhuthu:pulse-testing:0.1.2")
}
```

Or use individual modules if you only need specific functionality:

```kotlin
dependencies {
    implementation("io.github.vhuthu:pulse-core:0.1.2")
    implementation("io.github.vhuthu:pulse-android:0.1.2")   // lifecycle binding
    implementation("io.github.vhuthu:pulse-logging:0.1.2")   // pluggable logger
}
```

---

## Why this exists

Modern Android apps need realtime communication. Teams repeatedly build the same things — connection management, retry logic, lifecycle handling, subscription restoration — and get them wrong in subtle, hard-to-reproduce ways.

PulseRealtime solves this once, correctly, with a clean API surface and fully tested internals.

---

## Project status

| Phase | What | Status |
|-------|------|--------|
| **Phase 1** | Core SDK — connect, reconnect, subscribe, typed events | ✅ **Complete · v0.1.2** |
| Phase 2 | Buffering, offline queueing, metrics, typed serialization | 🔜 Planned |
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
│ loop         │  │ Dispatch    │  │ reconnect         │
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
       ├──► ConnectionManager   (drives ConnectionState transitions)
       ├──► EventRouter         (deserializes frames, routes to topics)
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
{ "event": "subscribed", "topic": "payments.updates", "ref": "ref-001" }
```

---

## Module structure

```
pulse-realtime/
├── pulse-core/          ← Pure Kotlin. Zero Android deps. KMP-ready.
├── pulse-android/       ← Lifecycle binding (bindToLifecycle)
├── pulse-logging/       ← Pluggable logger interface
├── pulse-testing/       ← FakePulseRealtime for unit tests
└── pulse-realtime/      ← Facade — single dependency for consumers
```

**Critical architectural rule:** `:pulse-core` imports zero Android dependencies. Only pure Kotlin, OkHttp, and coroutines. This keeps Kotlin Multiplatform on the table for Phase 4.

---

## Public API

### Building an instance

```kotlin
val pulse = PulseRealtime.Builder()
    .url("wss://api.example.com/socket")           // required
    .reconnectPolicy(ExponentialBackoff(            // optional
        maxAttempts        = 5,
        initialDelayMillis = 1_000L,
        multiplier         = 2.0,
        maxDelayMillis     = 30_000L,
    ))
    .okHttpClient(myCustomClient)                   // optional — bring your own
    .logger(AndroidLogger)                          // optional — silent by default
    .build()
```

### Connecting

```kotlin
pulse.connect()              // open the socket
pulse.disconnect()           // close intentionally — no auto-reconnect
pulse.disconnectTemporary()  // close temporarily — auto-reconnects on connect()
```

### Lifecycle binding (`:pulse-android`)

```kotlin
// One line — auto connect on foreground, disconnect on background
pulse.bindToLifecycle(this)
```

### Observing connection state

```kotlin
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

Subscriptions survive reconnects automatically. PulseRealtime replays all active subscribe frames to the server on reconnect — no app-side code required.

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

### Logging

```kotlin
// Development — print all SDK logs
PulseRealtime.Builder()
    .logger(PrintLogger)   // stdout — pure JVM
    .build()

// Custom logger — route to Timber, Firebase, etc.
PulseRealtime.Builder()
    .logger(object : PulseLogger {
        override fun log(level: LogLevel, tag: String, message: String) {
            Timber.tag(tag).d(message)
        }
        override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable) {
            Timber.tag(tag).e(throwable, message)
        }
    })
    .build()
```

---

## Testing with `:pulse-testing`

```kotlin
class PaymentsViewModelTest {

    private val fake = FakePulseRealtime()
    private lateinit var viewModel: PaymentsViewModel

    @Before
    fun setUp() {
        viewModel = PaymentsViewModel(pulse = fake)
    }

    @Test
    fun `shows payment when event arrives`() = runBlocking {
        fake.simulateConnected()

        fake.simulateMessage(
            topic   = "payments.updates",
            event   = "transfer_completed",
            payload = """{"amount":500}""",
        )

        delay(300)
        assertEquals("transfer_completed", viewModel.latestEvent.value?.event)
    }
}
```

No real server. No emulator. Pure JVM.

---

## ConnectionState reference

| State | Meaning |
|-------|---------|
| `Disconnected` | No active connection. Initial state, or after intentional disconnect. |
| `Connecting` | Handshake in progress. |
| `Connected` | Socket open and healthy. |
| `Reconnecting(attempt)` | Connection dropped. Waiting before retry `n`. |
| `Failed(cause)` | All reconnect attempts exhausted. Requires explicit `connect()` to retry. |

---

## Threading model

| Layer | Mechanism | Why |
|-------|-----------|-----|
| Internal scope | `SupervisorJob + Dispatchers.IO` | Child failures are isolated — a crash in the reconnect loop doesn't kill the command processor |
| Command delivery | `Channel.CONFLATED` | Duplicate commands (rapid `connect()`) are dropped safely |
| State output | `StateFlow<ConnectionState>` | Always delivers the latest state — no missed emissions |
| Event output | `SharedFlow<PulseEvent<T>>` | One-shot semantics — no stale "current value" |
| Public API | `Flow` + coroutines | No callbacks, no thread management for the caller |

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
| Internal comms | `SharedFlow` event bus | Managers fully decoupled; each independently testable |
| Threading | `SupervisorJob + Dispatchers.IO` | Child failures isolated; no blocking |
| Android dep isolation | `:pulse-android` only | `:pulse-core` stays KMP-ready |

---

## Sample app

**Chatz** — a full real-time chat app built with PulseRealtime.

- Room-based group chat
- Private messaging via invite codes
- Typing indicators
- Message history (MongoDB)
- Auto-reconnect with animated connection banner
- Lifecycle-aware — disconnects in background, reconnects in foreground

[View Chatz-Sample on GitHub](https://github.com/vhuthu/Chatz-Sample)

---

## Built with

- **Kotlin** 2.2.10
- **Kotlinx Coroutines** 1.8.1
- **OkHttp** 4.12.0
- **org.json** 20240303
- **JUnit 4** for unit testing
- **OkHttp MockWebServer** for integration tests

---

## Changelog

### v0.1.2
- Fixed `ConnectionManager` not triggering reconnect loop when socket drops from `Connected` state
- Added `sendRaw()` to public API for custom frame types

### v0.1.1
- Added `sendRaw()` internal method (superseded by v0.1.2)

### v0.1.0
- Initial release — full Phase 1 SDK

---

<div align="center">

*PulseRealtime — Per Aspera Ad Astra*

**[Phase 1 complete · v0.1.2 on Maven Central]**

[Maven Central](https://central.sonatype.com/artifact/io.github.vhuthu/pulse-realtime) · [Chatz Sample App](https://github.com/vhuthu/Chatz-Sample)

<br>

Made with ❤️ by **Vhuthu Kwinda**

</div>