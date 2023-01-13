package com.monkopedia.hauler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CustomerPickupImpl(private val deliveries: Deliveries, deliveryRates: DeliveryRates) :
    CustomerPickup {
    private val scope = CoroutineScope(SupervisorJob())
    private val lock = Mutex()
    private val circBuffer = CircularBuffer<Box>(deliveryRates.defaultPaletteSize)

    init {
        scope.launch {
            deliveries.collect { box ->
                lock.withLock {
                    circBuffer.add(box)
                }
            }
        }
    }

    override suspend fun get(maxEntries: Int): Palette {
        lock.withLock {
            return circBuffer.toListAndClear().pack()
        }
    }

    override suspend fun close() {
        super.close()
        scope.cancel()
    }
}
