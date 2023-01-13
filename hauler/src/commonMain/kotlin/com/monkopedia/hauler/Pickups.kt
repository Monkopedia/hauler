package com.monkopedia.hauler

import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@KsService
interface DropBox : RpcService {
    @KsMethod("/log")
    suspend fun log(logEvent: Box)
}

@KsService
interface LoadingDock : RpcService {
    @KsMethod("/li")
    suspend fun bulkLog(logs: Palette)
}

suspend fun Deliveries.attach(dropBox: DropBox, scope: CoroutineScope): Job {
    val launched = CompletableDeferred<Unit>()
    return scope.launch {
        launched.complete(Unit)
        collect {
            dropBox.log(it)
        }
    }.also {
        launched.await()
    }
}

fun Deliveries.attach(
    dock: LoadingDock,
    scope: CoroutineScope,
    deliveryRates: DeliveryRates = DeliveryRates()
): Job {
    return scope.launch {
        pack(deliveryRates).collect(dock::bulkLog)
    }
}
