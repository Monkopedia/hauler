package com.monkopedia.hauler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

object Garage {
    private val sharedFlow = MutableSharedFlow<Box>(replay = 0)
    private val job = SupervisorJob()
    val rootHauler: Hauler = Hauler { box ->
        CoroutineScope(job).launch {
            sharedFlow.emit(box)
        }
    }
    val deliveries: Deliveries = sharedFlow

    suspend fun flushLogs() {
        job.children.toList().joinAll()
    }
}

inline fun Hauler.named(loggerName: String): Hauler {
    return Hauler { box ->
        this@named.emit(box.copy(loggerName = loggerName))
    }
}

inline fun Hauler.level(level: Level): Hauler {
    return Hauler { box ->
        if (box.level.intLevel >= level.intLevel) {
            this@level.emit(box)
        }
    }
}

inline fun Deliveries.level(level: Level): Deliveries {
    return filter { box ->
        box.level.intLevel >= level.intLevel
    }
}

inline fun hauler(name: String): Hauler = Garage.rootHauler.named(name)

inline fun <reified T> T.hauler(): Hauler = createHauler<T>()

inline fun <reified T> createHauler(): Hauler =
    hauler(T::class.simpleName ?: T::class.toString())

inline fun route(
    scope: CoroutineScope,
    display: Display,
    noinline formatter: Formatter = FlowCollector<String>::defaultFormat
): Job {
    return scope.launch {
        Garage.deliveries.route(display, formatter)
    }
}

suspend inline fun route(display: Display, noinline formatter: Formatter) {
    Garage.deliveries.route(display, formatter)
}

suspend fun DropBox.attach(scope: CoroutineScope): Job {
    return Garage.deliveries.attach(this, scope)
}

fun LoadingDock.attach(
    scope: CoroutineScope,
    deliveryRates: DeliveryRates = DeliveryRates()
): Job {
    return Garage.deliveries.attach(this, scope, deliveryRates)
}
