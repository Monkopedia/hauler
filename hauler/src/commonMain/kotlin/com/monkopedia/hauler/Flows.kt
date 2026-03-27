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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Convert a [DeliveryService] into a [Deliveries] flow using automatic delivery callbacks. */
fun DeliveryService.deliveries(closeScope: CoroutineScope): Deliveries =
    callbackFlow {
        val observer =
            object : AutomaticDelivery {
                override suspend fun onLogEvent(event: Box) {
                    trySend(event)
                }
            }
        val registration = registerDelivery(observer)
        awaitClose {
            closeScope.launch {
                registration.unregister()
            }
        }
    }

/** Convert a [DeliveryService] into a [Deliveries] flow using batched delivery day callbacks. */
fun DeliveryService.withDeliveryDay(closeScope: CoroutineScope): Deliveries =
    callbackFlow {
        val observer =
            object : DeliveryDay {
                override suspend fun onLogs(event: Palette) {
                    event.forEach {
                        trySend(it)
                    }
                }
            }
        val registration = registerDeliveryDay(observer)
        awaitClose {
            closeScope.launch {
                registration.unregister()
            }
        }
    }

/** Convert a [DeliveryService] into a [Deliveries] flow by polling at the given [interval]. */
fun DeliveryService.withPickup(
    interval: Duration = 500.milliseconds,
    maxEntries: Int = 100,
    closeScope: CoroutineScope,
): Deliveries =
    callbackFlow {
        val polling = recurringCustomerPickup()
        launch {
            while (true) {
                polling.get(maxEntries = maxEntries).forEach {
                    send(it)
                }
                delay(interval)
            }
        }
        awaitClose {
            closeScope.launch {
                polling.close()
            }
        }
    }

/** Drain the replay cache as a [Deliveries] flow, then close. */
fun DeliveryService.dumpDeliveries(): Deliveries =
    callbackFlow {
        val observer =
            object : AutomaticDelivery {
                override suspend fun onLogEvent(event: Box) {
                    send(event)
                }
            }
        dumpDelivery(observer)
        close()
    }

/** Drain the replay cache as batched [Palette]s, unpacked into a [Deliveries] flow, then close. */
fun DeliveryService.dumpWithDeliveryDay(): Deliveries =
    callbackFlow {
        val observer =
            object : DeliveryDay {
                override suspend fun onLogs(event: Palette) {
                    event.forEach {
                        send(it)
                    }
                }
            }
        dumpDeliveryDay(observer)
        close()
    }

/** Drain the replay cache by polling, as a [Deliveries] flow. */
fun DeliveryService.dumpWithPickup(
    interval: Duration = 500.milliseconds,
    maxEntries: Int = 100,
    closeScope: CoroutineScope,
): Deliveries =
    callbackFlow {
        val polling = dumpCustomerPickup()
        launch {
            while (true) {
                polling.get(maxEntries = maxEntries).forEach {
                    send(it)
                }
                delay(interval)
            }
        }
        awaitClose {
            closeScope.launch {
                polling.close()
            }
        }
    }
