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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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

    // --- Deliveries.forwardTo(DropBox) ---

    @Test
    fun forwardToDropBox_forwardsEachBox() =
        runTest {
            // On a REAL dispatcher (#51/A4). The writers here run on
            // CoroutineScope(SupervisorJob()), i.e. Dispatchers.Default, while
            // awaitCondition polled runTest's VIRTUAL clock -- five virtual seconds
            // pass in a few thousand instantaneous delay(1) iterations, so the real
            // writer gets only incidental yield time. That is the exact failure #44
            // diagnosed and DeliveryServiceTest already guards against; this file was
            // the only one still polling a real writer on virtual time.
            withContext(Dispatchers.Default) {
                val flow = MutableSharedFlow<Box>(replay = 100)
                val scope = CoroutineScope(SupervisorJob())
                val received = mutableListOf<Box>()
                val dropBox =
                    object : DropBox {
                        override suspend fun log(logEvent: Box) {
                            received.add(logEvent)
                        }
                    }

                val job = flow.forwardTo(dropBox, scope)
                assertTrue(job.isActive)

                flow.emit(box(message = "a"))
                flow.emit(box(message = "b"))
                awaitCondition("both forwarded boxes") { received.size >= 2 }

                // cancelAndJoin, not cancel (#52). The writer is an `object : DropBox`
                // invoked from a coroutine the LIBRARY launched on a scope that resolves to
                // Dispatchers.Default -- so cancel() alone left it running and these reads
                // had no happens-before edge at all. My survey missed this family three
                // ways: the writer is not a collect lambda, the list is not read via a
                // name the axes matched, and the dispatcher is never named in the test.
                job.cancelAndJoin()

                assertEquals(2, received.size)
                assertEquals("a", received[0].message)
                assertEquals("b", received[1].message)
                scope.cancel()
            }
        }

    @Test
    fun forwardToDropBox_jobIsActiveAfterLaunch() =
        runTest {
            val flow = MutableSharedFlow<Box>(replay = 100)
            val scope = CoroutineScope(SupervisorJob())
            val dropBox =
                object : DropBox {
                    override suspend fun log(logEvent: Box) {}
                }
            val job = flow.forwardTo(dropBox, scope)
            assertTrue(job.isActive)
            job.cancel()
            scope.cancel()
        }

    // --- Deliveries.forwardTo(LoadingDock) ---

    @Test
    fun forwardToLoadingDock_forwardsPalettes() =
        runTest {
            // On a REAL dispatcher (#51/A4). The writers here run on
            // CoroutineScope(SupervisorJob()), i.e. Dispatchers.Default, while
            // awaitCondition polled runTest's VIRTUAL clock -- five virtual seconds
            // pass in a few thousand instantaneous delay(1) iterations, so the real
            // writer gets only incidental yield time. That is the exact failure #44
            // diagnosed and DeliveryServiceTest already guards against; this file was
            // the only one still polling a real writer on virtual time.
            withContext(Dispatchers.Default) {
                val flow = MutableSharedFlow<Box>(replay = 100)
                val scope = CoroutineScope(SupervisorJob())
                val received = mutableListOf<Palette>()
                val dock =
                    object : LoadingDock {
                        override suspend fun bulkLog(logs: Palette) {
                            received.add(logs)
                        }
                    }

                // Small palette size so size-based flush triggers. Long interval to avoid timer-based.
                val rates = DeliveryRates(defaultPaletteSize = 2, defaultPaletteInterval = 100.seconds, onDeliveryError = {})
                val job = flow.forwardTo(dock, scope, rates)
                assertTrue(job.isActive)

                flow.emit(box(message = "x"))
                flow.emit(box(message = "y"))
                awaitCondition("a flushed palette") { received.isNotEmpty() }
                // Join first: flatMap ITERATES `received` while the packer is still
                // appending, which is the ConcurrentModificationException shape, not just
                // a stale read.
                job.cancelAndJoin()

                val allBoxes = received.flatMap { it.unpack() }
                assertTrue(allBoxes.any { it.message == "x" })
                assertTrue(allBoxes.any { it.message == "y" })
                scope.cancel()
            }
        }

    @Test
    fun forwardToLoadingDock_respectsPaletteSize() =
        runTest {
            // On a REAL dispatcher (#51/A4). The writers here run on
            // CoroutineScope(SupervisorJob()), i.e. Dispatchers.Default, while
            // awaitCondition polled runTest's VIRTUAL clock -- five virtual seconds
            // pass in a few thousand instantaneous delay(1) iterations, so the real
            // writer gets only incidental yield time. That is the exact failure #44
            // diagnosed and DeliveryServiceTest already guards against; this file was
            // the only one still polling a real writer on virtual time.
            withContext(Dispatchers.Default) {
                val flow = MutableSharedFlow<Box>(replay = 100)
                val scope = CoroutineScope(SupervisorJob())
                val received = mutableListOf<Palette>()
                val dock =
                    object : LoadingDock {
                        override suspend fun bulkLog(logs: Palette) {
                            received.add(logs)
                        }
                    }

                // Palette size = 3, long interval so only size triggers flush
                val rates = DeliveryRates(defaultPaletteSize = 3, defaultPaletteInterval = 100.seconds, onDeliveryError = {})
                val job = flow.forwardTo(dock, scope, rates)

                flow.emit(box(message = "1"))
                flow.emit(box(message = "2"))
                flow.emit(box(message = "3"))
                awaitCondition("a flushed palette") { received.isNotEmpty() }
                job.cancelAndJoin()

                val allBoxes = received.flatMap { it.unpack() }
                assertEquals(3, allBoxes.size)
                scope.cancel()
            }
        }
}
