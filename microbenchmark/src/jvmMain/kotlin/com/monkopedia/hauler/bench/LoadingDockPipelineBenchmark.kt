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
import com.monkopedia.hauler.Level
import com.monkopedia.hauler.LoadingDock
import com.monkopedia.hauler.Palette
import com.monkopedia.hauler.Shipper
import com.monkopedia.hauler.Warehouse
import com.monkopedia.hauler.pack
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.sockets.asConnection
import com.monkopedia.ksrpc.toStub
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(BenchmarkTimeUnit.SECONDS)
class LoadingDockPipelineBenchmark {

    @Param("10", "100", "500")
    var batchSize: Int = 10

    private lateinit var palette: Palette
    private lateinit var rpcDock: LoadingDock
    private lateinit var directDock: com.monkopedia.hauler.LoadingDock
    private lateinit var scope: CoroutineScope
    private lateinit var conn1: Connection<String>
    private lateinit var conn2: Connection<String>
    private lateinit var warehouse: Warehouse

    @Setup
    fun setup() {
        val levels = Level.entries
        val boxes = (0 until batchSize).map { i ->
            Box(
                level = levels[i % levels.size],
                loggerName = "com.example.Logger${i % 5}",
                message = "Log message number $i",
                timestamp = 1700000000000L + i,
                threadName = "thread-${i % 4}",
            )
        }
        palette = boxes.pack()

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        warehouse = Warehouse(DeliveryRates(onDeliveryError = { it.printStackTrace() }))

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
            rpcDock = shipper.requestDockPickup()
            directDock = warehouse.requestDockPickup()
            ready.countDown()
        }
        ready.await()
    }

    @TearDown
    fun tearDown() {
        runBlocking(Dispatchers.IO) {
            runCatching { rpcDock.close() }
            runCatching { conn1.close() }
            runCatching { conn2.close() }
            runCatching { warehouse.close() }
        }
        scope.cancel()
    }

    @Benchmark
    fun sendViaRpc() = runBlocking(Dispatchers.IO) {
        rpcDock.bulkLog(palette)
    }

    @Benchmark
    fun sendDirect() = runBlocking {
        directDock.bulkLog(palette)
    }
}
