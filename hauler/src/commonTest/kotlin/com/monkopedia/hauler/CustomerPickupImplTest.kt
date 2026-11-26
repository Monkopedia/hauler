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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CustomerPickupImplTest {
    private fun box(
        level: Level = Level.INFO,
        loggerName: String = "com.example.Test",
        message: String = "test message",
        timestamp: Long = 1000L,
        threadName: String? = "main",
    ) = Box(level, loggerName, message, timestamp, threadName)

    /** Poll get() accumulating results until expected count, yielding to let external scopes run. */
    private suspend fun pollBoxes(
        pickup: CustomerPickup,
        expected: Int,
    ): List<Box> {
        val all = mutableListOf<Box>()
        withTimeout(5.seconds) {
            while (all.size < expected) {
                all.addAll(pickup.get().unpack())
                if (all.size < expected) delay(1)
            }
        }
        return all
    }

    @Test
    fun get_returnsCollectedBoxes() =
        runTest {
            val flow = MutableSharedFlow<Box>(replay = 100)
            val pickup = CustomerPickupImpl(flow, DeliveryRates(onDeliveryError = {}), this)

            flow.emit(box(message = "a"))
            flow.emit(box(message = "b"))

            val boxes = pollBoxes(pickup, 2)
            assertEquals(2, boxes.size)
            assertEquals("a", boxes[0].message)
            assertEquals("b", boxes[1].message)
            pickup.close()
        }

    @Test
    fun get_clearsBufferAfterRead() =
        runTest {
            val flow = MutableSharedFlow<Box>(replay = 100)
            val pickup = CustomerPickupImpl(flow, DeliveryRates(onDeliveryError = {}), this)

            flow.emit(box(message = "first"))
            val first = pollBoxes(pickup, 1)
            assertEquals(1, first.size)

            // Buffer was cleared, second get should be empty
            val palette2 = pickup.get()
            assertEquals(0, palette2.unpack().size)
            pickup.close()
        }

    @Test
    fun get_respectsCircularBufferSize() =
        runTest {
            val rates = DeliveryRates(defaultBoxRetention = 3, defaultPaletteSize = 3, onDeliveryError = {})
            val flow = MutableSharedFlow<Box>(replay = 100)
            val pickup = CustomerPickupImpl(flow, rates, this)

            for (i in 1..5) {
                flow.emit(box(message = "msg$i"))
            }

            val boxes = pollBoxes(pickup, 3)
            // CircularBuffer(3) keeps last 3 items
            assertEquals(3, boxes.size)
            assertEquals("msg3", boxes[0].message)
            assertEquals("msg4", boxes[1].message)
            assertEquals("msg5", boxes[2].message)
            pickup.close()
        }

    @Test
    fun get_emptyWhenNothingEmitted() =
        runTest {
            val flow = MutableSharedFlow<Box>(replay = 100)
            val pickup = CustomerPickupImpl(flow, DeliveryRates(onDeliveryError = {}), this)

            val palette = pickup.get()
            assertEquals(0, palette.unpack().size)
            pickup.close()
        }

    @Test
    fun get_multiplePollingCycles() =
        runTest {
            val flow = MutableSharedFlow<Box>(replay = 100)
            val pickup = CustomerPickupImpl(flow, DeliveryRates(onDeliveryError = {}), this)

            flow.tryEmit(box(message = "batch1"))
            val p1 = pollBoxes(pickup, 1)
            assertEquals(1, p1.size)

            flow.tryEmit(box(message = "batch2a"))
            flow.tryEmit(box(message = "batch2b"))
            val p2 = pollBoxes(pickup, 2)
            assertEquals(2, p2.size)

            pickup.close()
        }

    @Test
    fun get_respectsMaxEntries() =
        runTest {
            val flow = MutableSharedFlow<Box>(replay = 100)
            val pickup = CustomerPickupImpl(flow, DeliveryRates(defaultBoxRetention = 100, onDeliveryError = {}), this)

            for (i in 1..5) {
                flow.emit(box(message = "msg$i"))
            }
            // Wait for all 5 to buffer
            pollBoxes(pickup, 5).also { assertEquals(5, it.size) }

            // Emit 5 more and wait for them to buffer
            for (i in 6..10) {
                flow.emit(box(message = "msg$i"))
            }
            // Poll with maxEntries=3 — should return at most 3
            withTimeout(5.seconds) {
                while (true) {
                    val batch = pickup.get(maxEntries = 3).unpack()
                    if (batch.isNotEmpty()) {
                        assertEquals(3, batch.size)
                        assertEquals("msg6", batch[0].message)
                        assertEquals("msg7", batch[1].message)
                        assertEquals("msg8", batch[2].message)
                        // Remaining 2 should still be available
                        val rest = pollBoxes(pickup, 2)
                        assertEquals(2, rest.size)
                        assertEquals("msg9", rest[0].message)
                        assertEquals("msg10", rest[1].message)
                        break
                    }
                    delay(1)
                }
            }
            pickup.close()
        }

    @Test
    fun close_stopsCollection() =
        runTest {
            val flow = MutableSharedFlow<Box>(replay = 100)
            val pickup = CustomerPickupImpl(flow, DeliveryRates(onDeliveryError = {}), this)

            flow.emit(box(message = "before"))
            pollBoxes(pickup, 1)
            pickup.close()
            // Verifies close() doesn't throw
        }

    /**
     * Subscribe a pickup under [rates], emit [count] LIVE boxes before any poll, and return what a
     * single get() hands back. Virtual time: the collector buffers every box before get() runs.
     */
    private suspend fun TestScope.liveBurst(
        rates: DeliveryRates,
        count: Int,
    ): List<Box> {
        // Built as Warehouse builds its own source.
        val flow = MutableSharedFlow<Box>(replay = rates.defaultBoxRetention)
        val pickup = CustomerPickupImpl(flow, rates, this)
        runCurrent()
        for (i in 0 until count) {
            flow.emit(box(message = "live$i"))
        }
        runCurrent()
        val boxes = pickup.get(maxEntries = count).unpack()
        pickup.close()
        return boxes
    }

    // #69 review: "live only, no history" (retention 0) is a valid Warehouse config; a pickup
    // under it must still deliver live boxes rather than throw from a zero-capacity buffer.
    @Test
    fun get_deliversLiveBoxes_whenRetentionIsZero() =
        runTest {
            val boxes = liveBurst(DeliveryRates(defaultBoxRetention = 0, onDeliveryError = {}), 10)
            assertEquals(10, boxes.size, "live boxes returned of 10 emitted")
            assertEquals((0 until 10).map { "live$it" }, boxes.map { it.message })
        }

    // #69 review: lowering retention below the palette size must not shrink how many live boxes a
    // pickup holds between polls; the buffer is bounded by the larger of the two settings.
    @Test
    fun get_keepsLiveBurst_whenRetentionIsBelowPaletteSize() =
        runTest {
            val rates = DeliveryRates(defaultBoxRetention = 100, defaultPaletteSize = 500, onDeliveryError = {})
            val boxes = liveBurst(rates, 300)
            assertEquals(300, boxes.size, "live boxes returned of 300 emitted")
            assertEquals((0 until 300).map { "live$it" }, boxes.map { it.message })
        }

    /** A pickup holding [count] buffered boxes. Virtual time: every box is buffered on return. */
    private fun TestScope.bufferedPickup(count: Int): CustomerPickup {
        val flow = MutableSharedFlow<Box>(replay = count)
        val pickup = CustomerPickupImpl(flow, DeliveryRates(onDeliveryError = {}), this)
        runCurrent()
        repeat(count) { flow.tryEmit(box(message = "box$it")) }
        runCurrent()
        return pickup
    }

    // #68: maxEntries is an RPC parameter. A negative value is rejected, and the rejection must
    // not cost the caller (or anyone else polling) the boxes the pickup was holding.
    @Test
    fun get_negativeMaxEntries_isRejected_andKeepsTheBuffer() =
        runTest {
            for (bad in listOf(-1, Int.MIN_VALUE)) {
                val pickup = bufferedPickup(50)
                val error = assertFailsWith<IllegalArgumentException> { pickup.get(maxEntries = bad) }
                val kept = pickup.get(maxEntries = 100).unpack()
                assertEquals(
                    (0 until 50).map { "box$it" },
                    kept.map { it.message },
                    "boxes still buffered after get($bad) was rejected",
                )
                assertTrue(
                    error.message.orEmpty().contains("maxEntries must be >= 0, was $bad"),
                    "get($bad) rejected by the maxEntries guard, got: ${error.message}",
                )
                pickup.close()
            }
        }

    @Test
    fun get_negativeMaxEntries_isRejected_onAnEmptyPickup() =
        runTest {
            val pickup = bufferedPickup(0)
            val error = assertFailsWith<IllegalArgumentException> { pickup.get(maxEntries = -1) }
            assertEquals("maxEntries must be >= 0, was -1", error.message)
            pickup.close()
        }

    // Zero is valid: it returns nothing and leaves every buffered box for the next poll.
    @Test
    fun get_zeroMaxEntries_returnsNothing_andKeepsTheBuffer() =
        runTest {
            val pickup = bufferedPickup(50)
            assertEquals(0, pickup.get(maxEntries = 0).unpack().size, "boxes returned by get(0)")
            assertEquals((0 until 50).map { "box$it" }, pickup.get(maxEntries = 100).unpack().map { it.message })
            pickup.close()
        }
}
