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

/** Central dispatch for log transport. Request a [DropBox] for single messages, a [LoadingDock] for batches, or [DeliveryService] for observing. */
@KsService
interface Shipper : RpcService {
    @KsMethod("/pickup")
    suspend fun requestPickup(u: Unit = Unit): DropBox

    @KsMethod("/pickup_bulk")
    suspend fun requestDockPickup(u: Unit = Unit): LoadingDock

    @KsMethod("/deliveries")
    suspend fun deliveries(u: Unit = Unit): DeliveryService
}
