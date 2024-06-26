package com.monkopedia.hauler.benchmark

import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.sockets.asConnection
import com.monkopedia.ksrpc.sockets.internal.swallow
import com.monkopedia.ksrpc.toStub

class NodeHarnessRule : BeforeAfterRule() {
    private var harnessImpl: HarnessProtocol? = null
    private var connection: Connection<String>? = null

    val harness: HarnessProtocol
        get() = harnessImpl ?: error("harness can only be accessed within a test")

    override suspend fun before() {
        connection = ProcessBuilder(nodeArgs)
            .asConnection(ksrpcEnvironment { })
        harnessImpl = connection?.defaultChannel()?.toStub()
    }

    override suspend fun after() {
        swallow { harnessImpl?.finish() }
        swallow { harnessImpl?.close() }
        swallow { connection?.close() }
        connection = null
        harnessImpl = null
    }
}
