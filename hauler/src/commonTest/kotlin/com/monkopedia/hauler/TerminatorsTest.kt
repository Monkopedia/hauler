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

import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminatorsTest {

    private fun box(
        level: Level = Level.INFO,
        loggerName: String = "com.example.Test",
        message: String = "test message",
        timestamp: Long = 1000L,
        threadName: String? = "main",
    ) = Box(level, loggerName, message, timestamp, threadName)

    @Test
    fun defaultFormat_singleLine() = runTest {
        val lines = mutableListOf<String>()
        val collector = FlowCollector<String> { lines.add(it) }
        collector.defaultFormat(box(message = "hello world"))

        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("hello world"))
        assertTrue(lines[0].contains("com.example.Test"))
        assertTrue(lines[0].contains("main"))
    }

    @Test
    fun defaultFormat_multiLine() = runTest {
        val lines = mutableListOf<String>()
        val collector = FlowCollector<String> { lines.add(it) }
        collector.defaultFormat(box(message = "line1\nline2\nline3"))

        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("line1"))
        assertTrue(lines[1].contains("line2"))
        assertTrue(lines[2].contains("line3"))
    }

    @Test
    fun defaultFormat_multiLineSharesPrefix() = runTest {
        val lines = mutableListOf<String>()
        val collector = FlowCollector<String> { lines.add(it) }
        collector.defaultFormat(box(message = "XLINE1\nXLINE2", loggerName = "MyLogger"))

        // Both lines should share the same prefix
        val prefix1 = lines[0].substringBefore("XLINE1")
        val prefix2 = lines[1].substringBefore("XLINE2")
        assertEquals(prefix1, prefix2)
        assertTrue(prefix1.contains("MyLogger"))
    }

    @Test
    fun defaultFormat_nullThreadName() = runTest {
        val lines = mutableListOf<String>()
        val collector = FlowCollector<String> { lines.add(it) }
        collector.defaultFormat(box(threadName = null, message = "msg"))

        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("null"))
        assertTrue(lines[0].contains("msg"))
    }

    @Test
    fun route_collectsFormattedOutput() = runTest {
        val deliveries = flowOf(box(message = "hello"))
        val output = mutableListOf<String>()
        val display: Display = FlowCollector { output.add(it) }

        deliveries.route(display)

        assertEquals(1, output.size)
        assertTrue(output[0].contains("hello"))
    }

    @Test
    fun route_customFormatter() = runTest {
        val deliveries = flowOf(box(message = "hello"))
        val output = mutableListOf<String>()
        val display: Display = FlowCollector { output.add(it) }
        val formatter: Formatter = { box -> emit("[${box.level}] ${box.message}") }

        deliveries.route(display, formatter)

        assertEquals(1, output.size)
        assertEquals("[INFO] hello", output[0])
    }

    @Test
    fun route_multipleBoxes() = runTest {
        val deliveries = flowOf(
            box(message = "first"),
            box(message = "second"),
        )
        val output = mutableListOf<String>()
        val display: Display = FlowCollector { output.add(it) }

        deliveries.route(display)

        assertEquals(2, output.size)
        assertTrue(output[0].contains("first"))
        assertTrue(output[1].contains("second"))
    }
}
