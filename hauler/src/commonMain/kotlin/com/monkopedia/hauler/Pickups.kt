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

import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Endpoint for sending individual log [Box]es to a remote [Shipper]. */
@KsService
interface DropBox : RpcService {
    @KsMethod("/log")
    suspend fun log(logEvent: Box)
}

/** Endpoint for sending batched [Palette]s to a remote [Shipper]. More efficient than [DropBox] for high throughput. */
@KsService
interface LoadingDock : RpcService {
    @KsMethod("/li")
    suspend fun bulkLog(logs: Palette)
}

/** Forward all deliveries to a [DropBox], one [Box] at a time. */
suspend fun Deliveries.attach(
    dropBox: DropBox,
    scope: CoroutineScope,
): Job {
    val launched = CompletableDeferred<Unit>()
    return scope
        .launch {
            launched.complete(Unit)
            collect {
                dropBox.log(it)
            }
        }.also {
            launched.await()
        }
}

/** Forward all deliveries to a [LoadingDock], batching into [Palette]s per [deliveryRates]. */
fun Deliveries.attach(
    dock: LoadingDock,
    scope: CoroutineScope,
    deliveryRates: DeliveryRates = DeliveryRates(onDeliveryError = {}),
): Job =
    scope.launch {
        pack(deliveryRates).collect(dock::bulkLog)
    }
