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

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
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
    fun named_setsLoggerName() = runTest {
        val collected = mutableListOf<Box>()
        val hauler = Hauler { collected.add(it) }
        val named = hauler.named("MyLogger")

        named.emit(box(loggerName = "original"))
        assertEquals(1, collected.size)
        assertEquals("MyLogger", collected[0].loggerName)
    }

    @Test
    fun named_preservesOtherFields() = runTest {
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
    fun named_chainedInnerWins() = runTest {
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
    fun level_filtersLowSeverity() = runTest {
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
    fun level_allowsEqualLevel() = runTest {
        val collected = mutableListOf<Box>()
        val hauler = Hauler { collected.add(it) }
        val filtered = hauler.level(Level.INFO)

        filtered.emit(box(level = Level.INFO))
        assertEquals(1, collected.size)
    }

    @Test
    fun level_allowsHigherSeverity() = runTest {
        val collected = mutableListOf<Box>()
        val hauler = Hauler { collected.add(it) }
        val filtered = hauler.level(Level.DEBUG)

        filtered.emit(box(level = Level.ERROR))
        assertEquals(1, collected.size)
    }

    @Test
    fun level_blocksLowerSeverity() = runTest {
        val collected = mutableListOf<Box>()
        val hauler = Hauler { collected.add(it) }
        val filtered = hauler.level(Level.ERROR)

        filtered.emit(box(level = Level.WARN))
        assertEquals(0, collected.size)
    }

    // --- hauler() factory ---

    @Test
    fun haulerFactory_setsName() = runTest {
        val collected = mutableListOf<Box>()
        val base = Hauler { collected.add(it) }
        val named = base.named("TestClass")
        named.emit(box())
        assertEquals("TestClass", collected[0].loggerName)
    }

    // --- Garage global routing ---

    @Test
    fun garage_rootHaulerRoutesToDeliveries() = runTest {
        val result = mutableListOf<Box>()
        val job = launch {
            Garage.deliveries.take(1).toList().let { result.addAll(it) }
        }
        delay(50)
        Garage.rootHauler.emit(box(message = "garage test"))
        withTimeout(2.seconds) { job.join() }
        assertEquals(1, result.size)
        assertEquals("garage test", result[0].message)
    }

    @Test
    fun garage_namedHaulerSetsLoggerName() = runTest {
        val result = mutableListOf<Box>()
        val job = launch {
            Garage.deliveries.take(1).toList().let { result.addAll(it) }
        }
        delay(50)

        val namedHauler = Garage.rootHauler.named("MyComponent")
        namedHauler.emit(box())
        withTimeout(2.seconds) { job.join() }
        assertEquals("MyComponent", result[0].loggerName)
    }
}
