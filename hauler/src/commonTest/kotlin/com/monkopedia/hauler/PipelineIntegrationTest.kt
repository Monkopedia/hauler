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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end integration tests verifying the full pipeline:
 * DropBox/LoadingDock -> Warehouse -> DeliveryService -> consumer
 */
class PipelineIntegrationTest {

    private fun box(
        level: Level = Level.INFO,
        loggerName: String = "com.example.Test",
        message: String = "test message",
        timestamp: Long = 1000L,
        threadName: String? = "main",
    ) = Box(level, loggerName, message, timestamp, threadName)

    private suspend fun awaitCondition(description: String = "", check: () -> Boolean) {
        withTimeout(5.seconds) {
            while (!check()) delay(1)
        }
    }

    @Test
    fun dropBox_to_streamDeliveries() = runTest {
        withContext(Dispatchers.Default) {
            val warehouse = Warehouse(DeliveryRates())
            val dropBox = warehouse.requestPickup()
            val service = warehouse.deliveries()

            val received = mutableListOf<Box>()
            val collectJob = launch {
                service.streamDeliveries().collect { received.add(it) }
            }
            delay(50)

            dropBox.log(box(level = Level.INFO, message = "first"))
            dropBox.log(box(level = Level.WARN, message = "second"))
            dropBox.log(box(level = Level.ERROR, message = "third"))

            awaitCondition { received.size >= 3 }

            assertEquals(3, received.size)
            assertEquals(Level.INFO, received[0].level)
            assertEquals("first", received[0].message)
            assertEquals(Level.WARN, received[1].level)
            assertEquals("second", received[1].message)
            assertEquals(Level.ERROR, received[2].level)
            assertEquals("third", received[2].message)
            collectJob.cancelAndJoin()
            warehouse.close()
        }
    }

    @Test
    fun loadingDock_to_streamDeliveries() = runTest {
        withContext(Dispatchers.Default) {
            val warehouse = Warehouse(DeliveryRates())
            val dock = warehouse.requestDockPickup()
            val service = warehouse.deliveries()

            val received = mutableListOf<Box>()
            val collectJob = launch {
                service.streamDeliveries().collect { received.add(it) }
            }
            delay(50)

            val boxes = listOf(
                box(message = "bulk1"),
                box(message = "bulk2"),
                box(message = "bulk3"),
            )
            dock.bulkLog(boxes.pack())

            awaitCondition { received.size >= 3 }

            assertEquals(3, received.size)
            assertEquals("bulk1", received[0].message)
            assertEquals("bulk2", received[1].message)
            assertEquals("bulk3", received[2].message)
            collectJob.cancelAndJoin()
            warehouse.close()
        }
    }

    @Test
    fun multipleSources_mergeIntoSingleDelivery() = runTest {
        withContext(Dispatchers.Default) {
            val warehouse = Warehouse(DeliveryRates())
            val drop1 = warehouse.requestPickup()
            val drop2 = warehouse.requestPickup()
            val dock = warehouse.requestDockPickup()
            val service = warehouse.deliveries()

            val received = mutableListOf<Box>()
            val collectJob = launch {
                service.streamDeliveries().collect { received.add(it) }
            }
            delay(50)

            drop1.log(box(loggerName = "Source1", message = "from-drop1"))
            drop2.log(box(loggerName = "Source2", message = "from-drop2"))
            dock.bulkLog(
                listOf(
                    box(loggerName = "Source3", message = "from-dock1"),
                    box(loggerName = "Source3", message = "from-dock2"),
                ).pack(),
            )

            awaitCondition { received.size >= 4 }

            assertEquals(4, received.size)
            assertTrue(received.any { it.loggerName == "Source1" })
            assertTrue(received.any { it.loggerName == "Source2" })
            assertEquals(2, received.count { it.loggerName == "Source3" })
            collectJob.cancelAndJoin()
            warehouse.close()
        }
    }

