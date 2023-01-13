package com.monkopedia.hauler.benchmark

import com.monkopedia.hauler.Box
import com.monkopedia.hauler.DefaultFormat
import com.monkopedia.hauler.Garage
import com.monkopedia.hauler.Level
import com.monkopedia.hauler.Warehouse
import com.monkopedia.hauler.attach
import com.monkopedia.hauler.benchmark.ConnectionType.DEFAULT
import com.monkopedia.hauler.deliveries
import com.monkopedia.hauler.dumpDeliveries
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@OptIn(ExperimentalTime::class)
abstract class HighVolumeTest(private val platform: String) {

    abstract val harness: HarnessProtocol

    @Test
    fun testExecute() = runBlocking {
        val warehouse = Warehouse()
        val warehouseJob = launchAttach(warehouse)
        val stop = Box(Level.ERROR, "DONE", "DONE", 0, null)
        val messageAsync = async {
            warehouse.deliveries().deliveries(this).takeWhile { it != stop }
                .toCollection(mutableListOf())
        }
        harness.setShipper(warehouse)

        val time = measureTime {
            harness.execTasks(
                ExecSpec(
                    "Big test",
                    ConnectionSpec(DEFAULT, null, ShippingType.DEFAULT, null),
                    List(100) {
                        TaskSpec("Tag$it", "Thread", it.toLong(), 10, 100)
                    }
                )
            )
            Garage.flushLogs()
        }
        warehouse.requestPickup().also {
            it.log(stop)
        }.close()
        Garage.rootHauler.emit(stop)
        val messages = messageAsync.await()
        println("Took $time to produce ${messages.size} messages")
        warehouseJob.cancelAndJoin()
        assertEquals(10200, messages.size)
    }

    @Test
    fun testBulkExecute() = runBlocking {
        val warehouse = Warehouse()
        val warehouseJob = launchAttach(warehouse)
        val stop = Box(Level.ERROR, "DONE", "DONE", 0, null)
        val messageAsync = async {
            warehouse.deliveries().deliveries(this).takeWhile { it != stop }
                .toCollection(mutableListOf())
        }
        harness.setShipper(warehouse)

        val time = measureTime {
            harness.execTasks(
                ExecSpec(
                    "Big test",
                    ConnectionSpec(
                        DEFAULT,
                        null,
                        ShippingType.PACKED,
                        SerializableDeliveryRates(15, 1000, 500)
                    ),
                    List(100) {
                        TaskSpec("Tag$it", "Thread", it.toLong(), 10, 100)
                    }
                )
            )
        }
        warehouse.requestPickup().also {
            it.log(stop)
        }.close()
        Garage.rootHauler.emit(stop)
        val messages = messageAsync.await()
        println("Took $time to produce ${messages.size} packed messages")
        warehouseJob.cancelAndJoin()
        assertEquals(10200, messages.size)
    }

    protected open suspend fun CoroutineScope.launchAttach(warehouse: Warehouse) =
        warehouse.requestPickup().attach(this)
}

@RunWith(JUnit4::class)
class HighVolumeNodeTest : HighVolumeTest("Node") {
    @get:Rule
    val harnessRule = NodeHarnessRule()

    override val harness: HarnessProtocol
        get() = harnessRule.harness
}

@RunWith(JUnit4::class)
class HighVolumeNativeTest : HighVolumeTest("Native") {
    @get:Rule
    val harnessRule = NativeHarnessRule()

    override val harness: HarnessProtocol
        get() = harnessRule.harness
}

@RunWith(JUnit4::class)
class HighVolumeJvmTest : HighVolumeTest("JVM") {
    @get:Rule
    val harnessRule = JvmHarnessRule()

    override val harness: HarnessProtocol
        get() = harnessRule.harness

    override suspend fun CoroutineScope.launchAttach(warehouse: Warehouse): Job {
        return launch { }
    }
}
