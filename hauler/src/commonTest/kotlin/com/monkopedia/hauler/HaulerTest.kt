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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HaulerTest {

    private fun collectingHauler(): Pair<Hauler, MutableList<Box>> {
        val collected = mutableListOf<Box>()
        val hauler = Hauler { collected.add(it) }
        return hauler to collected
    }

    @Test
    fun ship_emitsBoxWithCorrectLevel() = runTest {
        val (hauler, collected) = collectingHauler()
        hauler.ship(Level.WARN, "test message")
        assertEquals(1, collected.size)
        assertEquals(Level.WARN, collected[0].level)
        assertEquals("test message", collected[0].message)
    }

    @Test
    fun ship_includesThrowableInMessage() = runTest {
        val (hauler, collected) = collectingHauler()
        val ex = RuntimeException("boom")
        hauler.ship(Level.ERROR, "failed", ex)
        assertEquals(1, collected.size)
        assertTrue(collected[0].message.startsWith("failed\n"))
        assertTrue(collected[0].message.contains("RuntimeException"))
        assertTrue(collected[0].message.contains("boom"))
    }

    @Test
    fun ship_includesMetadata() = runTest {
        val (hauler, collected) = collectingHauler()
        hauler.ship(Level.INFO, "msg", metadata = mapOf("k" to "v"))
        assertEquals(mapOf("k" to "v"), collected[0].metadata)
    }

    @Test
    fun error_usesErrorLevel() = runTest {
        val (hauler, collected) = collectingHauler()
        hauler.error("err msg")
        assertEquals(Level.ERROR, collected[0].level)
        assertEquals("err msg", collected[0].message)
    }

    @Test
    fun warn_usesWarnLevel() = runTest {
        val (hauler, collected) = collectingHauler()
        hauler.warn("warn msg")
        assertEquals(Level.WARN, collected[0].level)
    }

    @Test
    fun info_usesInfoLevel() = runTest {
        val (hauler, collected) = collectingHauler()
        hauler.info("info msg")
        assertEquals(Level.INFO, collected[0].level)
    }

    @Test
    fun debug_usesDebugLevel() = runTest {
        val (hauler, collected) = collectingHauler()
        hauler.debug("debug msg")
        assertEquals(Level.DEBUG, collected[0].level)
    }

    @Test
    fun trace_usesTraceLevel() = runTest {
        val (hauler, collected) = collectingHauler()
        hauler.trace("trace msg")
        assertEquals(Level.TRACE, collected[0].level)
    }

    @Test
    fun levelShortcuts_passMetadataThrough() = runTest {
        val (hauler, collected) = collectingHauler()
        val meta = mapOf("req" to "123")
        hauler.error("e", metadata = meta)
        hauler.warn("w", metadata = meta)
        hauler.info("i", metadata = meta)
        hauler.debug("d", metadata = meta)
        hauler.trace("t", metadata = meta)
        assertEquals(5, collected.size)
        collected.forEach { assertEquals(meta, it.metadata) }
    }

    @Test
    fun levelShortcuts_passThrowableThrough() = runTest {
        val (hauler, collected) = collectingHauler()
        val ex = IllegalStateException("bad")
        hauler.error("e", ex)
        hauler.warn("w", ex)
        hauler.info("i", ex)
        hauler.debug("d", ex)
        hauler.trace("t", ex)
        assertEquals(5, collected.size)
        collected.forEach {
            assertTrue(it.message.contains("IllegalStateException"))
        }
    }

    @Test
    fun ship_setsLoggerNameToHauler() = runTest {
        val (hauler, collected) = collectingHauler()
        hauler.ship(Level.INFO, "test")
        assertEquals("Hauler", collected[0].loggerName)
    }

    @Test
    fun ship_setsTimestamp() = runTest {
        val (hauler, collected) = collectingHauler()
        hauler.ship(Level.INFO, "test")
        assertTrue(collected[0].timestamp > 0)
    }
}
