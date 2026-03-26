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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversionsTest {

    private fun box(
        level: Level = Level.INFO,
        loggerName: String = "com.example.Test",
        message: String = "test message",
        timestamp: Long = 1000L,
        threadName: String? = "main",
    ) = Box(level, loggerName, message, timestamp, threadName)

    // --- Pack/Unpack roundtrip ---

    @Test
    fun packUnpack_singleItem() {
        val original = listOf(box())
        val unpacked = original.pack().unpack()
        assertEquals(original, unpacked)
    }

    @Test
    fun packUnpack_multipleItems() {
        val original = listOf(
            box(level = Level.INFO, loggerName = "A", message = "msg1", timestamp = 100),
            box(level = Level.WARN, loggerName = "B", message = "msg2", timestamp = 200),
            box(level = Level.ERROR, loggerName = "A", message = "msg3", timestamp = 300),
        )
        val unpacked = original.pack().unpack()
        assertEquals(original, unpacked)
    }

    @Test
    fun packUnpack_preservesOrder() {
        val original = (1..20).map { i ->
            box(
                loggerName = "logger${i % 5}",
                message = "msg $i",
                timestamp = i.toLong(),
                threadName = "thread-${i % 3}",
            )
        }
        val unpacked = original.pack().unpack()
        assertEquals(original, unpacked)
    }

    @Test
    fun packUnpack_nullThreadName() {
        val original = listOf(
            box(threadName = null),
            box(threadName = "main"),
            box(threadName = null),
        )
        val unpacked = original.pack().unpack()
        assertEquals(original, unpacked)
    }

    @Test
    fun packUnpack_allNullThreadNames() {
        val original = listOf(
            box(threadName = null),
            box(threadName = null, message = "msg2"),
        )
        val unpacked = original.pack().unpack()
        assertEquals(original, unpacked)
    }

    @Test
    fun packUnpack_emptyList() {
        val original = emptyList<Box>()
        val palette = original.pack()
        assertEquals(emptyList(), palette.loggerNames)
        assertEquals(emptyList(), palette.threadNames)
        assertEquals(emptyList(), palette.messages)
        assertEquals(original, palette.unpack())
    }

    @Test
    fun packUnpack_allLevels() {
        val original = Level.entries.map { level ->
            box(level = level, message = "level ${level.name}")
        }
        val unpacked = original.pack().unpack()
        assertEquals(original, unpacked)
    }

    // --- Pack deduplication ---

    @Test
    fun pack_deduplicatesLoggerNames() {
        val original = listOf(
            box(loggerName = "A"),
            box(loggerName = "B"),
            box(loggerName = "A"),
            box(loggerName = "B"),
            box(loggerName = "C"),
        )
        val palette = original.pack()
        assertEquals(listOf("A", "B", "C"), palette.loggerNames)
    }

    @Test
    fun pack_deduplicatesThreadNames() {
        val original = listOf(
            box(threadName = "t1"),
            box(threadName = "t2"),
            box(threadName = "t1"),
            box(threadName = "t2"),
        )
        val palette = original.pack()
        assertEquals(listOf("t1", "t2"), palette.threadNames)
    }

    @Test
    fun pack_loggerIndicesAreCorrect() {
        val original = listOf(
            box(loggerName = "A"),
            box(loggerName = "B"),
            box(loggerName = "A"),
        )
        val palette = original.pack()
        assertEquals(0, palette.messages[0].loggerName) // "A" -> index 0
        assertEquals(1, palette.messages[1].loggerName) // "B" -> index 1
        assertEquals(0, palette.messages[2].loggerName) // "A" -> index 0
    }

    @Test
    fun pack_threadIndicesAreCorrect() {
        val original = listOf(
            box(threadName = "main"),
            box(threadName = "worker"),
            box(threadName = "main"),
            box(threadName = null),
        )
        val palette = original.pack()
        assertEquals(0, palette.messages[0].threadName) // "main" -> index 0
        assertEquals(1, palette.messages[1].threadName) // "worker" -> index 1
        assertEquals(0, palette.messages[2].threadName) // "main" -> index 0
        assertNull(palette.messages[3].threadName) // null stays null
    }

    // --- Palette.forEach ---

    @Test
    fun forEach_visitsAllItems() {
        val original = listOf(
            box(message = "a"),
            box(message = "b"),
            box(message = "c"),
        )
        val palette = original.pack()
        val visited = mutableListOf<Box>()
        palette.forEach { visited.add(it) }
        assertEquals(original, visited)
    }

    // --- Level conversion ---

    @Test
    fun levelRoundtrip() {
        for (level in Level.entries) {
            with(Level.Companion) {
                assertEquals(level, level.intLevel.asLevel())
            }
        }
    }

    // --- Metadata roundtrip ---

    @Test
    fun packUnpack_withMetadata() {
        val original = listOf(
            box().copy(metadata = mapOf("key1" to "value1", "key2" to "value2")),
            box(message = "msg2").copy(metadata = mapOf("requestId" to "abc-123")),
        )
        val unpacked = original.pack().unpack()
        assertEquals(original, unpacked)
    }

    @Test
    fun packUnpack_mixedMetadata() {
        val original = listOf(
            box().copy(metadata = mapOf("key" to "val")),
            box(message = "msg2"),
            box(message = "msg3").copy(metadata = null),
        )
        val unpacked = original.pack().unpack()
        assertEquals(original, unpacked)
    }

    @Test
    fun packUnpack_nullMetadata_default() {
        val original = listOf(box())
        val unpacked = original.pack().unpack()
        assertEquals(null, unpacked[0].metadata)
    }

    // --- combine() ---

    @Test
    fun combine_noThrowable_returnsMessage() {
        assertEquals("hello", combine("hello", null))
    }

    @Test
    fun combine_withThrowable_appendsStackTrace() {
        val throwable = RuntimeException("boom")
        val result = combine("hello", throwable)
        assertTrue(result.startsWith("hello\n"))
        assertTrue(result.contains("RuntimeException"))
        assertTrue(result.contains("boom"))
    }
}
