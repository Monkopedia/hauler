package com.monkopedia.hauler

import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService

@KsService
interface Shipper : RpcService {
    @KsMethod("/pickup")
    suspend fun requestPickup(u: Unit = Unit): DropBox

    @KsMethod("/pickup_bulk")
    suspend fun requestDockPickup(u: Unit = Unit): LoadingDock

    @KsMethod("/deliveries")
    suspend fun deliveries(u: Unit = Unit): DeliveryService
}