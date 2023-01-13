package com.monkopedia.hauler

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Options specifying how often deliveries will happen and how they will perform.
 */
data class DeliveryRates(
    val defaultBoxRetention: Int = 1000,
    val defaultPaletteSize: Int = 500,
    val defaultPaletteInterval: Duration = 500.milliseconds
)