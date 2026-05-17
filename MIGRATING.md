# Migrating from 0.3.x to 0.4.x

`0.4.0` adopts ksrpc 1.0 service-tier vocabulary and replaces the callback-based
delivery API with `Flow<T>` returns. The compile errors will tell you *that* a
symbol moved; this guide tells you *where it went*.

If you're not sure where to look first: most consumer-side migration falls under
"Streaming deliveries" below.

---

## Streaming deliveries (the big one)

The `register*` / `dump*` callback methods on `DeliveryService`, plus the
`Registration` / `AutomaticDelivery` / `DeliveryDay` interfaces, are removed.
Use the new `Flow<T>`-returning methods and `.collect { }` directly.

### Live, single boxes

**Before:**
```kotlin
val received = mutableListOf<Box>()
val receiver = object : AutomaticDelivery {
    override suspend fun onLogEvent(event: Box) { received.add(event) }
}
val registration = service.registerDelivery(receiver)
// ...later, to stop:
registration.unregister()
```

**After:**
```kotlin
val received = mutableListOf<Box>()
val job = scope.launch {
    service.streamDeliveries().collect { received.add(it) }
}
// ...later, to stop:
job.cancel()
```

### Live, batched palettes

**Before:**
```kotlin
val receiver = object : DeliveryDay {
    override suspend fun onLogs(event: Palette) { ... }
}
service.registerDeliveryDay(receiver)
```

**After:**
```kotlin
scope.launch { service.streamDeliveriesPacked().collect { palette -> ... } }
```

If you previously consumed `DeliveryDay` callbacks just to unpack palettes back
into individual boxes, use the existing `Flow<Palette>.unpack()` helper:

```kotlin
scope.launch { service.streamDeliveriesPacked().unpack().collect { box -> ... } }
```

### Replay-cache dumps

**Before:**
```kotlin
val dumped = mutableListOf<Box>()
service.dumpDelivery(object : AutomaticDelivery {
    override suspend fun onLogEvent(event: Box) { dumped.add(event) }
})
// dumpDeliveryDay similarly
```

**After:**
```kotlin
val dumped = service.dumpDeliveries().toList()              // single boxes, finite
val palettes = service.dumpDeliveriesPacked().toList()      // batched, finite
```

`dumpDeliveries()` / `dumpDeliveriesPacked()` complete after replay is exhausted
— no callback service to manage, no `Registration` to unregister.

### Cancellation, liveness

`Registration` is gone, including its `ping()` and `unregister()` methods.
Cancellation of the collecting coroutine is now the unsubscribe mechanism.
Liveness is implicit: if the upstream connection dies, your `.collect { }`
throws and your coroutine completes.

---

## `Shipper` and `DeliveryService` types changed (compile-time)

| 0.3.x | 0.4.x | Why |
|---|---|---|
| `interface Shipper : RpcService` | `interface Shipper : BasicShipper, RpcBidiService` | Returns sub-services *and* gives back a bidi `DeliveryService` |
| `interface DeliveryService : RpcService` | `interface DeliveryService : BasicDeliveryService, RpcBidiService` | Accepts no callbacks any more, but returns `Flow<T>` (still bidi) |

The new `BasicShipper` / `BasicDeliveryService` are host-tier subsets you can
host on simple transports (HTTP / JSON-RPC). If your consumer only needs the
push side of `Shipper` (request a `DropBox` / `LoadingDock`) or the polling
side of `DeliveryService` (`recurringCustomerPickup`, `dumpCustomerPickup`,
`weighIn`), prefer the `Basic*` interfaces.

If you previously did `connection.registerDefault(warehouse, Shipper)` and you
want to host over an HTTP transport, use `BasicShipper` instead. Server-side
`Warehouse` automatically satisfies `BasicShipper` because `Shipper` extends
it.

---

## `attach` extensions renamed (where the receiver is the source)

