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

class LogPackerTest {

    private fun box(
        level: Level = Level.INFO,
        loggerName: String = "com.example.Test",
        message: String = "test message",
        timestamp: Long = 1000L,
        threadName: String? = "main",
        metadata: Map<String, String>? = null,
    ) = Box(level, loggerName, message, timestamp, threadName, metadata)

    @Test
    fun log_incrementsSize() = runTest {
        val packer = LogPacker()
        assertEquals(0, packer.size)
        packer.log(box(message = "a"))
        assertEquals(1, packer.size)
        packer.log(box(message = "b"))
        assertEquals(2, packer.size)
    }

    @Test
    fun dumpLogs_returnsPackedPalette() = runTest {
        val packer = LogPacker()
        packer.log(box(message = "a", loggerName = "Logger1"))
        packer.log(box(message = "b", loggerName = "Logger2"))

        val palette = packer.dumpLogs()
        val boxes = palette.unpack()
        assertEquals(2, boxes.size)
        assertEquals("a", boxes[0].message)
        assertEquals("Logger1", boxes[0].loggerName)
        assertEquals("b", boxes[1].message)
        assertEquals("Logger2", boxes[1].loggerName)
    }

    @Test
    fun dumpLogs_resetsSize() = runTest {
        val packer = LogPacker()
        packer.log(box(message = "a"))
        packer.log(box(message = "b"))
        assertEquals(2, packer.size)

        packer.dumpLogs()
        assertEquals(0, packer.size)
    }

    @Test
    fun dumpLogs_emptyReturnsEmptyPalette() = runTest {
        val packer = LogPacker()
        val palette = packer.dumpLogs()
        assertEquals(0, palette.messages.size)
        assertEquals(0, palette.loggerNames.size)
    }

    @Test
    fun dumpLogs_deduplicatesLoggerNames() = runTest {
        val packer = LogPacker()
        packer.log(box(loggerName = "Same"))
        packer.log(box(loggerName = "Same"))
        packer.log(box(loggerName = "Different"))

        val palette = packer.dumpLogs()
        assertEquals(2, palette.loggerNames.size)
        assertEquals("Same", palette.loggerNames[0])
        assertEquals("Different", palette.loggerNames[1])
    }

    @Test
    fun dumpLogs_deduplicatesThreadNames() = runTest {
        val packer = LogPacker()
        packer.log(box(threadName = "t1"))
        packer.log(box(threadName = "t1"))
        packer.log(box(threadName = "t2"))

        val palette = packer.dumpLogs()
        assertEquals(2, palette.threadNames.size)
        assertEquals("t1", palette.threadNames[0])
        assertEquals("t2", palette.threadNames[1])
    }

    @Test
    fun dumpLogs_multipleDumpsIndependent() = runTest {
        val packer = LogPacker()

        packer.log(box(message = "batch1"))
        val p1 = packer.dumpLogs()

        packer.log(box(message = "batch2a"))
        packer.log(box(message = "batch2b"))
        val p2 = packer.dumpLogs()

        // First palette should still be intact (not corrupted by second dump)
        assertEquals(1, p1.unpack().size)
        assertEquals("batch1", p1.unpack()[0].message)

        assertEquals(2, p2.unpack().size)
        assertEquals("batch2a", p2.unpack()[0].message)
        assertEquals("batch2b", p2.unpack()[1].message)
    }

    @Test
    fun dumpLogs_threeDumpsRotatesCorrectly() = runTest {
        val packer = LogPacker()

        packer.log(box(message = "a"))
        val p1 = packer.dumpLogs()

        packer.log(box(message = "b"))
        val p2 = packer.dumpLogs()

        packer.log(box(message = "c"))
        val p3 = packer.dumpLogs()

        // All palettes should have correct data despite buffer reuse
        assertEquals("a", p1.unpack()[0].message)
        assertEquals("b", p2.unpack()[0].message)
        assertEquals("c", p3.unpack()[0].message)
    }

    @Test
    fun log_preservesMetadata() = runTest {
        val packer = LogPacker()
        val meta = mapOf("key" to "value")
        packer.log(box(metadata = meta))

        val palette = packer.dumpLogs()
        assertEquals(meta, palette.unpack()[0].metadata)
    }

    @Test
    fun log_preservesNullThreadName() = runTest {
        val packer = LogPacker()
        packer.log(box(threadName = null))

        val palette = packer.dumpLogs()
        assertEquals(null, palette.unpack()[0].threadName)
    }
}
