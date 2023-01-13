package com.monkopedia.hauler

import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Default implementation af a [Shipper]. Can send and receive Boxes as needed.
 */
open class Warehouse(private val deliveryRates: DeliveryRates = DeliveryRates()) : Shipper {
    private val scope = CoroutineScope(SupervisorJob())
    private val centralFlow = MutableSharedFlow<Box>(replay = deliveryRates.defaultBoxRetention)

    override suspend fun requestPickup(u: Unit): DropBox {
        return DropBox()
    }

    override suspend fun requestDockPickup(u: Unit): LoadingDock {
        return LoadingDock()
    }

    override suspend fun deliveries(u: Unit): DeliveryService {
        return centralFlow.deliveries(scope, deliveryRates)
    }

    override suspend fun close() {
        super.close()
        scope.cancel()
    }

    inner class DropBox : com.monkopedia.hauler.DropBox {
        override suspend fun log(logEvent: Box) {
            centralFlow.emit(logEvent)
        }
    }

    inner class LoadingDock : com.monkopedia.hauler.LoadingDock {
        override suspend fun bulkLog(logs: Palette) {
            val boxes = logs.unpack()
            boxes.forEach {
                centralFlow.emit(it)
            }
        }
    }
}

private class DeliveryServiceImpl(
    private val flow: Flow<Box>,
    private val dumpFlow: Flow<Box>,
    private val scope: CoroutineScope,
    private val deliveryRates: DeliveryRates
) : DeliveryService {

    private suspend fun Flow<Box>.fetchDelivery(
        receiver: AutomaticDelivery
    ) {
        collect { box ->
            try {
                receiver.onLogEvent(box)
            } catch (t: Throwable) {
                // Don't crash, just stop observing
                return@collect
            }
        }
    }

    private suspend fun Flow<Box>.fetchDeliveryDay(receiver: DeliveryDay) {
        pack(deliveryRates).collect { palette ->
            try {
                receiver.onLogs(palette)
            } catch (t: Throwable) {
                // Don't crash, just stop observing
                return@collect
            }
        }
    }

    private fun Flow<Box>.fetchCustomerPickup(): CustomerPickup {
        return CustomerPickupImpl(this, deliveryRates)
    }

    override suspend fun registerDelivery(receiver: AutomaticDelivery): Registration {
        return RegistrationImpl(scope.launch { flow.fetchDelivery(receiver) })
    }

    override suspend fun registerDeliveryDay(receiver: DeliveryDay): Registration {
        return RegistrationImpl(scope.launch { flow.fetchDeliveryDay(receiver) })
    }

    override suspend fun recurringCustomerPickup(u: Unit): CustomerPickup {
        return flow.fetchCustomerPickup()
    }

    override suspend fun dumpDelivery(receiver: AutomaticDelivery) {
        dumpFlow.fetchDelivery(receiver)
    }

    override suspend fun dumpDeliveryDay(receiver: DeliveryDay) {
        dumpFlow.fetchDeliveryDay(receiver)
    }

    override suspend fun dumpCustomerPickup(u: Unit): CustomerPickup {
        return dumpFlow.fetchCustomerPickup()
    }

    override suspend fun weighIn(filter: WeighStation): DeliveryService {
        return DeliveryServiceImpl(
            flow.filter {
                it in filter
            },
            dumpFlow.filter {
                it in filter
            },
            scope,
            deliveryRates
        )
    }

    private class RegistrationImpl(private val collectJob: Job) : Registration {
        override suspend fun ping(u: Unit) {
            require(collectJob.isActive) {
                "Deliveries no longer available"
            }
        }

        override suspend fun unregister(u: Unit) {
            collectJob.cancel(
                CancellationException("Registration#unregister requesting stop of deliveries")
            )
        }
    }
}

fun SharedFlow<Box>.deliveries(
    scope: CoroutineScope,
    deliveryRates: DeliveryRates = DeliveryRates()
): DeliveryService {
    return deliveries(
        this,
        flow {
            val replay = replayCache.toList()
            replay.forEach { emit(it) }
        },
        scope,
        deliveryRates
    )
}

fun deliveries(
    flow: Flow<Box>,
    dumpFlow: Flow<Box>,
    scope: CoroutineScope,
    deliveryRates: DeliveryRates = DeliveryRates()
): DeliveryService {
    return DeliveryServiceImpl(flow, dumpFlow, scope, deliveryRates)
}
