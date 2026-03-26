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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PickupsTest {

    private fun box(
        level: Level = Level.INFO,
        loggerName: String = "com.example.Test",
        message: String = "test message",
        timestamp: Long = 1000L,
        threadName: String? = "main",
    ) = Box(level, loggerName, message, timestamp, threadName)

    private suspend fun awaitCondition(check: () -> Boolean) {
        withTimeout(5.seconds) {
            while (!check()) delay(1)
        }
    }

    // --- DropBox.attach ---

    @Test
    fun dropBoxAttach_forwardsEachBox() = runTest {
        val flow = MutableSharedFlow<Box>(replay = 100)
        val scope = CoroutineScope(SupervisorJob())
        val received = mutableListOf<Box>()
        val dropBox = object : DropBox {
            override suspend fun log(logEvent: Box) {
                received.add(logEvent)
            }
        }

        val job = flow.attach(dropBox, scope)
        assertTrue(job.isActive)

        flow.emit(box(message = "a"))
        flow.emit(box(message = "b"))
        awaitCondition { received.size >= 2 }

        assertEquals(2, received.size)
        assertEquals("a", received[0].message)
        assertEquals("b", received[1].message)
        job.cancel()
        scope.cancel()
    }

    @Test
    fun dropBoxAttach_jobIsActiveAfterLaunch() = runTest {
        val flow = MutableSharedFlow<Box>(replay = 100)
        val scope = CoroutineScope(SupervisorJob())
        val dropBox = object : DropBox {
            override suspend fun log(logEvent: Box) {}
        }
        val job = flow.attach(dropBox, scope)
        assertTrue(job.isActive)
        job.cancel()
        scope.cancel()
    }

    // --- LoadingDock.attach ---

    @Test
    fun loadingDockAttach_forwardsPalettes() = runTest {
        val flow = MutableSharedFlow<Box>(replay = 100)
        val scope = CoroutineScope(SupervisorJob())
        val received = mutableListOf<Palette>()
        val dock = object : LoadingDock {
            override suspend fun bulkLog(logs: Palette) {
                received.add(logs)
            }
        }

        // Small palette size so size-based flush triggers. Long interval to avoid timer-based.
        val rates = DeliveryRates(defaultPaletteSize = 2, defaultPaletteInterval = 100.seconds)
        val job = flow.attach(dock, scope, rates)
        assertTrue(job.isActive)

        flow.emit(box(message = "x"))
        flow.emit(box(message = "y"))
        awaitCondition { received.isNotEmpty() }

        val allBoxes = received.flatMap { it.unpack() }
        assertTrue(allBoxes.any { it.message == "x" })
        assertTrue(allBoxes.any { it.message == "y" })
        job.cancel()
        scope.cancel()
    }

    @Test
    fun loadingDockAttach_respectsPaletteSize() = runTest {
        val flow = MutableSharedFlow<Box>(replay = 100)
        val scope = CoroutineScope(SupervisorJob())
        val received = mutableListOf<Palette>()
        val dock = object : LoadingDock {
            override suspend fun bulkLog(logs: Palette) {
                received.add(logs)
            }
        }

        // Palette size = 3, long interval so only size triggers flush
        val rates = DeliveryRates(defaultPaletteSize = 3, defaultPaletteInterval = 100.seconds)
        val job = flow.attach(dock, scope, rates)

        flow.emit(box(message = "1"))
        flow.emit(box(message = "2"))
        flow.emit(box(message = "3"))
        awaitCondition { received.isNotEmpty() }

        val allBoxes = received.flatMap { it.unpack() }
        assertEquals(3, allBoxes.size)
        job.cancel()
        scope.cancel()
    }
}
