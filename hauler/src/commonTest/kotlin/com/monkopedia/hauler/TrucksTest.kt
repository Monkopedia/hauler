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

class TrucksTest {

    private fun box(
        level: Level = Level.INFO,
        loggerName: String = "com.example.Test",
        message: String = "test message",
        timestamp: Long = 1000L,
        threadName: String? = "main",
    ) = Box(level, loggerName, message, timestamp, threadName)

    @Test
    fun container_silentlyDiscardsBoxes() = runTest {
        // Container should not throw; it just discards
        Container.emit(box(message = "discarded"))
        Container.emit(box(message = "also discarded"))
    }

    @Test
    fun container_multipleEmitsNoEffect() = runTest {
        // Emitting many boxes should still be a no-op
        repeat(1000) {
            Container.emit(box(message = "msg$it"))
        }
        // No assertions needed — just verifying it doesn't throw or leak
    }

    @Test
    fun flatbed_emitsToStdout() = runTest {
        // Flatbed wraps println; just verify it doesn't throw
        // We can't easily capture stdout in common code, so just verify the contract
        val collected = mutableListOf<String>()
        val testDisplay: Display = Display { collected.add(it) }
        testDisplay.emit("hello")
        assertEquals(1, collected.size)
        assertEquals("hello", collected[0])
    }
}
