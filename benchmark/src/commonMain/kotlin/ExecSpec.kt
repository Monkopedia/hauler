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
package com.monkopedia.hauler.benchmark

import com.monkopedia.hauler.DeliveryRates
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class ExecSpec(
    val name: String,
    val connection: ConnectionSpec,
    val taskSpec: List<TaskSpec>,
)

enum class ConnectionType {
    DEFAULT,
    HTTPS,
    WSS,
}

enum class ShippingType {
    DEFAULT,
    PACKED,
}

@Serializable
data class ConnectionSpec(
    val type: ConnectionType,
    val endpoint: String?,
    val shippingType: ShippingType,
    val deliveryRates: SerializableDeliveryRates?,
)

@Serializable
data class SerializableDeliveryRates(
    val defaultBoxRetention: Int,
    val defaultPaletteSize: Int,
    val defaultPaletteInterval: Long,
) {
    val asDeliveryRates: DeliveryRates
        get() =
            DeliveryRates(
                defaultBoxRetention,
                defaultPaletteSize,
                defaultPaletteInterval.milliseconds,
            )

    companion object {
        fun from(deliveryRates: DeliveryRates): SerializableDeliveryRates =
            SerializableDeliveryRates(
                defaultBoxRetention = deliveryRates.defaultBoxRetention,
                defaultPaletteSize = deliveryRates.defaultPaletteSize,
                defaultPaletteInterval = deliveryRates.defaultPaletteInterval.inWholeMilliseconds,
            )
    }
}

@Serializable
data class TaskSpec(
    val loggerName: String,
    val threadName: String,
    val startDelay: Long,
    val interval: Long,
    val count: Int,
)
