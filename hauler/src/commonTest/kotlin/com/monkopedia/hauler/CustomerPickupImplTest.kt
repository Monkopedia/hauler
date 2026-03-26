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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
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
    private suspend fun pollBoxes(pickup: CustomerPickup, expected: Int): List<Box> {
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
    fun get_returnsCollectedBoxes() = runTest {
        val flow = MutableSharedFlow<Box>(replay = 100)
        val pickup = CustomerPickupImpl(flow, DeliveryRates())

        flow.emit(box(message = "a"))
        flow.emit(box(message = "b"))

        val boxes = pollBoxes(pickup, 2)
        assertEquals(2, boxes.size)
        assertEquals("a", boxes[0].message)
        assertEquals("b", boxes[1].message)
        pickup.close()
    }

    @Test
    fun get_clearsBufferAfterRead() = runTest {
        val flow = MutableSharedFlow<Box>(replay = 100)
        val pickup = CustomerPickupImpl(flow, DeliveryRates())

        flow.emit(box(message = "first"))
        val first = pollBoxes(pickup, 1)
        assertEquals(1, first.size)

        // Buffer was cleared, second get should be empty
        val palette2 = pickup.get()
        assertEquals(0, palette2.unpack().size)
        pickup.close()
    }

    @Test
    fun get_respectsCircularBufferSize() = runTest {
        val rates = DeliveryRates(defaultPaletteSize = 3)
        val flow = MutableSharedFlow<Box>(replay = 100)
        val pickup = CustomerPickupImpl(flow, rates)

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
    fun get_emptyWhenNothingEmitted() = runTest {
        val flow = MutableSharedFlow<Box>(replay = 100)
        val pickup = CustomerPickupImpl(flow, DeliveryRates())

        val palette = pickup.get()
        assertEquals(0, palette.unpack().size)
        pickup.close()
    }

    @Test
    fun get_multiplePollingCycles() = runTest {
        val flow = MutableSharedFlow<Box>(replay = 100)
        val pickup = CustomerPickupImpl(flow, DeliveryRates())

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
    fun close_stopsCollection() = runTest {
        val flow = MutableSharedFlow<Box>(replay = 100)
        val pickup = CustomerPickupImpl(flow, DeliveryRates())

        flow.emit(box(message = "before"))
        pollBoxes(pickup, 1)
        pickup.close()
        // Verifies close() doesn't throw
    }
}
