package com.monkopedia.hauler.benchmark

import com.monkopedia.hauler.Shipper
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService

@KsService
interface HarnessProtocol : RpcService {
    @KsMethod("/platform")
    suspend fun getPlatform(controller: String): String

    @KsMethod("/setShipper")
    suspend fun setShipper(shipper: Shipper)

    @KsMethod("/exec")
    suspend fun execTasks(exec: ExecSpec)

    @KsMethod("/finish")
    suspend fun finish(u: Unit = Unit)
}