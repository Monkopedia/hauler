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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FlowsTest {

    private fun box(
        level: Level = Level.INFO,
        loggerName: String = "com.example.Test",
        message: String = "test message",
        timestamp: Long = 1000L,
        threadName: String? = "main",
    ) = Box(level, loggerName, message, timestamp, threadName)

    // --- DeliveryService.deliveries() (callbackFlow) ---

    @Test
    fun deliveries_receivesLiveEvents() = runTest {
        withContext(Dispatchers.Default) {
            val warehouse = Warehouse(DeliveryRates(onDeliveryError = {}))
            val dropBox = warehouse.requestPickup()
            val service = warehouse.deliveries()
            val scope = CoroutineScope(SupervisorJob())

            val result = mutableListOf<Box>()
            val collectJob = launch {
                service.deliveries(scope).take(2).collect { result.add(it) }
            }
            delay(100) // let callbackFlow setup

            dropBox.log(box(message = "live1"))
            dropBox.log(box(message = "live2"))

            // Poll for results — each delay(1) yields to real scheduler
            withTimeout(5.seconds) {
                while (result.size < 2) delay(1)
            }
            assertEquals(2, result.size)
            assertEquals("live1", result[0].message)
            assertEquals("live2", result[1].message)
            collectJob.cancel()
            scope.cancel()
            warehouse.close()
        }
    }

    // --- DeliveryService.dumpDeliveries() ---

    @Test
    fun dumpDeliveries_returnsReplayCache() = runTest {
        val warehouse = Warehouse(DeliveryRates(defaultBoxRetention = 100, onDeliveryError = {}))
        val dropBox = warehouse.requestPickup()
        dropBox.log(box(message = "cached1"))
        dropBox.log(box(message = "cached2"))

        val service = warehouse.deliveries()
        val result = service.dumpDeliveries().toList()
        assertEquals(2, result.size)
        assertEquals("cached1", result[0].message)
        assertEquals("cached2", result[1].message)
        warehouse.close()
    }

    // --- DeliveryService.dumpCustomerPickup() ---

    @Test
    fun dumpCustomerPickup_returnsReplayedBoxes() = runTest {
        val warehouse = Warehouse(DeliveryRates(defaultBoxRetention = 100, onDeliveryError = {}))
        val dropBox = warehouse.requestPickup()
        dropBox.log(box(message = "poll1"))
        dropBox.log(box(message = "poll2"))

        val service = warehouse.deliveries()
        val pickup = service.dumpCustomerPickup()
        val allBoxes = mutableListOf<Box>()
        withTimeout(5.seconds) {
            while (allBoxes.size < 2) {
                allBoxes.addAll(pickup.get().unpack())
                if (allBoxes.size < 2) delay(1)
            }
        }
        assertEquals(2, allBoxes.size)
        pickup.close()
        warehouse.close()
    }

    // --- DeliveryService.withDeliveryDay() (uses pack() with timer-based flushing) ---
    // Uses UnconfinedTestDispatcher so that pack()'s delay() runs on virtual time.

    @Test
    fun withDeliveryDay_receivesLiveEvents() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val flow = MutableSharedFlow<Box>(replay = 100)
        val scope = CoroutineScope(SupervisorJob() + testDispatcher)
        val rates = DeliveryRates(
            defaultPaletteSize = 500,
            defaultPaletteInterval = 1.seconds,
            onDeliveryError = {},
        )
        val service = flow.deliveries(scope, rates)

        val result = mutableListOf<Box>()
        backgroundScope.launch(testDispatcher) {
            service.withDeliveryDay(scope).take(2).collect { result.add(it) }
        }
        testScheduler.runCurrent()

        flow.emit(box(message = "a"))
        flow.emit(box(message = "b"))
        testScheduler.runCurrent()

        testScheduler.advanceTimeBy(1100)
        testScheduler.runCurrent()

        assertEquals(2, result.size)
        assertEquals("a", result[0].message)
        assertEquals("b", result[1].message)
        scope.cancel()
    }

    // --- DeliveryService.dumpWithDeliveryDay() ---

    @Test
    fun dumpWithDeliveryDay_returnsReplayCache() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val sharedFlow = MutableSharedFlow<Box>(replay = 100)
        val scope = CoroutineScope(SupervisorJob() + testDispatcher)
        val rates = DeliveryRates(
            defaultPaletteSize = 500,
            defaultPaletteInterval = 500.milliseconds,
            onDeliveryError = {},
        )
        val service = sharedFlow.deliveries(scope, rates)

        sharedFlow.emit(box(message = "cached1"))
        sharedFlow.emit(box(message = "cached2"))

        val result = mutableListOf<Box>()
        backgroundScope.launch(testDispatcher) {
            service.dumpWithDeliveryDay().collect { result.add(it) }
        }

        testScheduler.advanceTimeBy(600)
        testScheduler.runCurrent()

        assertEquals(2, result.size)
        assertEquals("cached1", result[0].message)
        assertEquals("cached2", result[1].message)
        scope.cancel()
    }

    // --- DeliveryService.withPickup() ---
    // Uses withContext(Dispatchers.Default) for real-time execution because withPickup
    // wraps a polling loop inside callbackFlow + coroutineScope — the layered
    // producer/consumer doesn't propagate items within a single test scheduler tick.

    @Test
    fun withPickup_receivesLiveEvents() = runTest {
        withContext(Dispatchers.Default) {
            val warehouse = Warehouse(DeliveryRates(onDeliveryError = {}))
            val dropBox = warehouse.requestPickup()
            val service = warehouse.deliveries()
            val scope = CoroutineScope(SupervisorJob())

            val result = mutableListOf<Box>()
            val collectJob = launch {
                service.withPickup(interval = 50.milliseconds, closeScope = scope)
                    .collect { result.add(it) }
            }
            delay(200)

            dropBox.log(box(message = "a"))
            dropBox.log(box(message = "b"))

            withTimeout(5.seconds) {
                while (result.size < 2) delay(50)
            }
            assertEquals(2, result.size)
            assertEquals("a", result[0].message)
            assertEquals("b", result[1].message)

            collectJob.cancel()
            scope.cancel()
            warehouse.close()
        }
    }

    // --- DeliveryService.dumpWithPickup() ---

    @Test
    fun dumpWithPickup_returnsReplayedBoxes() = runTest {
        withContext(Dispatchers.Default) {
            val warehouse = Warehouse(DeliveryRates(defaultBoxRetention = 100, onDeliveryError = {}))
            val dropBox = warehouse.requestPickup()
            dropBox.log(box(message = "r1"))
            dropBox.log(box(message = "r2"))

            val service = warehouse.deliveries()
            val scope = CoroutineScope(SupervisorJob())

            val result = mutableListOf<Box>()
            val collectJob = launch {
                service.dumpWithPickup(interval = 50.milliseconds, closeScope = scope)
                    .collect { result.add(it) }
            }

            withTimeout(5.seconds) {
                while (result.size < 2) delay(50)
            }
            assertEquals(2, result.size)
            assertEquals("r1", result[0].message)
            assertEquals("r2", result[1].message)

            collectJob.cancel()
            scope.cancel()
            warehouse.close()
        }
    }
}
