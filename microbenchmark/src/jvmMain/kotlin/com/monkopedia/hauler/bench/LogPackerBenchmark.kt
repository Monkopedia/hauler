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
import com.monkopedia.hauler.LogPacker
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.runBlocking
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlin.concurrent.Volatile

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
class LogPackerBenchmark {

    @Param("10", "500")
    var batchSize: Int = 10

    private lateinit var boxes: List<Box>

    @Volatile
    private var sink: Any? = null

    @Setup
    fun setup() {
        val levels = Level.entries
        boxes = (0 until batchSize).map { i ->
            Box(
                level = levels[i % levels.size],
                loggerName = "com.example.Logger${i % 5}",
                message = "Log message number $i",
                timestamp = 1700000000000L + i,
                threadName = "thread-${i % 4}",
            )
        }
    }

    @Benchmark
    fun logAndDump() = runBlocking {
        val packer = LogPacker()
        for (box in boxes) {
            packer.log(box)
        }
        sink = packer.dumpLogs()
    }
}
