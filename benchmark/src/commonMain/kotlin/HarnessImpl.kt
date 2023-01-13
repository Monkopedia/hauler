package com.monkopedia.hauler.benchmark

import com.monkopedia.hauler.CallSign
import com.monkopedia.hauler.Garage
import com.monkopedia.hauler.Shipper
import com.monkopedia.hauler.attach
import com.monkopedia.hauler.benchmark.ConnectionType.DEFAULT
import com.monkopedia.hauler.benchmark.ConnectionType.HTTPS
import com.monkopedia.hauler.benchmark.ConnectionType.WSS
import com.monkopedia.hauler.benchmark.ShippingType.PACKED
import com.monkopedia.hauler.error
import com.monkopedia.hauler.hauler
import com.monkopedia.hauler.info
import com.monkopedia.hauler.warn
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.asConnection
import com.monkopedia.ksrpc.toStub
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock.System
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

open class HarnessImpl(private val platform: String) : HarnessProtocol {
    private var lastShipper: Shipper? = null

    override suspend fun getPlatform(controller: String): String {
        return platform
    }

    override suspend fun setShipper(shipper: Shipper) {
        lastShipper = shipper
    }

    override suspend fun execTasks(exec: ExecSpec) {
        coroutineScope {
            val shipper = createShipper(exec.connection, lastShipper)
            val job = attachShipper(exec.connection, shipper)
            delay(1)
            try {
                exec.taskSpec.map {
                    async {
                        executeTask(it)
                    }
                }.awaitAll()
                Garage.flushLogs()
                delay((exec.connection.deliveryRates?.defaultPaletteInterval ?: 0) + 10)
                Garage.flushLogs()
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    open override suspend fun finish(u: Unit) {
        exit(0)
    }
}

expect fun exit(code: Int)

expect fun createPlatformShipper(exec: ConnectionSpec, lastShipper: Shipper?): Shipper?

suspend fun createShipper(exec: ConnectionSpec, lastShipper: Shipper?): Shipper {
    createPlatformShipper(exec, lastShipper)?.let { return it }
    return when (exec.type) {
        DEFAULT -> lastShipper ?: error("Expected shipper to be set through harness")
        HTTPS -> HttpClient().asConnection(exec.endpoint!!, ksrpcEnvironment { }).defaultChannel()
            .toStub()
        WSS -> HttpClient {
            install(WebSockets)
        }.asConnection(exec.endpoint!!, ksrpcEnvironment { }).defaultChannel()
            .toStub()
        else -> error("Not sure how to handle $exec")
    }
}

private suspend fun CoroutineScope.attachShipper(
    connection: ConnectionSpec,
    shipper: Shipper
): Job {
    return when (connection.shippingType) {
        ShippingType.DEFAULT -> shipper.requestPickup().attach(this)
        PACKED -> shipper.requestDockPickup()
            .attach(this, connection.deliveryRates!!.asDeliveryRates)
    }
}

@OptIn(ExperimentalTime::class)
suspend fun executeTask(task: TaskSpec) {
    val hauler = hauler(task.loggerName)
    withContext(CallSign(task.threadName)) {
        val time = measureTime {
            hauler.error("Starting task with delay ${task.startDelay} ${task.count}")
            delay(task.startDelay)
            for (i in 0 until task.count) {
                hauler.info("Task (${i + 1}/${task.count}) has executed at ${System.now()}")
                delay(task.interval)
            }
        }
        hauler.warn("Ending task after $time", Throwable("Test throwable"))
    }
    // Settling time
    delay(100)
}
