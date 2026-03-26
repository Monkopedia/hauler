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
import com.monkopedia.hauler.Level
import com.monkopedia.hauler.Palette
import com.monkopedia.hauler.pack
import com.monkopedia.hauler.unpack
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
class PackUnpackBenchmark {

    @Param("10", "500")
    var batchSize: Int = 10

    @Param("1", "20")
    var distinctLoggers: Int = 1

    private lateinit var boxes: List<Box>
    private lateinit var palette: Palette

    @Volatile
    private var sink: Any? = null

    @Setup
    fun setup() {
        val levels = Level.entries
        val loggerNames = (1..distinctLoggers).map { "com.example.Logger$it" }
        boxes = (0 until batchSize).map { i ->
            Box(
                level = levels[i % levels.size],
                loggerName = loggerNames[i % loggerNames.size],
                message = "Log message number $i",
                timestamp = 1700000000000L + i,
                threadName = "thread-${i % 4}",
            )
        }
        palette = boxes.pack()
    }

    @Benchmark
    fun pack() {
        sink = boxes.pack()
    }

    @Benchmark
    fun unpack() {
        sink = palette.unpack()
    }
}
