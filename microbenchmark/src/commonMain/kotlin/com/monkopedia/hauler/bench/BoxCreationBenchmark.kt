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
class BoxCreationBenchmark {

    @Param("simple", "multiline", "with_exception")
    var messageType: String = "simple"

    private var message: String = ""
    private var throwable: Throwable? = null

    @Volatile
    private var sink: Any? = null

    @Setup
    fun setup() {
        when (messageType) {
            "simple" -> {
                message = "Application started successfully"
                throwable = null
            }
            "multiline" -> {
                message = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5"
                throwable = null
            }
            "with_exception" -> {
                message = "Something went wrong"
                throwable = RuntimeException("Test exception")
            }
        }
    }

    @Benchmark
    fun convertToBox() {
        val combined = if (throwable == null) message else "$message\n${throwable!!.stackTraceToString()}"
        sink = Box(Level.INFO, "Hauler", combined, kotlin.time.Clock.System.now().toEpochMilliseconds(), "main")
    }

    @Benchmark
    fun combineMessage() {
        sink = if (throwable == null) message else "$message\n${throwable!!.stackTraceToString()}"
    }
}