    @Test
    fun pipeline_withFiltering() = runTest {
        withContext(Dispatchers.Default) {
            val warehouse = Warehouse(DeliveryRates())
            val dropBox = warehouse.requestPickup()
            val service = warehouse.deliveries()
            val filtered = service.weighIn(LevelFilter(LevelMatchMode.GT, Level.INFO))

            val received = mutableListOf<Box>()
            val collectJob = launch {
                filtered.streamDeliveries().collect { received.add(it) }
            }
            delay(50)

            dropBox.log(box(level = Level.DEBUG, message = "skip-debug"))
            dropBox.log(box(level = Level.INFO, message = "skip-info"))
            dropBox.log(box(level = Level.WARN, message = "keep-warn"))
            dropBox.log(box(level = Level.ERROR, message = "keep-error"))

            awaitCondition { received.size >= 2 }

            assertEquals(2, received.size)
            assertEquals("keep-warn", received[0].message)
            assertEquals("keep-error", received[1].message)
            collectJob.cancelAndJoin()
            warehouse.close()
        }
    }

    @Test
    fun pipeline_withBatchedDelivery() = runTest {
        withContext(Dispatchers.Default) {
            val rates = DeliveryRates(
                defaultPaletteSize = 2,
                defaultPaletteInterval = 100.seconds,
            )
            val warehouse = Warehouse(rates)
            val dropBox = warehouse.requestPickup()
            val service = warehouse.deliveries()

            val received = mutableListOf<Palette>()
            val collectJob = launch {
                service.streamDeliveriesPacked().collect { received.add(it) }
            }
            delay(50)

            dropBox.log(box(message = "batch-a"))
            dropBox.log(box(message = "batch-b"))

            awaitCondition("batched delivery received") {
                val allBoxes = received.flatMap { it.unpack() }
                allBoxes.size >= 2
            }

            val allBoxes = received.flatMap { it.unpack() }
            assertTrue(allBoxes.any { it.message == "batch-a" })
            assertTrue(allBoxes.any { it.message == "batch-b" })
            collectJob.cancelAndJoin()
            warehouse.close()
        }
    }

    @Test
    fun pipeline_metadataPreservedEndToEnd() = runTest {
        withContext(Dispatchers.Default) {
            val warehouse = Warehouse(DeliveryRates())
            val dropBox = warehouse.requestPickup()
            val service = warehouse.deliveries()

            val received = mutableListOf<Box>()
            val collectJob = launch {
                service.streamDeliveries().collect { received.add(it) }
            }
            delay(50)

            val meta = mapOf("requestId" to "abc-123", "userId" to "42")
            dropBox.log(box(message = "with-meta").copy(metadata = meta))

            awaitCondition { received.isNotEmpty() }

            assertEquals(1, received.size)
            assertEquals(meta, received[0].metadata)
            collectJob.cancelAndJoin()
            warehouse.close()
        }
    }

    @Test
    fun pipeline_replayAndLiveDelivery() = runTest {
        withContext(Dispatchers.Default) {
            val warehouse = Warehouse(DeliveryRates(defaultBoxRetention = 100))
            val dropBox = warehouse.requestPickup()

            // Emit before subscribing — goes to replay cache
            dropBox.log(box(message = "historical"))

            val service = warehouse.deliveries()

            // Dump replay cache as a finite flow
            val dumped = service.dumpDeliveries().toList()
            assertEquals(1, dumped.size)
            assertEquals("historical", dumped[0].message)

            // Subscribe — SharedFlow replay means subscriber also gets cached events
            val live = mutableListOf<Box>()
            val collectJob = launch {
                service.streamDeliveries().collect { live.add(it) }
            }
            delay(50)

            dropBox.log(box(message = "live"))

            awaitCondition("live delivery received") {
                live.any { it.message == "live" }
            }

            // Subscriber receives replayed "historical" + new "live"
            assertTrue(live.any { it.message == "historical" })
            assertTrue(live.any { it.message == "live" })
            collectJob.cancelAndJoin()
            warehouse.close()
        }
    }
}
