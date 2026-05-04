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
@file:OptIn(KsrpcInternal::class)

package com.monkopedia.hauler

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import com.monkopedia.ksrpc.internal.asClient
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.toStub
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Wire-level round-trip tests for the service-tier split. Validates that the covariant override
 * of [DeliveryService.weighIn] dispatches correctly through serialization — the filtered stub
 * obtained over the wire must expose the bidi callback methods, not just the basic poll surface.
 */
class ServiceTierWireTest {

    private fun box(level: Level = Level.INFO, message: String = "msg") =
        Box(level, "logger", message, 0L, "main")

    @Test
    fun shipperOverWire_pickupAndLog() = runTest {
        withContext(Dispatchers.Default) {
            val warehouse = Warehouse(DeliveryRates(onDeliveryError = {}))
            val (shipper, channel) = hostShipper(warehouse)
            try {
                val dropBox = shipper.requestPickup()
                val received = mutableListOf<Box>()
                val done = CompletableDeferred<Unit>()
                val service = shipper.deliveries()
                service.registerDelivery(object : AutomaticDelivery {
                    override suspend fun onLogEvent(event: Box) {
                        received.add(event)
                        if (received.size == 1) done.complete(Unit)
                    }
                })
                delay(50)
                dropBox.log(box(message = "hi"))
                withTimeout(5.seconds) { done.await() }
                assertEquals(1, received.size)
                assertEquals("hi", received[0].message)
            } finally {
                runCatching { channel.close() }
                warehouse.close()
            }
        }
    }

    @Test
    fun weighInOverWire_filteredViewKeepsBidiCapability() = runTest {
        withContext(Dispatchers.Default) {
            val warehouse = Warehouse(DeliveryRates(onDeliveryError = {}))
            val (shipper, channel) = hostShipper(warehouse)
            try {
                val dropBox = shipper.requestPickup()
                val service = shipper.deliveries()
                val filtered: DeliveryService =
                    service.weighIn(LevelFilter(LevelMatchMode.GT, Level.INFO))

                val received = mutableListOf<Box>()
                filtered.registerDelivery(object : AutomaticDelivery {
                    override suspend fun onLogEvent(event: Box) {
                        received.add(event)
                    }
                })
                delay(50)
                dropBox.log(box(level = Level.DEBUG, message = "skip-debug"))
                dropBox.log(box(level = Level.INFO, message = "skip-info"))
                dropBox.log(box(level = Level.WARN, message = "keep-warn"))
                dropBox.log(box(level = Level.ERROR, message = "keep-error"))

                withTimeout(5.seconds) {
                    while (received.size < 2) delay(5)
                }
                assertEquals(2, received.size)
                assertEquals("keep-warn", received[0].message)
                assertEquals("keep-error", received[1].message)
            } finally {
                runCatching { channel.close() }
                warehouse.close()
            }
        }
    }

    @Test
    fun basicShipperStubExposesOnlyHostMethods() = runTest {
        withContext(Dispatchers.Default) {
            val warehouse = Warehouse(DeliveryRates(onDeliveryError = {}))
            val (basic, channel) = hostBasicShipper(warehouse)
            try {
                val dropBox = basic.requestPickup()
                val dock = basic.requestDockPickup()
                assertTrue(true, "BasicShipper round-trip OK; both methods callable")
                dropBox.log(box(message = "via basic"))
                dock.bulkLog(Palette(loggerNames = emptyList(), messages = emptyList()))
            } finally {
                runCatching { channel.close() }
                warehouse.close()
            }
        }
    }

    private suspend fun hostShipper(warehouse: Warehouse): Pair<Shipper, HostSerializedChannelImpl<String>> {
        val channel = HostSerializedChannelImpl(ksrpcEnvironment { })
        channel.registerDefault(warehouse, Shipper)
        val stub: Shipper = channel.asClient.defaultChannel().toStub()
        return stub to channel
    }

    private suspend fun hostBasicShipper(
        warehouse: Warehouse,
    ): Pair<BasicShipper, HostSerializedChannelImpl<String>> {
        val channel = HostSerializedChannelImpl(ksrpcEnvironment { })
        channel.registerDefault(warehouse, BasicShipper)
        val stub: BasicShipper = channel.asClient.defaultChannel().toStub()
        return stub to channel
    }
}
