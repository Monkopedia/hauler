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
import com.monkopedia.hauler.DeliveryRates
import com.monkopedia.hauler.DeliveryService
import com.monkopedia.hauler.Level
import com.monkopedia.hauler.Shipper
import com.monkopedia.hauler.Warehouse
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.sockets.asConnection
import com.monkopedia.ksrpc.toStub
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch

/**
 * End-to-end wire benchmark for the streaming delivery path. Setup is one [Warehouse] hosted as
 * [Shipper] over piped sockets; each invocation subscribes via the streaming API, pushes
 * [batchSize] boxes through a wire-side [DropBox], and waits for all to round-trip back to the
 * client collector.
 *
 * Measures both the per-subscription setup cost and the per-item delivery cost. With low
 * [batchSize] the setup dominates; with high [batchSize] the per-item cost dominates.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(BenchmarkTimeUnit.SECONDS)
class StreamingPipelineBenchmark {

    @Param("10", "100")
    var batchSize: Int = 10

    private lateinit var box: Box
    private lateinit var rpcDropBox: com.monkopedia.hauler.DropBox
    private lateinit var rpcService: DeliveryService
    private lateinit var scope: CoroutineScope
    private lateinit var conn1: Connection<String>
    private lateinit var conn2: Connection<String>
    private lateinit var warehouse: Warehouse

    @Setup
    fun setup() {
        box = Box(
            level = Level.INFO,
            loggerName = "com.example.Logger",
            message = "Log message",
            timestamp = 1700000000000L,
            threadName = "thread-0",
        )

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        warehouse = Warehouse(DeliveryRates())

        val o1 = PipedOutputStream()
        val i1 = PipedInputStream(o1)
        val o2 = PipedOutputStream()
        val i2 = PipedInputStream(o2)

        val ready = CountDownLatch(1)
        scope.launch {
            conn1 = (i2 to o1).asConnection(ksrpcEnvironment { })
            conn1.registerDefault(warehouse, Shipper)
        }
        scope.launch {
            conn2 = (i1 to o2).asConnection(ksrpcEnvironment { })
            val shipper: Shipper = conn2.defaultChannel().toStub()
            rpcDropBox = shipper.requestPickup()
            rpcService = shipper.deliveries()
            ready.countDown()
        }
        ready.await()
    }

    @TearDown
    fun tearDown() {
        runBlocking(Dispatchers.IO) {
            runCatching { rpcDropBox.close() }
            runCatching { rpcService.close() }
            runCatching { conn1.close() }
            runCatching { conn2.close() }
            runCatching { warehouse.close() }
        }
        scope.cancel()
    }

    /**
     * Subscribe -> push [batchSize] boxes -> collect them all -> tear subscription down.
     * On the new Flow API: streamDeliveries().take(batchSize).collect.
     * On the pre-Flow API: registerDelivery(callback) + unregister.
     */
    @Benchmark
    fun streamLiveBoxes() = runBlocking(Dispatchers.IO) {
        val received = AtomicInteger(0)
        val flowJob = launch {
            rpcService.streamDeliveries().take(batchSize).collect {
                received.incrementAndGet()
            }
        }
        // Allow the subscription round-trip to complete before producing.
        delay(5)
        repeat(batchSize) { rpcDropBox.log(box) }
        while (received.get() < batchSize) yield()
        flowJob.cancelAndJoin()
    }
}
