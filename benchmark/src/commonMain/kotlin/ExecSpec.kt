package com.monkopedia.hauler.benchmark

import com.monkopedia.hauler.DeliveryRates
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class ExecSpec(
    val name: String,
    val connection: ConnectionSpec,
    val taskSpec: List<TaskSpec>
)

enum class ConnectionType {
    DEFAULT,
    HTTPS,
    WSS
}

enum class ShippingType {
    DEFAULT,
    PACKED
}

@Serializable
data class ConnectionSpec(
    val type: ConnectionType,
    val endpoint: String?,
    val shippingType: ShippingType,
    val deliveryRates: SerializableDeliveryRates?
)

@Serializable
data class SerializableDeliveryRates(
    val defaultBoxRetention: Int,
    val defaultPaletteSize: Int,
    val defaultPaletteInterval: Long
) {
    val asDeliveryRates: DeliveryRates
        get() = DeliveryRates(
            defaultBoxRetention,
            defaultPaletteSize,
            defaultPaletteInterval.milliseconds
        )

    companion object {
        fun from(deliveryRates: DeliveryRates): SerializableDeliveryRates {
            return SerializableDeliveryRates(
                defaultBoxRetention = deliveryRates.defaultBoxRetention,
                defaultPaletteSize = deliveryRates.defaultPaletteSize,
                defaultPaletteInterval = deliveryRates.defaultPaletteInterval.inWholeMilliseconds
            )
        }
    }
}

@Serializable
data class TaskSpec(
    val loggerName: String,
    val threadName: String,
    val startDelay: Long,
    val interval: Long,
    val count: Int
)
