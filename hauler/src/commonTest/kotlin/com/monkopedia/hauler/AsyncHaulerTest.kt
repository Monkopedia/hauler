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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AsyncHaulerTest {

    private fun box(
        level: Level = Level.INFO,
        loggerName: String = "com.example.Test",
        message: String = "test message",
        timestamp: Long = 1000L,
        threadName: String? = "main",
    ) = Box(level, loggerName, message, timestamp, threadName)

    @Test
    fun asyncHauler_emitsToUnderlying() = runTest {
        val collected = mutableListOf<Box>()
        val hauler = Hauler { collected.add(it) }
        val async = hauler.asAsync(this)

        val testBox = box(message = "async test")
        async.emit(testBox)
        withTimeout(2.seconds) {
            while (collected.isEmpty()) delay(10)
        }
        assertEquals(1, collected.size)
        assertEquals("async test", collected[0].message)
    }

    @Test
    fun asyncHauler_multipleEmissions() = runTest {
        val collected = mutableListOf<Box>()
        val hauler = Hauler { collected.add(it) }
        val async = hauler.asAsync(this)

        repeat(5) { i ->
            async.emit(box(message = "msg$i"))
        }
        withTimeout(2.seconds) {
            while (collected.size < 5) delay(10)
        }
        assertEquals(5, collected.size)
    }

    @Test
    fun asyncHauler_shipSetsLevel() = runTest {
        val collected = mutableListOf<Box>()
        val hauler = Hauler { collected.add(it) }
        val async = hauler.asAsync(this)

        async.ship(Level.ERROR, "error msg")
        withTimeout(2.seconds) {
            while (collected.isEmpty()) delay(10)
        }
        assertEquals(Level.ERROR, collected[0].level)
        assertTrue(collected[0].message.contains("error msg"))
    }

    @Test
    fun asyncHauler_levelShortcuts() = runTest {
        val collected = mutableListOf<Box>()
        val hauler = Hauler { collected.add(it) }
        val async = hauler.asAsync(this)

        async.error("e")
        async.warn("w")
        async.info("i")
        async.debug("d")
        async.trace("t")

        withTimeout(2.seconds) {
            while (collected.size < 5) delay(10)
        }
        assertEquals(Level.ERROR, collected[0].level)
        assertEquals(Level.WARN, collected[1].level)
        assertEquals(Level.INFO, collected[2].level)
        assertEquals(Level.DEBUG, collected[3].level)
        assertEquals(Level.TRACE, collected[4].level)
    }

    @Test
    fun asyncHauler_shipWithMetadata() = runTest {
        val collected = mutableListOf<Box>()
        val hauler = Hauler { collected.add(it) }
        val async = hauler.asAsync(this)

        async.ship(Level.INFO, "meta", metadata = mapOf("key" to "val"))
        withTimeout(2.seconds) {
            while (collected.isEmpty()) delay(10)
        }
        assertEquals(mapOf("key" to "val"), collected[0].metadata)
    }

    @Test
    fun asyncHauler_shipWithThrowable() = runTest {
        val collected = mutableListOf<Box>()
        val hauler = Hauler { collected.add(it) }
        val async = hauler.asAsync(this)

        async.ship(Level.ERROR, "oops", RuntimeException("boom"))
        withTimeout(2.seconds) {
            while (collected.isEmpty()) delay(10)
        }
        assertTrue(collected[0].message.contains("oops"))
        assertTrue(collected[0].message.contains("boom"))
    }
}
