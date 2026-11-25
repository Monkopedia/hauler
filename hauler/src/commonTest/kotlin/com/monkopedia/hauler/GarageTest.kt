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

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class GarageTest {
    private fun box(
        level: Level = Level.INFO,
        loggerName: String = "com.example.Test",
        message: String = "test message",
        timestamp: Long = 1000L,
        threadName: String? = "main",
    ) = Box(level, loggerName, message, timestamp, threadName)

    // --- Hauler.named ---

    @Test
    fun named_setsLoggerName() =
        runTest {
            val collected = mutableListOf<Box>()
            val hauler = Hauler { collected.add(it) }
            val named = hauler.named("MyLogger")

            named.emit(box(loggerName = "original"))
            assertEquals(1, collected.size)
            assertEquals("MyLogger", collected[0].loggerName)
        }

    @Test
    fun named_preservesOtherFields() =
        runTest {
            val collected = mutableListOf<Box>()
            val hauler = Hauler { collected.add(it) }
            val named = hauler.named("MyLogger")

            val original = box(level = Level.ERROR, message = "hello", timestamp = 42L)
            named.emit(original)
            assertEquals(Level.ERROR, collected[0].level)
            assertEquals("hello", collected[0].message)
            assertEquals(42L, collected[0].timestamp)
        }

    @Test
    fun named_chainedInnerWins() =
        runTest {
            // When chaining named("First").named("Second"), the outer wrapper sets "Second"
            // then the inner wrapper overwrites with "First", so "First" arrives at the base.
            val collected = mutableListOf<Box>()
            val hauler = Hauler { collected.add(it) }
            val named = hauler.named("First").named("Second")

            named.emit(box())
            assertEquals("First", collected[0].loggerName)
        }

    // --- Hauler.level ---

    @Test
    fun level_filtersLowSeverity() =
        runTest {
            val collected = mutableListOf<Box>()
            val hauler = Hauler { collected.add(it) }
            val filtered = hauler.level(Level.WARN)

            filtered.emit(box(level = Level.DEBUG))
            filtered.emit(box(level = Level.INFO))
            filtered.emit(box(level = Level.WARN))
            filtered.emit(box(level = Level.ERROR))

            assertEquals(2, collected.size)
            assertEquals(Level.WARN, collected[0].level)
            assertEquals(Level.ERROR, collected[1].level)
        }

    @Test
    fun level_allowsEqualLevel() =
        runTest {
            val collected = mutableListOf<Box>()
            val hauler = Hauler { collected.add(it) }
            val filtered = hauler.level(Level.INFO)

            filtered.emit(box(level = Level.INFO))
            assertEquals(1, collected.size)
        }

    @Test
    fun level_allowsHigherSeverity() =
        runTest {
            val collected = mutableListOf<Box>()
            val hauler = Hauler { collected.add(it) }
            val filtered = hauler.level(Level.DEBUG)

            filtered.emit(box(level = Level.ERROR))
            assertEquals(1, collected.size)
        }

    @Test
    fun level_blocksLowerSeverity() =
        runTest {
            val collected = mutableListOf<Box>()
            val hauler = Hauler { collected.add(it) }
            val filtered = hauler.level(Level.ERROR)

            filtered.emit(box(level = Level.WARN))
            assertEquals(0, collected.size)
        }

    // --- hauler() factory ---

    private class SampleComponent

    @Test
    fun haulerFactory_byName_setsLoggerNameAndRoutesToGarage() =
        runTest {
            val result = mutableListOf<Box>()
            val job =
                launch {
                    Garage.deliveries
                        .take(1)
                        .toList()
                        .let { result.addAll(it) }
                }
            delay(50)

            hauler("ByNameFactory").emit(box(message = "by name"))

            withTimeout(2.seconds) { job.join() }
            assertEquals(1, result.size)
            assertEquals("ByNameFactory", result[0].loggerName)
            assertEquals("by name", result[0].message)
        }

    @Test
    fun haulerFactory_reifiedReceiver_usesSimpleName() =
        runTest {
            val result = mutableListOf<Box>()
            val job =
                launch {
                    Garage.deliveries
                        .take(1)
                        .toList()
                        .let { result.addAll(it) }
                }
            delay(50)

            SampleComponent().hauler().emit(box(message = "from receiver"))

            withTimeout(2.seconds) { job.join() }
            assertEquals(1, result.size)
            assertEquals("SampleComponent", result[0].loggerName)
        }

    @Test
    fun createHauler_reified_usesSimpleName() =
        runTest {
            val result = mutableListOf<Box>()
            val job =
                launch {
                    Garage.deliveries
                        .take(1)
                        .toList()
                        .let { result.addAll(it) }
                }
            delay(50)

            createHauler<SampleComponent>().emit(box(message = "from type"))

            withTimeout(2.seconds) { job.join() }
            assertEquals(1, result.size)
            assertEquals("SampleComponent", result[0].loggerName)
        }

    // --- Garage global routing ---

    @Test
    fun garage_rootHaulerRoutesToDeliveries() =
        runTest {
            val result = mutableListOf<Box>()
            val job =
                launch {
                    Garage.deliveries
                        .take(1)
                        .toList()
                        .let { result.addAll(it) }
                }
            delay(50)
            Garage.rootHauler.emit(box(message = "garage test"))
            withTimeout(2.seconds) { job.join() }
            assertEquals(1, result.size)
            assertEquals("garage test", result[0].message)
        }

    @Test
    fun garage_namedHaulerSetsLoggerName() =
        runTest {
            val result = mutableListOf<Box>()
            val job =
                launch {
                    Garage.deliveries
                        .take(1)
                        .toList()
                        .let { result.addAll(it) }
                }
            delay(50)

            val namedHauler = Garage.rootHauler.named("MyComponent")
            namedHauler.emit(box())
            withTimeout(2.seconds) { job.join() }
            assertEquals("MyComponent", result[0].loggerName)
        }

    // --- Garage overflow accounting (#45) ---

    @Test
    fun garage_everyBoxIsDeliveredOrCountedAsDropped() =
        runTest {
            withContext(Dispatchers.Default) {
                val tag = "overflow-accounting"
                val droppedBefore = Garage.droppedBoxes.value
                val firstIndex = CompletableDeferred<Int>()
                val release = CompletableDeferred<Unit>()
                val delivered = atomic(0)
                val job =
                    launch {
                        Garage.deliveries.filter { it.loggerName == tag }.collect {
                            delivered.incrementAndGet()
                            if (firstIndex.complete(it.message.toInt())) release.await()
                        }
                    }
                // Boxes emitted before the collector subscribes reach no one (replay = 0), so
                // keep emitting until it holds one. From then on it is stuck: the 64-box buffer
                // fills and every later box must either be buffered or counted as dropped.
                var emitted = 0
                awaitCondition("the collector to receive its first box") {
                    Garage.rootHauler.emit(box(loggerName = tag, message = "${emitted++}"))
                    firstIndex.isCompleted
                }
                repeat(200) {
                    Garage.rootHauler.emit(box(loggerName = tag, message = "${emitted++}"))
                }
                val offered = emitted - firstIndex.await()
                release.complete(Unit)

                // All it can ever receive: the box it held, plus the full buffer behind it.
                awaitCondition("the collector to drain the 64-box buffer") {
                    delivered.value == 1 + 64
                }
                assertEquals(
                    (offered - delivered.value).toLong(),
                    Garage.droppedBoxes.value - droppedBefore,
                    "of $offered boxes offered, ${delivered.value} were delivered; " +
                        "every other one must be counted in droppedBoxes",
                )
                job.cancel()
            }
        }
}
