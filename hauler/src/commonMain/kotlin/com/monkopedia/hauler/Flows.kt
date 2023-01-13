package com.monkopedia.hauler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
fun DeliveryService.deliveries(closeScope: CoroutineScope = GlobalScope): Deliveries =
    callbackFlow {
        val job = SupervisorJob(coroutineContext[Job])
        val scope = CoroutineScope(closeScope.coroutineContext + job)
        val observer = object : AutomaticDelivery {
            override suspend fun onLogEvent(event: Box) {
                scope.launch {
                    send(event)
                }
            }
        }
        val registration = registerDelivery(observer)
        awaitClose {
            job.cancel()
            closeScope.launch {
                registration.unregister()
            }
        }
    }

@OptIn(DelicateCoroutinesApi::class)
fun DeliveryService.withDeliveryDay(closeScope: CoroutineScope = GlobalScope): Deliveries =
    callbackFlow {
        val job = SupervisorJob(coroutineContext[Job])
        val scope = CoroutineScope(job)
        val observer = object : DeliveryDay {
            override suspend fun onLogs(event: Palette) {
                scope.launch {
                    event.forEach {
                        send(it)
                    }
                }
            }
        }
        val registration = registerDeliveryDay(observer)
        awaitClose {
            job.cancel()
            closeScope.launch {
                registration.unregister()
            }
        }
    }

@OptIn(DelicateCoroutinesApi::class)
fun DeliveryService.withPickup(
    interval: Long = 500L,
    maxEntries: Int = 100,
    closeScope: CoroutineScope = GlobalScope
): Deliveries =
    callbackFlow {
        coroutineScope {
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
    }

fun DeliveryService.dumpDeliveries(): Deliveries =
    callbackFlow {
        coroutineScope {
            val observer = object : AutomaticDelivery {
                override suspend fun onLogEvent(event: Box) {
                    launch {
                        send(event)
                    }
                }
            }
            dumpDelivery(observer)
        }
        close()
    }

fun DeliveryService.dumpWithDeliveryDay(): Deliveries =
    callbackFlow {
        coroutineScope {
            val observer = object : DeliveryDay {
                override suspend fun onLogs(event: Palette) {
                    launch {
                        event.forEach {
                            send(it)
                        }
                    }
                }
            }
            dumpDeliveryDay(observer)
            close()
        }
    }

@OptIn(DelicateCoroutinesApi::class)
fun DeliveryService.dumpWithPickup(
    interval: Long = 500L,
    maxEntries: Int = 100,
    closeScope: CoroutineScope = GlobalScope
): Deliveries =
    callbackFlow {
        coroutineScope {
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
    }
