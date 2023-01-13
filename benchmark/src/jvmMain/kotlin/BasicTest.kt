package com.monkopedia.hauler.benchmark

import com.monkopedia.hauler.DefaultFormat
import com.monkopedia.hauler.Garage
import com.monkopedia.hauler.Warehouse
import com.monkopedia.hauler.attach
import com.monkopedia.hauler.benchmark.ConnectionType.DEFAULT
import com.monkopedia.hauler.dumpDeliveries
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

abstract class BasicTest {

    abstract val harness: HarnessProtocol

    @Test
    fun testExecute() = runBlocking {
        val warehouse = Warehouse()
        val warehouseJob = launchAttach(warehouse)
        harness.setShipper(warehouse)

        harness.execTasks(
            ExecSpec(
                "First test",
                ConnectionSpec(DEFAULT, null, ShippingType.DEFAULT, null),
                listOf(TaskSpec("Tag1", "MyThread", 0, 0, 1))
            )
        )
        Garage.flushLogs()
        val messages = warehouse.deliveries().dumpDeliveries().toCollection(mutableListOf())
        warehouseJob.cancelAndJoin()
        assertEquals(3, messages.size)
    }

    @Test
    fun testBulkExecute() = runBlocking {
        val warehouse = Warehouse()
        val warehouseJob = launchAttach(warehouse)
        harness.setShipper(warehouse)

        harness.execTasks(
            ExecSpec(
                "First test",
                ConnectionSpec(
                    DEFAULT,
                    null,
                    ShippingType.PACKED,
                    SerializableDeliveryRates(15, 5, 1)
                ),
                listOf(TaskSpec("Tag1", "MyThread", 100, 0, 10))
            )
        )
        Garage.flushLogs()
        val messages = warehouse.deliveries().dumpDeliveries().toCollection(mutableListOf())
        warehouseJob.cancelAndJoin()
        println("\n\n\nStart")
        messages.forEach {
            DefaultFormat.invoke(
                FlowCollector {
                    println(it)
                },
                it
            )
        }
        assertEquals(12, messages.size)
    }

    protected open suspend fun CoroutineScope.launchAttach(warehouse: Warehouse) =
        warehouse.requestPickup().attach(this)
}

@RunWith(JUnit4::class)
class BasicNodeTest : BasicTest() {
    @get:Rule
    val harnessRule = NodeHarnessRule()

    override val harness: HarnessProtocol
        get() = harnessRule.harness
}

@RunWith(JUnit4::class)
class BasicNativeTest : BasicTest() {
    @get:Rule
    val harnessRule = NativeHarnessRule()

    override val harness: HarnessProtocol
        get() = harnessRule.harness
}

@RunWith(JUnit4::class)
class BasicJvmTest : BasicTest() {
    @get:Rule
    val harnessRule = JvmHarnessRule()

    override val harness: HarnessProtocol
        get() = harnessRule.harness

    override suspend fun CoroutineScope.launchAttach(warehouse: Warehouse): Job {
        return launch { }
    }
}
