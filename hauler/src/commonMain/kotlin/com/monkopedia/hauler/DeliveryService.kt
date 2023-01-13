package com.monkopedia.hauler

import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import kotlinx.coroutines.flow.Flow

typealias Deliveries = Flow<Box>

@KsService
interface DeliveryService : RpcService {
    @KsMethod("/register")
    suspend fun registerDelivery(receiver: AutomaticDelivery): Registration

    @KsMethod("/register_bulk")
    suspend fun registerDeliveryDay(receiver: DeliveryDay): Registration

    @KsMethod("/register_poll")
    suspend fun recurringCustomerPickup(u: Unit = Unit): CustomerPickup

    @KsMethod("/dump")
    suspend fun dumpDelivery(receiver: AutomaticDelivery)

    @KsMethod("/dump_bulk")
    suspend fun dumpDeliveryDay(receiver: DeliveryDay)

    @KsMethod("/dump_poll")
    suspend fun dumpCustomerPickup(u: Unit = Unit): CustomerPickup

    @KsMethod("/filter")
    suspend fun weighIn(filter: WeighStation): DeliveryService
}

@KsService
interface Registration : RpcService {
    @KsMethod("/ping")
    suspend fun ping(u: Unit = Unit)

    @KsMethod("/unregister")
    suspend fun unregister(u: Unit = Unit)
}

@KsService
interface AutomaticDelivery : RpcService {
    @KsMethod("/logs")
    suspend fun onLogEvent(event: Box)
}

@KsService
interface DeliveryDay : RpcService {
    @KsMethod("/logs")
    suspend fun onLogs(event: Palette)
}

@KsService
interface CustomerPickup : RpcService {
    @KsMethod("/get")
    suspend fun get(maxEntries: Int = 100): Palette
}
