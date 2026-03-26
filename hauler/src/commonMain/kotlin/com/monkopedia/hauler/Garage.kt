/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.monkopedia.hauler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Global routing center for log messages. Provides a shared [rootHauler] and [deliveries] flow.
 *
 * For simple applications, use [hauler] to get a named logger that routes through the Garage:
 * ```
 * val log = hauler("MyClass")
 * log.info("Something happened")
 * ```
 *
 * Connect output via [route] or attach a remote endpoint via [DropBox.attach] / [LoadingDock.attach].
 */
object Garage {
    private val sharedFlow = MutableSharedFlow<Box>(extraBufferCapacity = 64)
    val rootHauler: Hauler =
        Hauler { box ->
            sharedFlow.tryEmit(box)
        }
    val deliveries: Deliveries = sharedFlow

    @Suppress("RedundantSuspendModifier")
    suspend fun flushLogs() {
        // No-op: tryEmit is synchronous, so there are no pending emissions to flush.
    }
}

/** Create a [Hauler] that tags all emitted [Box]es with the given [loggerName]. */
inline fun Hauler.named(loggerName: String): Hauler =
    Hauler { box ->
        this@named.emit(
            Box(box.level, loggerName, box.message, box.timestamp, box.threadName, box.metadata),
        )
    }

/** Create a [Hauler] that only emits [Box]es at or above the given [level]. */
inline fun Hauler.level(level: Level): Hauler =
    Hauler { box ->
        if (box.level.intLevel >= level.intLevel) {
            this@level.emit(box)
        }
    }

inline fun Deliveries.level(level: Level): Deliveries =
    filter { box ->
        box.level.intLevel >= level.intLevel
    }

/** Create a named [Hauler] that routes through the global [Garage]. */
inline fun hauler(name: String): Hauler = Garage.rootHauler.named(name)

inline fun <reified T> T.hauler(): Hauler = createHauler<T>()

inline fun <reified T> createHauler(): Hauler = hauler(T::class.simpleName ?: T::class.toString())

inline fun route(
    scope: CoroutineScope,
    display: Display,
    noinline formatter: Formatter = FlowCollector<String>::defaultFormat,
): Job =
    scope.launch {
        Garage.deliveries.route(display, formatter)
    }

suspend inline fun route(
    display: Display,
    noinline formatter: Formatter,
) {
    Garage.deliveries.route(display, formatter)
}

suspend fun DropBox.attach(scope: CoroutineScope): Job = Garage.deliveries.attach(this, scope)

fun LoadingDock.attach(
    scope: CoroutineScope,
    deliveryRates: DeliveryRates = DeliveryRates(),
): Job = Garage.deliveries.attach(this, scope, deliveryRates)
