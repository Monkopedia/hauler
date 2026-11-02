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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
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

/**
 * Forward all deliveries to a [DropBox], one [Box] at a time.
 *
 * Subscribed to this flow by the time it returns, so a [Box] emitted right after
 * the call reaches [dropBox] even on a hot `replay = 0` source like
 * [Garage.deliveries] (#65). [CoroutineStart.UNDISPATCHED] runs the collector up
 * to its first suspension -- which is after `SharedFlow.collect` has registered --
 * before [launch] returns. The `suspend` is no longer needed for that and stays
 * only so the published signature does not change.
 */
suspend fun Deliveries.forwardTo(
    dropBox: DropBox,
    scope: CoroutineScope,
): Job =
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
        collect(dropBox::log)
    }

/**
 * Forward all deliveries to a [LoadingDock], batching into [Palette]s per [deliveryRates].
 *
 * Subscribed to this flow by the time it returns (#65), for the same reason as the
 * [DropBox] overload. Starting [pack] undispatched is not enough on its own: its
 * `merge` subscribes to the source from a child it DISPATCHES, so the source is
 * collected here, undispatched, and handed to [pack] through a channel.
 */
fun Deliveries.forwardTo(
    dock: LoadingDock,
    scope: CoroutineScope,
    deliveryRates: DeliveryRates = DeliveryRates(),
): Job =
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
        val boxes = Channel<Box>()
        launch(start = CoroutineStart.UNDISPATCHED) {
            collect(boxes::send)
            boxes.close()
        }
        boxes.consumeAsFlow().pack(deliveryRates).collect(dock::bulkLog)
    }
