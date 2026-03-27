# Hauler

Hauler is a Kotlin Multiplatform logging library built on [ksrpc](https://github.com/Monkopedia/ksrpc). It provides RPC-serializable logging interfaces for forwarding log messages across service boundaries — push frontend logs to a backend for aggregation, or stream backend logs to a frontend for live debugging.

## Quick start

### Local logging

```kotlin
// Get a named logger through the global Garage
val log = hauler("MyClass")

log.info("Request received")
log.error("Something failed", exception)
log.debug("Details", metadata = mapOf("requestId" to "abc-123"))

// Route logs to console
route(scope, Flatbed)
```

### Forwarding logs over RPC

On the receiving side, create a `Warehouse` and expose it as a ksrpc service:

```kotlin
val warehouse = Warehouse(DeliveryRates(onDeliveryError = { it.printStackTrace() }))

// Serve over ksrpc (e.g. via ktor)
ksrpcEnvironment {
    service(warehouse, ShipperStub)
}
```

On the sending side, connect and forward logs:

```kotlin
// Connect to the remote Warehouse
val shipper = connection.defaultChannel().toStub<ShipperStub, Shipper>()

// Forward individual log events
val dropBox = shipper.requestPickup()
Garage.deliveries.attach(dropBox, scope)

// Or batch for higher throughput
val dock = shipper.requestDockPickup()
Garage.deliveries.attach(dock, scope, DeliveryRates())
```

### Consuming logs

Subscribe to incoming logs on the receiving side:

```kotlin
val service = warehouse.deliveries()

// Callback-based delivery
service.registerDelivery(object : AutomaticDelivery {
    override suspend fun onLogEvent(event: Box) {
        println("${event.level} ${event.loggerName} - ${event.message}")
    }
})

// Or as a Flow
service.deliveries(scope).collect { box ->
    println(box.message)
}

// With filtering
val filtered = service.weighIn(weighStation {
    level(LevelMatchMode.GT, Level.DEBUG)
    logger(LoggerMatchMode.PREFIX, "com.example")
})
```

### Batched delivery

For efficiency, logs can be packed into `Palette`s that deduplicate logger and thread names:

```kotlin
// Batched callback
service.registerDeliveryDay(object : DeliveryDay {
    override suspend fun onLogs(event: Palette) {
        event.forEach { box -> process(box) }
    }
})

// Or as a Flow with automatic batching
service.withDeliveryDay(scope).collect { box ->
    // Boxes are unpacked automatically
}
```

## Dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.monkopedia:hauler:0.2.1")
}
```

Available for JVM, JS, WasmJS, Linux (x64/arm64), macOS (x64/arm64), iOS (arm64/x64/simulatorArm64), and Windows (x64).

## Architecture

All service interfaces (`Shipper`, `DropBox`, `LoadingDock`, `DeliveryService`, etc.) are annotated with `@KsService` and serialize over ksrpc, making them transparent to the transport layer. The `Warehouse` class is the default in-process implementation backed by a `MutableSharedFlow`, with configurable replay cache for historical log access.

## License

Apache License 2.0
