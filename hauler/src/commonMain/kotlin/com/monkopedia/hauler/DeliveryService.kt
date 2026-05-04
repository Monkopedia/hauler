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
 * Full [BasicDeliveryService] plus streaming subscriptions via [streamDeliveries] and friends.
 * Each stream method returns a fresh single-use [Flow]; cancel collection to stop the stream.
 * [weighIn] is narrowed to return a full [DeliveryService] so chained filtering keeps streams
 * available.
 */
@KsService
interface DeliveryService :
    BasicDeliveryService,
    RpcBidiService {
    /** Live stream of incoming [Box]es. Single-use; cancel collection to unsubscribe. */
    @KsMethod("/stream")
    suspend fun streamDeliveries(): Flow<Box>

    /** Live stream of incoming [Box]es batched into [Palette]s per the configured rates. */
    @KsMethod("/stream_bulk")
    suspend fun streamDeliveriesPacked(): Flow<Palette>

    /** Replay-only stream of buffered [Box]es. Completes after replay is exhausted. */
    @KsMethod("/dump")
    suspend fun dumpDeliveries(): Flow<Box>

    /** Replay-only stream batched into [Palette]s. Completes after replay is exhausted. */
    @KsMethod("/dump_bulk")
    suspend fun dumpDeliveriesPacked(): Flow<Palette>

    @KsMethod("/filter")
    override suspend fun weighIn(filter: WeighStation): DeliveryService
}

/** Polling interface for pulling log batches on demand. */
@KsService
interface CustomerPickup : RpcService {
    @KsMethod("/get")
    suspend fun get(maxEntries: Int = 100): Palette
}
