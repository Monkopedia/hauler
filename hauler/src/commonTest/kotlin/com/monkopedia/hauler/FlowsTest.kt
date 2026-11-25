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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for the polling helpers in [Flows.kt] (BasicDeliveryService.withPickup /
 * dumpWithPickup). The streaming Flow paths on DeliveryService are exercised directly in
 * DeliveryServiceTest / PipelineIntegrationTest.
 */
class FlowsTest {
    private fun box(
        level: Level = Level.INFO,
        loggerName: String = "com.example.Test",
        message: String = "test message",
        timestamp: Long = 1000L,
        threadName: String? = "main",
    ) = Box(level, loggerName, message, timestamp, threadName)

    @Test
    fun withPickup_receivesLiveEvents() =
        runTest {
            withContext(Dispatchers.Default) {
                val warehouse = Warehouse(DeliveryRates())
                val dropBox = warehouse.requestPickup()
                val service = warehouse.deliveries()
                val scope = CoroutineScope(SupervisorJob())

                val result = mutableListOf<Box>()
                val collectJob =
                    launch {
                        service
                            .withPickup(interval = 50.milliseconds, closeScope = scope)
                            .collect { result.add(it) }
                    }
                delay(200)

                dropBox.log(box(message = "a"))
                dropBox.log(box(message = "b"))

                withTimeout(5.seconds) {
                    while (result.size < 2) delay(50)
                }
                collectJob.cancelAndJoin()
                assertEquals(2, result.size)
                assertEquals("a", result[0].message)
                assertEquals("b", result[1].message)

                scope.cancel()
                warehouse.close()
            }
        }

    @Test
    fun dumpWithPickup_returnsReplayedBoxes() =
        runTest {
            withContext(Dispatchers.Default) {
                val warehouse = Warehouse(DeliveryRates(defaultBoxRetention = 100))
                val dropBox = warehouse.requestPickup()
                dropBox.log(box(message = "r1"))
                dropBox.log(box(message = "r2"))

                val service = warehouse.deliveries()
                val scope = CoroutineScope(SupervisorJob())

                val result = mutableListOf<Box>()
                val collectJob =
                    launch {
                        service
                            .dumpWithPickup(interval = 50.milliseconds, closeScope = scope)
                            .collect { result.add(it) }
                    }

                withTimeout(5.seconds) {
                    while (result.size < 2) delay(50)
                }
                collectJob.cancelAndJoin()
                assertEquals(2, result.size)
                assertEquals("r1", result[0].message)
                assertEquals("r2", result[1].message)

                scope.cancel()
                warehouse.close()
            }
        }

    /**
     * #69: a pickup's buffer was sized by `defaultPaletteSize` (500) while the Warehouse replays
     * `defaultBoxRetention` (1000), and the whole replay lands in the buffer on subscribe, before
     * the first poll. Under default [DeliveryRates] both polling paths silently returned only the
     * newest 500 of 1000 retained boxes, while the streaming dump returned all 1000.
     *
     * Runs on the virtual scheduler ON PURPOSE: there, a pickup's collector drains the whole
     * replay before the first poll can run -- the worst case, and the 500-of-1000 shape #69
     * measured on a real Warehouse. On a real dispatcher a poll could interleave with the replay
     * and lose fewer boxes, so the RED would depend on timing. The source is built exactly as [Warehouse] builds its own.
     */
    private suspend fun TestScope.retainedService(): Pair<DeliveryService, List<String>> {
        val rates = DeliveryRates()
        val source = MutableSharedFlow<Box>(replay = rates.defaultBoxRetention)
        val expected = (0 until rates.defaultBoxRetention).map { "m$it" }
        expected.forEach { source.emit(box(message = it)) }
        val service = source.deliveries(backgroundScope, rates)
        // The streaming dump is the baseline the polling paths must match.
        assertEquals(expected, service.dumpDeliveries().toList().map { it.message })
        return service to expected
    }

    /** Count first, so a RED reads "expected 1000, was 500" rather than two 1000-name lists. */
    private fun assertSameMessages(
        expected: List<String>,
        actual: List<Box>,
    ) {
        assertEquals(expected.size, actual.size, "boxes returned of ${expected.size} retained")
        assertEquals(expected, actual.map { it.message })
    }

    @Test
    fun dumpWithPickup_returnsEveryRetainedBox_underDefaultRates() =
        runTest {
            val (service, expected) = retainedService()
            val dumped = mutableListOf<Box>()
            backgroundScope.launch {
                service
                    .dumpWithPickup(interval = 20.milliseconds, closeScope = backgroundScope)
                    .collect { dumped.add(it) }
            }
            // 1000 boxes at 100 per 20ms poll need 200ms; allow five times that.
            advanceTimeBy(1.seconds)
            runCurrent()
            assertSameMessages(expected, dumped)
        }

    @Test
    fun recurringCustomerPickup_returnsEveryRetainedBox_underDefaultRates() =
        runTest {
            val (service, expected) = retainedService()
            val pickup = service.recurringCustomerPickup()
            runCurrent()
            val picked = pickup.get(maxEntries = expected.size).unpack()
            pickup.close()
            assertSameMessages(expected, picked)
        }
}
