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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
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
 * Connect output via [route] or attach a remote endpoint via [DropBox.attach] / [LoadingDock.attach]
 * (which delegate to [Deliveries.forwardTo]).
 */
object Garage {
    private val sharedFlow = MutableSharedFlow<Box>(extraBufferCapacity = 64)
    private val dropped = MutableStateFlow(0L)

    /**
     * Never suspends, and is therefore LOSSY. Each [Box] is offered to every current subscriber of
     * [deliveries] with a 64-box buffer; if the slowest subscriber's buffer is full, the box is
     * discarded for ALL subscribers, not just the slow one, and counted once in [droppedBoxes].
     * One stuck subscriber therefore costs every other subscriber the same boxes.
     */
    val rootHauler: Hauler =
        Hauler { box ->
            if (!sharedFlow.tryEmit(box)) dropped.update { it + 1 }
        }

    /**
     * Every [Box] emitted through [rootHauler]. A hot flow with `replay = 0`: a box emitted while
     * nothing is collecting is delivered to no one and is NOT counted in [droppedBoxes], so
     * subscribe (e.g. via [route] or `attach`) before logging anything you need to see.
     */
    val deliveries: Deliveries = sharedFlow

    /**
     * Running total of boxes [rootHauler] discarded because a subscriber of [deliveries] fell more
     * than 64 boxes behind. Never decreases.
     */
    val droppedBoxes: StateFlow<Long> = dropped.asStateFlow()
}

/** Create a [Hauler] that tags all emitted [Box]es with the given [loggerName]. */
fun Hauler.named(loggerName: String): Hauler =
    Hauler { box ->
        this@named.emit(
            Box(box.level, loggerName, box.message, box.timestamp, box.threadName, box.metadata),
        )
    }

/** Create a [Hauler] that only emits [Box]es at or above the given [level]. */
fun Hauler.level(level: Level): Hauler =
    Hauler { box ->
        if (box.level.intLevel >= level.intLevel) {
            this@level.emit(box)
        }
    }

fun Deliveries.level(level: Level): Deliveries =
    filter { box ->
        box.level.intLevel >= level.intLevel
    }

/** Create a named [Hauler] that routes through the global [Garage]. */
fun hauler(name: String): Hauler = Garage.rootHauler.named(name)

inline fun <reified T> T.hauler(): Hauler = createHauler<T>()

inline fun <reified T> createHauler(): Hauler = hauler(T::class.simpleName ?: T::class.toString())

fun route(
    scope: CoroutineScope,
    display: Display,
    formatter: Formatter = DefaultFormat,
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

suspend fun DropBox.attach(scope: CoroutineScope): Job = Garage.deliveries.forwardTo(this, scope)

fun LoadingDock.attach(
    scope: CoroutineScope,
    deliveryRates: DeliveryRates = DeliveryRates(),
): Job = Garage.deliveries.forwardTo(this, scope, deliveryRates)
