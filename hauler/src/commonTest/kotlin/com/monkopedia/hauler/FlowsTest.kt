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
import kotlinx.coroutines.launch
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
                assertEquals(2, result.size)
                assertEquals("a", result[0].message)
                assertEquals("b", result[1].message)

                collectJob.cancelAndJoin()
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
                assertEquals(2, result.size)
                assertEquals("r1", result[0].message)
                assertEquals("r2", result[1].message)

                collectJob.cancelAndJoin()
                scope.cancel()
                warehouse.close()
            }
        }
}