The `Deliveries.attach(DropBox|LoadingDock, ...)` extensions are renamed to
`forwardTo` to disambiguate from `DropBox.attach(scope)` /
`LoadingDock.attach(scope)`, which pull from the global `Garage` instead.

| 0.3.x | 0.4.x |
|---|---|
| `flow.attach(dropBox, scope)` | `flow.forwardTo(dropBox, scope)` |
| `flow.attach(dock, scope, rates)` | `flow.forwardTo(dock, scope, rates)` |
| `dropBox.attach(scope)` | unchanged |
| `dock.attach(scope, rates)` | unchanged |

Reading rule: `flow.forwardTo(sink)` matches the data direction. The
`receiver.attach(scope)` form is the convenience for "attach this remote sink
to the global `Garage` feed."

---

## `Flows.kt` extensions removed

The `DeliveryService.deliveries(scope)` / `withDeliveryDay(scope)` /
`dumpDeliveries(scope)` / `dumpWithDeliveryDay(scope)` extensions on
`DeliveryService` are gone — they were callback-flow shims around the old
`register*` / `dump*` methods, which now return `Flow<T>` directly.

| 0.3.x | 0.4.x |
|---|---|
| `service.deliveries(scope).collect { ... }` | `service.streamDeliveries().collect { ... }` |
| `service.withDeliveryDay(scope).collect { ... }` | `service.streamDeliveriesPacked().unpack().collect { ... }` |
| `service.dumpDeliveries(scope).collect { ... }` | `service.dumpDeliveries().collect { ... }` (now an interface method) |
| `service.dumpWithDeliveryDay(scope).collect { ... }` | `service.dumpDeliveriesPacked().unpack().collect { ... }` |

The polling helpers `BasicDeliveryService.withPickup(...)` and
`dumpWithPickup(...)` are unchanged — they still bridge polling
`CustomerPickup` into a `Deliveries` flow.

---

## `DeliveryRates.onDeliveryError` is now optional and server-side only

`onDeliveryError` is now a defaulted parameter (`= {}`) and is only invoked on
*server-side* source errors — for example, an exception inside a custom
`Flow<Box>` driving the warehouse, or a serialization failure mid-stream.
Errors raised inside the *client's* `.collect { }` block propagate through the
client coroutine as usual; they don't reach `onDeliveryError`.

If you were relying on `onDeliveryError` to observe client-side handler
exceptions, wrap your collector instead:
```kotlin
scope.launch {
    service.streamDeliveries()
        .catch { handler(it) }
        .collect { ... }
}
```

---

## `Formatter` typealias

Still present, unchanged: `Terminators.kt:28`
```kotlin
typealias Formatter = suspend FlowCollector<String>.(Box) -> Unit
```

If for any reason you're constructing a `Formatter` value without the alias
in scope, the spelled-out type is `suspend FlowCollector<String>.(Box) -> Unit`.

---

## ksrpc dependency

`hauler` 0.4.x depends on `com.monkopedia.ksrpc:ksrpc-core:1.0.0-RC4`.
Consumers transitively inherit a pre-release dependency. `hauler` 0.4.0 final
will follow `ksrpc` 1.0.0 final as a one-line bump.

If your project applies the `com.monkopedia.ksrpc.plugin` Gradle plugin
directly, also bump it to `1.0.0-RC4`. If you reference `ksrpc-sockets` /
`ksrpc-ktor-client` / `ksrpc-ktor-websocket-client` directly, the entry-point
names changed in ksrpc 1.0:

| 0.11.x | 1.0 |
|---|---|
| `HttpClient.asConnection(url, env).defaultChannel()` | `HttpClient.asHttpChannelClient(url, env).defaultChannel()` |
| `HttpClient.asConnection(url, env)` (websocket) | `HttpClient.asWebsocketConnection(url, env)` |
| Sockets `(input to output).asConnection(env)` | unchanged |

See the [ksrpc 0.11.x → 1.0 migration guide](https://github.com/Monkopedia/ksrpc/blob/main/dokka/guides/migration-1.0.md)
for the full ksrpc story.
