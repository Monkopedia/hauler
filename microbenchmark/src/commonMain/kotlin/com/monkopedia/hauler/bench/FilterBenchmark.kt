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

import com.monkopedia.hauler.AndFilter
import com.monkopedia.hauler.Box
import com.monkopedia.hauler.Level
import com.monkopedia.hauler.LevelFilter
import com.monkopedia.hauler.LevelMatchMode
import com.monkopedia.hauler.LoggerMatchMode
import com.monkopedia.hauler.LoggerNameFilter
import com.monkopedia.hauler.MessageFilter
import com.monkopedia.hauler.OrFilter
import com.monkopedia.hauler.WeighStation
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
class FilterBenchmark {

    @Param("level", "composed", "regex")
    var filterType: String = "level"

    private lateinit var filter: WeighStation
    private lateinit var box: Box

    @Volatile
    private var sink: Boolean = false

    @Setup
    fun setup() {
        box = Box(
            level = Level.INFO,
            loggerName = "com.example.MyService",
            message = "Processing request id=12345 from user=admin",
            timestamp = 1700000000000L,
            threadName = "worker-3",
        )
        filter = when (filterType) {
            "level" -> LevelFilter(LevelMatchMode.GT, Level.DEBUG)
            "composed" -> AndFilter(
                LevelFilter(LevelMatchMode.GT, Level.DEBUG),
                OrFilter(
                    LoggerNameFilter(LoggerMatchMode.PREFIX, "com.example"),
                    LoggerNameFilter(LoggerMatchMode.EXACT, "com.other.Service"),
                ),
            )
            "regex" -> MessageFilter(LoggerMatchMode.REGEX, ".*id=\\d+.*")
            else -> error("Unknown filter type: $filterType")
        }
    }

    @Benchmark
    fun evaluateFilter() {
        sink = box in filter
    }
}
