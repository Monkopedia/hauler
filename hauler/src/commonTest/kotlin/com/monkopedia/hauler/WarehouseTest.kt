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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class WarehouseTest {

    private fun box(
        level: Level = Level.INFO,
        loggerName: String = "com.example.Test",
        message: String = "test message",
        timestamp: Long = 1000L,
        threadName: String? = "main",
    ) = Box(level, loggerName, message, timestamp, threadName)

    @Test
    fun dropBox_sendsToDelivery() = runTest {
        val warehouse = Warehouse()
        val dropBox = warehouse.requestPickup()
        val deliveryService = warehouse.deliveries()

        val received = mutableListOf<Box>()
        val ready = CompletableDeferred<Unit>()
        val receiver = object : AutomaticDelivery {
            override suspend fun onLogEvent(event: Box) {
                received.add(event)
                ready.complete(Unit)
            }
        }
        deliveryService.registerDelivery(receiver)
        delay(50) // let registration settle

        val testBox = box(message = "hello")
        dropBox.log(testBox)
        withTimeout(2.seconds) { ready.await() }
        assertEquals(listOf(testBox), received)
        warehouse.close()
    }

    @Test
    fun loadingDock_sendsToDelivery() = runTest {
        val warehouse = Warehouse()
        val dock = warehouse.requestDockPickup()
        val deliveryService = warehouse.deliveries()

        val received = mutableListOf<Box>()
        val done = CompletableDeferred<Unit>()
        val boxes = listOf(box(message = "a"), box(message = "b"))
        val receiver = object : AutomaticDelivery {
            override suspend fun onLogEvent(event: Box) {
                received.add(event)
                if (received.size >= 2) done.complete(Unit)
            }
        }
        deliveryService.registerDelivery(receiver)
        delay(50)

        dock.bulkLog(boxes.pack())
        withTimeout(2.seconds) { done.await() }
        assertEquals(boxes, received)
        warehouse.close()
    }

    @Test
    fun replayCache_returnsHistoricalLogs() = runTest {
        val warehouse = Warehouse(DeliveryRates(defaultBoxRetention = 100))
        val dropBox = warehouse.requestPickup()
        val testBox = box(message = "historical")
        dropBox.log(testBox)

        val deliveryService = warehouse.deliveries()
        val received = mutableListOf<Box>()
        val done = CompletableDeferred<Unit>()
        val receiver = object : AutomaticDelivery {
            override suspend fun onLogEvent(event: Box) {
                received.add(event)
                done.complete(Unit)
            }
        }
        deliveryService.dumpDelivery(receiver)
        assertEquals(listOf(testBox), received)
        warehouse.close()
    }

    @Test
    fun multipleDropBoxes_allFeedSameFlow() = runTest {
        val warehouse = Warehouse()
        val drop1 = warehouse.requestPickup()
        val drop2 = warehouse.requestPickup()
        val deliveryService = warehouse.deliveries()

        val received = mutableListOf<Box>()
        val done = CompletableDeferred<Unit>()
        val receiver = object : AutomaticDelivery {
            override suspend fun onLogEvent(event: Box) {
                received.add(event)
                if (received.size >= 2) done.complete(Unit)
            }
        }
        deliveryService.registerDelivery(receiver)
        delay(50)

        drop1.log(box(message = "from1"))
        drop2.log(box(message = "from2"))
        withTimeout(2.seconds) { done.await() }
        assertEquals(2, received.size)
        assertTrue(received.any { it.message == "from1" })
        assertTrue(received.any { it.message == "from2" })
        warehouse.close()
    }
}
