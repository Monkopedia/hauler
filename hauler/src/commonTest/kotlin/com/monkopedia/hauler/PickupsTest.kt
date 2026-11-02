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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
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

    // --- Subscribed by the time forwardTo returns (#65) ---
    //
    // Against a hot replay = 0 source, the shape of Garage.deliveries: the replay =
    // 100 flows above hide the bug by replaying to a late subscriber.

    @Test
    fun forwardToDropBox_isSubscribedWhenItReturns() =
        runTest {
            withContext(Dispatchers.Default) {
                val lost =
                    countBoxesLostRightAfterAttach { source, scope, sink ->
                        source.forwardTo(
                            object : DropBox {
                                override suspend fun log(logEvent: Box) = sink.send(logEvent.message)
                            },
                            scope,
                        )
                    }
                assertEquals(0, lost, "boxes lost right after forwardTo(DropBox) returned: $lost of $ATTACHES")
            }
        }

    @Test
    fun forwardToLoadingDock_isSubscribedWhenItReturns() =
        runTest {
            withContext(Dispatchers.Default) {
                val rates = DeliveryRates(defaultPaletteSize = 2, defaultPaletteInterval = 1.milliseconds, onDeliveryError = {})
                val lost =
                    countBoxesLostRightAfterAttach { source, scope, sink ->
                        source.forwardTo(
                            object : LoadingDock {
                                override suspend fun bulkLog(logs: Palette) = logs.unpack().forEach { sink.send(it.message) }
                            },
                            scope,
                            rates,
                        )
                    }
                assertEquals(0, lost, "boxes lost right after forwardTo(LoadingDock) returned: $lost of $ATTACHES")
            }
        }

    /**
     * Attaches [ATTACHES] times, each to a fresh hot source, and emits one box the
     * moment [attach] returns. Both happen on [Dispatchers.Unconfined], which makes
     * the window deterministic on every target: the old DropBox overload's
     * `launched.complete()` resumes the caller inline, on the collector's stack,
     * BEFORE it reaches `collect`, and the old LoadingDock overload's dispatched
     * launch has not run at all. A SENTINEL then goes out once the source shows a
     * subscriber, so it always arrives; the first thing the sink sees is either
     * the box (delivered) or the sentinel (the box was dropped before anyone
     * subscribed). That bounds every iteration without a per-loss timeout.
     */
    private suspend fun countBoxesLostRightAfterAttach(
        attach: suspend (source: MutableSharedFlow<Box>, scope: CoroutineScope, sink: Channel<String>) -> Job,
    ): Int {
        val scope = CoroutineScope(SupervisorJob())
        var lost = 0
        repeat(ATTACHES) {
            val source = MutableSharedFlow<Box>(extraBufferCapacity = 64)
            val sink = Channel<String>(Channel.UNLIMITED)
            val job =
                withContext(Dispatchers.Unconfined) {
                    attach(source, scope, sink).also { source.emit(box(message = "box")) }
                }
            awaitCondition("a subscriber on the source") { source.subscriptionCount.value > 0 }
            source.emit(box(message = "sentinel"))
            if (withTimeout(5.seconds) { sink.receive() } == "sentinel") lost++
            job.cancelAndJoin()
        }
        scope.cancel()
        return lost
    }

    private companion object {
        // Without the fix, both arms lose (nearly) every box on every target, so
        // a handful of attaches is enough; it is not a sampling count.
        const val ATTACHES = 20
    }
}
