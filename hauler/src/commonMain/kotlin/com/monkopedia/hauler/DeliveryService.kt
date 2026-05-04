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
package com.monkopedia.hauler

import com.monkopedia.ksrpc.RpcBidiService
import com.monkopedia.ksrpc.RpcHostService
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import kotlinx.coroutines.flow.Flow

/** A flow of individual log [Box]es. */
typealias Deliveries = Flow<Box>

/**
 * Polling-only subset of [DeliveryService]: pull batches via [recurringCustomerPickup] /
 * [dumpCustomerPickup], or filter the stream via [weighIn]. Hostable on simple transports
 * (HTTP, JSON-RPC) — does not require bidirectional capability.
 */
@KsService
interface BasicDeliveryService : RpcHostService {
    @KsMethod("/register_poll")
    suspend fun recurringCustomerPickup(u: Unit = Unit): CustomerPickup

    @KsMethod("/dump_poll")
    suspend fun dumpCustomerPickup(u: Unit = Unit): CustomerPickup

    @KsMethod("/filter")
    suspend fun weighIn(filter: WeighStation): BasicDeliveryService
}

/**
 * Full [BasicDeliveryService] plus callback-based subscriptions via [registerDelivery] /
 * [registerDeliveryDay] / [dumpDelivery] / [dumpDeliveryDay]. [weighIn] is narrowed to return a
 * full [DeliveryService] so chained filtering keeps the bidi capabilities.
 */
@KsService
interface DeliveryService :
    BasicDeliveryService,
    RpcBidiService {
    @KsMethod("/register")
    suspend fun registerDelivery(receiver: AutomaticDelivery): Registration

    @KsMethod("/register_bulk")
    suspend fun registerDeliveryDay(receiver: DeliveryDay): Registration

    @KsMethod("/dump")
    suspend fun dumpDelivery(receiver: AutomaticDelivery)

    @KsMethod("/dump_bulk")
    suspend fun dumpDeliveryDay(receiver: DeliveryDay)

    @KsMethod("/filter")
    override suspend fun weighIn(filter: WeighStation): DeliveryService
}

/** Handle for an active delivery subscription. Use [ping] to check liveness and [unregister] to stop. */
@KsService
interface Registration : RpcService {
    @KsMethod("/ping")
    suspend fun ping(u: Unit = Unit)

    @KsMethod("/unregister")
    suspend fun unregister(u: Unit = Unit)
}

/** Callback interface for receiving individual log [Box]es as they arrive. */
@KsService
interface AutomaticDelivery : RpcService {
    @KsMethod("/logs")
    suspend fun onLogEvent(event: Box)
}

/** Callback interface for receiving batched log [Palette]s. */
@KsService
interface DeliveryDay : RpcService {
    @KsMethod("/logs")
    suspend fun onLogs(event: Palette)
}

/** Polling interface for pulling log batches on demand. */
@KsService
interface CustomerPickup : RpcService {
    @KsMethod("/get")
    suspend fun get(maxEntries: Int = 100): Palette
}
