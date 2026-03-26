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

class CircularBufferTest {

    @Test
    fun emptyBuffer_returnsEmptyList() {
        val buffer = CircularBuffer<Int>(5)
        assertEquals(emptyList(), buffer.toListAndClear())
    }

    @Test
    fun partialFill_returnsItemsInOrder() {
        val buffer = CircularBuffer<Int>(5)
        buffer.add(1)
        buffer.add(2)
        buffer.add(3)
        assertEquals(listOf(1, 2, 3), buffer.toListAndClear())
    }

    @Test
    fun exactFill_returnsAllItems() {
        val buffer = CircularBuffer<Int>(3)
        buffer.add(1)
        buffer.add(2)
        buffer.add(3)
        assertEquals(listOf(1, 2, 3), buffer.toListAndClear())
    }

    @Test
    fun wraparound_preservesInsertionOrder() {
        val buffer = CircularBuffer<Int>(3)
        buffer.add(1)
        buffer.add(2)
        buffer.add(3)
        buffer.add(4) // overwrites 1
        assertEquals(listOf(2, 3, 4), buffer.toListAndClear())
    }

    @Test
    fun multipleWraparounds_preservesOrder() {
        val buffer = CircularBuffer<Int>(3)
        for (i in 1..10) {
            buffer.add(i)
        }
        // Last 3 items: 8, 9, 10
        assertEquals(listOf(8, 9, 10), buffer.toListAndClear())
    }

    @Test
    fun clearResetsState() {
        val buffer = CircularBuffer<Int>(3)
        buffer.add(1)
        buffer.add(2)
        buffer.toListAndClear()
        assertEquals(emptyList(), buffer.toListAndClear())
    }

    @Test
    fun reuseAfterClear() {
        val buffer = CircularBuffer<Int>(3)
        buffer.add(1)
        buffer.add(2)
        buffer.add(3)
        assertEquals(listOf(1, 2, 3), buffer.toListAndClear())

        buffer.add(4)
        buffer.add(5)
        assertEquals(listOf(4, 5), buffer.toListAndClear())
    }

    @Test
    fun reuseAfterClearWithWraparound() {
        val buffer = CircularBuffer<Int>(3)
        buffer.add(1)
        buffer.add(2)
        buffer.add(3)
        buffer.add(4) // wraparound
        assertEquals(listOf(2, 3, 4), buffer.toListAndClear())

        buffer.add(10)
        buffer.add(20)
        buffer.add(30)
        buffer.add(40) // wraparound again
        assertEquals(listOf(20, 30, 40), buffer.toListAndClear())
    }

    @Test
    fun singleItemBuffer() {
        val buffer = CircularBuffer<Int>(1)
        buffer.add(1)
        assertEquals(listOf(1), buffer.toListAndClear())

        buffer.add(2)
        buffer.add(3) // overwrites 2
        assertEquals(listOf(3), buffer.toListAndClear())
    }

    @Test
    fun wraparoundAtExactBoundary() {
        val buffer = CircularBuffer<Int>(3)
        // Fill exactly, then add exactly capacity more
        buffer.add(1)
        buffer.add(2)
        buffer.add(3)
        buffer.add(4)
        buffer.add(5)
        buffer.add(6) // nextIndex wraps back to 0
        assertEquals(listOf(4, 5, 6), buffer.toListAndClear())
    }

    @Test
    fun stringBuffer() {
        val buffer = CircularBuffer<String>(2)
        buffer.add("hello")
        buffer.add("world")
        buffer.add("!")
        assertEquals(listOf("world", "!"), buffer.toListAndClear())
    }
}
