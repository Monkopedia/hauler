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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class DeliveryServiceTest {
    private fun box(
        level: Level = Level.INFO,
        loggerName: String = "com.example.Test",
        message: String = "test message",
        timestamp: Long = 1000L,
        threadName: String? = "main",
    ) = Box(level, loggerName, message, timestamp, threadName)

    private fun createDeliveryService(): Triple<MutableSharedFlow<Box>, DeliveryService, CoroutineScope> {
        val flow = MutableSharedFlow<Box>(replay = 100)
        val scope = CoroutineScope(SupervisorJob())
        val service = flow.deliveries(scope, DeliveryRates())
        return Triple(flow, service, scope)
    }

    /** Wait for a condition by polling, yielding to let external scopes run. */
    private suspend fun awaitCondition(
        description: String = "",
        check: () -> Boolean,
    ) {
        withTimeout(5.seconds) {
            while (!check()) delay(1)
        }
    }

    // --- streamDeliveries ---

    @Test
    fun streamDeliveries_receivesEvents() =
        runTest {
            withContext(Dispatchers.Default) {
                val (flow, service, scope) = createDeliveryService()
                val received = mutableListOf<Box>()
                val collectJob =
                    launch {
                        service.streamDeliveries().collect { received.add(it) }
                    }
                delay(50)

                flow.emit(box(message = "a"))
                flow.emit(box(message = "b"))
                awaitCondition { received.size >= 2 }
                assertEquals(2, received.size)
                assertEquals("a", received[0].message)
                assertEquals("b", received[1].message)
                collectJob.cancelAndJoin()
                scope.cancel()
            }
        }

    @Test
    fun streamDeliveries_cancellationStopsDelivery() =
        runTest {
            withContext(Dispatchers.Default) {
                val (flow, service, scope) = createDeliveryService()
                val received = mutableListOf<Box>()
                val collectJob =
                    launch {
                        service.streamDeliveries().collect { received.add(it) }
                    }
                delay(50)

                flow.emit(box(message = "before"))
                awaitCondition { received.size >= 1 }
                collectJob.cancelAndJoin()

                flow.emit(box(message = "after"))
                // Brief wait to verify "after" does NOT arrive
                repeat(50) { delay(1) }

                assertEquals(1, received.size)
                assertEquals("before", received[0].message)
                scope.cancel()
            }
        }

    // --- streamDeliveriesPacked ---

    @Test
    fun streamDeliveriesPacked_receivesPalettes() =
        runTest {
            withContext(Dispatchers.Default) {
                val rates = DeliveryRates(defaultPaletteSize = 2, defaultPaletteInterval = 100.seconds)
                val flow = MutableSharedFlow<Box>(replay = 100)
                val scope = CoroutineScope(SupervisorJob())
                val service = flow.deliveries(scope, rates)

                val received = mutableListOf<Palette>()
                val collectJob =
                    launch {
                        service.streamDeliveriesPacked().collect { received.add(it) }
                    }
                delay(50)

                flow.emit(box(message = "a"))
                flow.emit(box(message = "b"))
                awaitCondition { received.isNotEmpty() }
                collectJob.cancelAndJoin()
                scope.cancel()
            }
        }

    // --- weighIn (filtering) ---

    @Test
    fun weighIn_filtersEvents() =
        runTest {
            withContext(Dispatchers.Default) {
                val (flow, service, scope) = createDeliveryService()
                val filtered = service.weighIn(LevelFilter(LevelMatchMode.EQ, Level.ERROR))

                val received = mutableListOf<Box>()
                val collectJob =
                    launch {
                        filtered.streamDeliveries().collect { received.add(it) }
                    }
                delay(50)

                flow.emit(box(level = Level.INFO, message = "info"))
                flow.emit(box(level = Level.ERROR, message = "error"))
                awaitCondition { received.size >= 1 }
                assertEquals(1, received.size)
                assertEquals("error", received[0].message)
                collectJob.cancelAndJoin()
                scope.cancel()
            }
        }

    @Test
    fun weighIn_chainedFilters() =
        runTest {
            withContext(Dispatchers.Default) {
                val (flow, service, scope) = createDeliveryService()
                val filtered =
                    service
                        .weighIn(LevelFilter(LevelMatchMode.GT, Level.DEBUG))
                        .weighIn(LoggerNameFilter(LoggerMatchMode.PREFIX, "com"))

                val received = mutableListOf<Box>()
                val collectJob =
                    launch {
                        filtered.streamDeliveries().collect { received.add(it) }
                    }
                delay(50)

                flow.emit(box(level = Level.INFO, loggerName = "com.example", message = "yes"))
                flow.emit(box(level = Level.INFO, loggerName = "org.other", message = "no"))
                flow.emit(box(level = Level.DEBUG, loggerName = "com.example", message = "no2"))
                awaitCondition { received.size >= 1 }
                // Brief extra wait to verify no others arrive
                repeat(50) { delay(1) }
                assertEquals(1, received.size)
                assertEquals("yes", received[0].message)
                collectJob.cancelAndJoin()
                scope.cancel()
            }
        }

    // --- dumpDeliveries ---

    @Test
    fun dumpDeliveries_returnsReplayedEvents() =
        runTest {
            val (flow, service, scope) = createDeliveryService()
            flow.emit(box(message = "replayed1"))
            flow.emit(box(message = "replayed2"))

            val received = service.dumpDeliveries().toList()
            assertEquals(2, received.size)
            assertEquals("replayed1", received[0].message)
            assertEquals("replayed2", received[1].message)
            scope.cancel()
        }

    // --- onDeliveryError (server-side source error hook) ---

    @Test
    fun streamDeliveries_sourceErrorReachesOnDeliveryError() =
        runTest {
            val errors = mutableListOf<Throwable>()
            val rates = DeliveryRates(onDeliveryError = { errors.add(it) })
            val source =
                flow {
                    emit(box(message = "ok"))
                    throw RuntimeException("source boom")
                }
            val scope = CoroutineScope(SupervisorJob())
            val service = deliveries(source, emptyFlow(), scope, rates)
            val received = service.streamDeliveries().toList()
            assertEquals(1, received.size)
            assertEquals("ok", received[0].message)
            assertEquals(1, errors.size)
            assertEquals("source boom", errors[0].message)
            scope.cancel()
        }

    // --- recurringCustomerPickup ---

    @Test
    fun recurringCustomerPickup_collectsFromFlow() =
        runTest {
            val (flow, service, scope) = createDeliveryService()
            val pickup = service.recurringCustomerPickup()

            flow.emit(box(message = "poll1"))
            flow.emit(box(message = "poll2"))

            val allBoxes = mutableListOf<Box>()
            withTimeout(5.seconds) {
                while (allBoxes.size < 2) {
                    allBoxes.addAll(pickup.get().unpack())
                    if (allBoxes.size < 2) delay(1)
                }
            }
            assertEquals(2, allBoxes.size)
            pickup.close()
            scope.cancel()
        }
}
