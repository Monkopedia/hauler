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

import kotlinx.coroutines.CancellationException
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
open class Warehouse(
    private val deliveryRates: DeliveryRates = DeliveryRates(),
) : Shipper {
    private val scope = CoroutineScope(SupervisorJob())
    private val centralFlow = MutableSharedFlow<Box>(replay = deliveryRates.defaultBoxRetention)

    override suspend fun requestPickup(u: Unit): DropBox = DropBox()

    override suspend fun requestDockPickup(u: Unit): LoadingDock = LoadingDock()

    override suspend fun deliveries(u: Unit): DeliveryService = centralFlow.deliveries(scope, deliveryRates)

    override suspend fun close() {
        try {
            super.close()
        } finally {
            scope.cancel()
        }
    }

    inner class DropBox : com.monkopedia.hauler.DropBox {
        override suspend fun log(logEvent: Box) {
            centralFlow.emit(logEvent)
        }
    }

    inner class LoadingDock : com.monkopedia.hauler.LoadingDock {
        override suspend fun bulkLog(logs: Palette) {
            logs.forEach {
                centralFlow.emit(it)
            }
        }
    }
}

private class DeliveryServiceImpl(
    private val flow: Flow<Box>,
    private val dumpFlow: Flow<Box>,
    private val scope: CoroutineScope,
    private val deliveryRates: DeliveryRates,
) : DeliveryService {
    private suspend fun Flow<Box>.fetchDelivery(receiver: AutomaticDelivery) {
        collect { box ->
            try {
                receiver.onLogEvent(box)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                deliveryRates.onDeliveryError(t)
                return@collect
            }
        }
    }

    private suspend fun Flow<Box>.fetchDeliveryDay(receiver: DeliveryDay) {
        pack(deliveryRates).collect { palette ->
            try {
                receiver.onLogs(palette)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                deliveryRates.onDeliveryError(t)
                return@collect
            }
        }
    }

    private fun Flow<Box>.fetchCustomerPickup(): CustomerPickup = CustomerPickupImpl(this, deliveryRates)

    override suspend fun registerDelivery(receiver: AutomaticDelivery): Registration =
        RegistrationImpl(
            scope.launch {
                flow.fetchDelivery(receiver)
            },
        )

    override suspend fun registerDeliveryDay(receiver: DeliveryDay): Registration =
        RegistrationImpl(
            scope.launch {
                flow.fetchDeliveryDay(receiver)
            },
        )

    override suspend fun recurringCustomerPickup(u: Unit): CustomerPickup = flow.fetchCustomerPickup()

    override suspend fun dumpDelivery(receiver: AutomaticDelivery) {
        dumpFlow.fetchDelivery(receiver)
    }

    override suspend fun dumpDeliveryDay(receiver: DeliveryDay) {
        dumpFlow.fetchDeliveryDay(receiver)
    }

    override suspend fun dumpCustomerPickup(u: Unit): CustomerPickup = dumpFlow.fetchCustomerPickup()

    override suspend fun weighIn(filter: WeighStation): DeliveryService =
        DeliveryServiceImpl(
            flow.filter {
                it in filter
            },
            dumpFlow.filter {
                it in filter
            },
            scope,
            deliveryRates,
        )

    private class RegistrationImpl(
        private val collectJob: Job,
    ) : Registration {
        override suspend fun ping(u: Unit) {
            require(collectJob.isActive) {
                "Deliveries no longer available"
            }
        }

        override suspend fun unregister(u: Unit) {
            collectJob.cancel(
                CancellationException("Registration#unregister requesting stop of deliveries"),
            )
        }
    }
}

fun SharedFlow<Box>.deliveries(
    scope: CoroutineScope,
    deliveryRates: DeliveryRates = DeliveryRates(),
): DeliveryService =
    deliveries(
        this,
        flow {
            replayCache.forEach { emit(it) }
        },
        scope,
        deliveryRates,
    )

fun deliveries(
    flow: Flow<Box>,
    dumpFlow: Flow<Box>,
    scope: CoroutineScope,
    deliveryRates: DeliveryRates = DeliveryRates(),
): DeliveryService = DeliveryServiceImpl(flow, dumpFlow, scope, deliveryRates)
