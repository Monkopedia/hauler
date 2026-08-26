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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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

    /**
     * A list written by a collector coroutine and read by the test body.
     *
     * Every test here runs on [Dispatchers.Default], so those are genuinely different threads: a
     * bare `mutableListOf` appended inside `collect { }` and iterated by an assertion produced a
     * `ConcurrentModificationException` in 8 of 100 runs. Reads take a snapshot under the same
     * lock the writer uses, so an assertion always sees a consistent list.
     */
    private class Collected<T> {
        private val lock = Mutex()
        private val items = mutableListOf<T>()

        suspend fun add(item: T) {
            lock.withLock { items.add(item) }
        }

        suspend fun snapshot(): List<T> = lock.withLock { items.toList() }
    }

    @Test
    fun dropBox_to_streamDeliveries() =
        runTest {
            withContext(Dispatchers.Default) {
                val warehouse = Warehouse(DeliveryRates())
                val dropBox = warehouse.requestPickup()
                val service = warehouse.deliveries()

                val received = Collected<Box>()
                val collectJob =
                    launch {
                        service.streamDeliveries().collect { received.add(it) }
                    }
                delay(50)

                dropBox.log(box(level = Level.INFO, message = "first"))
                dropBox.log(box(level = Level.WARN, message = "second"))
                dropBox.log(box(level = Level.ERROR, message = "third"))

                awaitCondition { received.snapshot().size >= 3 }

                val snap = received.snapshot()
                assertEquals(3, snap.size)
                assertEquals(Level.INFO, snap[0].level)
                assertEquals("first", snap[0].message)
                assertEquals(Level.WARN, snap[1].level)
                assertEquals("second", snap[1].message)
                assertEquals(Level.ERROR, snap[2].level)
                assertEquals("third", snap[2].message)
                collectJob.cancelAndJoin()
                warehouse.close()
            }
        }

    @Test
    fun loadingDock_to_streamDeliveries() =
        runTest {
            withContext(Dispatchers.Default) {
                val warehouse = Warehouse(DeliveryRates())
                val dock = warehouse.requestDockPickup()
                val service = warehouse.deliveries()

                val received = Collected<Box>()
                val collectJob =
                    launch {
                        service.streamDeliveries().collect { received.add(it) }
                    }
                delay(50)

                val boxes =
                    listOf(
                        box(message = "bulk1"),
                        box(message = "bulk2"),
                        box(message = "bulk3"),
                    )
                dock.bulkLog(boxes.pack())

                awaitCondition { received.snapshot().size >= 3 }

                val snap = received.snapshot()
                assertEquals(3, snap.size)
                assertEquals("bulk1", snap[0].message)
                assertEquals("bulk2", snap[1].message)
                assertEquals("bulk3", snap[2].message)
                collectJob.cancelAndJoin()
                warehouse.close()
            }
        }

    @Test
    fun multipleSources_mergeIntoSingleDelivery() =
        runTest {
            withContext(Dispatchers.Default) {
                val warehouse = Warehouse(DeliveryRates())
                val drop1 = warehouse.requestPickup()
                val drop2 = warehouse.requestPickup()
                val dock = warehouse.requestDockPickup()
                val service = warehouse.deliveries()

                val received = Collected<Box>()
                val collectJob =
                    launch {
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

                awaitCondition { received.snapshot().size >= 4 }

                val snap = received.snapshot()
                assertEquals(4, snap.size)
                assertTrue(snap.any { it.loggerName == "Source1" })
                assertTrue(snap.any { it.loggerName == "Source2" })
                assertEquals(2, snap.count { it.loggerName == "Source3" })
                collectJob.cancelAndJoin()
                warehouse.close()
            }
        }

    @Test
    fun pipeline_withFiltering() =
        runTest {
            withContext(Dispatchers.Default) {
                val warehouse = Warehouse(DeliveryRates())
                val dropBox = warehouse.requestPickup()
                val service = warehouse.deliveries()
                val filtered = service.weighIn(LevelFilter(LevelMatchMode.GT, Level.INFO))

                val received = Collected<Box>()
                val collectJob =
                    launch {
                        filtered.streamDeliveries().collect { received.add(it) }
                    }
                delay(50)

                dropBox.log(box(level = Level.DEBUG, message = "skip-debug"))
                dropBox.log(box(level = Level.INFO, message = "skip-info"))
                dropBox.log(box(level = Level.WARN, message = "keep-warn"))
                dropBox.log(box(level = Level.ERROR, message = "keep-error"))

                awaitCondition { received.snapshot().size >= 2 }

                val snap = received.snapshot()
                assertEquals(2, snap.size)
                assertEquals("keep-warn", snap[0].message)
                assertEquals("keep-error", snap[1].message)
                collectJob.cancelAndJoin()
                warehouse.close()
            }
        }

    @Test
    fun pipeline_withBatchedDelivery() =
        runTest {
            withContext(Dispatchers.Default) {
                val rates =
                    DeliveryRates(
                        defaultPaletteSize = 2,
                        defaultPaletteInterval = 100.seconds,
                    )
                val warehouse = Warehouse(rates)
                val dropBox = warehouse.requestPickup()
                val service = warehouse.deliveries()

                val received = Collected<Palette>()
                val collectJob =
                    launch {
                        service.streamDeliveriesPacked().collect { received.add(it) }
                    }
                delay(50)

                dropBox.log(box(message = "batch-a"))
                dropBox.log(box(message = "batch-b"))

                awaitCondition("batched delivery received") {
                    val allBoxes = received.snapshot().flatMap { it.unpack() }
                    allBoxes.size >= 2
                }

                val allBoxes = received.snapshot().flatMap { it.unpack() }
                assertTrue(allBoxes.any { it.message == "batch-a" })
                assertTrue(allBoxes.any { it.message == "batch-b" })
                collectJob.cancelAndJoin()
                warehouse.close()
            }
        }

    @Test
    fun pipeline_metadataPreservedEndToEnd() =
        runTest {
            withContext(Dispatchers.Default) {
                val warehouse = Warehouse(DeliveryRates())
                val dropBox = warehouse.requestPickup()
                val service = warehouse.deliveries()

                val received = Collected<Box>()
                val collectJob =
                    launch {
                        service.streamDeliveries().collect { received.add(it) }
                    }
                delay(50)

                val meta = mapOf("requestId" to "abc-123", "userId" to "42")
                dropBox.log(box(message = "with-meta").copy(metadata = meta))

                awaitCondition { received.snapshot().isNotEmpty() }

                val snap = received.snapshot()
                assertEquals(1, snap.size)
                assertEquals(meta, snap[0].metadata)
                collectJob.cancelAndJoin()
                warehouse.close()
            }
        }

    @Test
    fun pipeline_replayAndLiveDelivery() =
        runTest {
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
                val live = Collected<Box>()
                val collectJob =
                    launch {
                        service.streamDeliveries().collect { live.add(it) }
                    }
                delay(50)

                dropBox.log(box(message = "live"))

                awaitCondition("live delivery received") {
                    live.snapshot().any { it.message == "live" }
                }

                // Subscriber receives replayed "historical" + new "live"
                val snap = live.snapshot()
                assertTrue(snap.any { it.message == "historical" })
                assertTrue(snap.any { it.message == "live" })
                collectJob.cancelAndJoin()
                warehouse.close()
            }
        }
}
