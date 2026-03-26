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
package com.monkopedia.hauler.bench

import com.monkopedia.hauler.Box
import com.monkopedia.hauler.CircularBuffer
import com.monkopedia.hauler.Level
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlin.concurrent.Volatile

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
class CircularBufferBenchmark {

    @Param("100", "1000")
    var bufferSize: Int = 100

    @Param("1", "5")
    var fillRatio: Int = 1

    private lateinit var items: List<Box>

    @Volatile
    private var sink: Any? = null

    @Setup
    fun setup() {
        val count = bufferSize * fillRatio
        items = (0 until count).map { i ->
            Box(
                level = Level.INFO,
                loggerName = "com.example.Logger",
                message = "Message $i",
                timestamp = 1700000000000L + i,
                threadName = "main",
            )
        }
    }

    @Benchmark
    fun addAndClear() {
        val buffer = CircularBuffer<Box>(bufferSize)
        for (item in items) {
            buffer.add(item)
        }
        sink = buffer.toListAndClear()
    }
}
