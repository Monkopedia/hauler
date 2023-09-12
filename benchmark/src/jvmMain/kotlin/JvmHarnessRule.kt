package com.monkopedia.hauler.benchmark

import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.sockets.asConnection
import com.monkopedia.ksrpc.sockets.internal.swallow
import com.monkopedia.ksrpc.toStub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PipedInputStream
import java.io.PipedOutputStream

class JvmHarnessRule : BeforeAfterRule() {
    private var harnessImpl: HarnessProtocol? = null
    private var conn1: Connection<String>? = null
    private var conn2: Connection<String>? = null
    private var scope: CoroutineScope? = null

    val harness: HarnessProtocol
        get() = harnessImpl ?: error("harness can only be accessed within a test")

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun before() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val o1 = PipedOutputStream()
        val i1 = PipedInputStream(o1)
        val o2 = PipedOutputStream()
        val i2 = PipedInputStream(o2)
        conn2 = (i1 to o2).asConnection(ksrpcEnvironment { })
        scope!!.launch {
            conn1 = (i2 to o1).asConnection(ksrpcEnvironment { })
            conn1?.registerDefault(HarnessJvm(), HarnessProtocol)
        }
        harnessImpl = conn2?.defaultChannel()?.toStub()
    }

    override suspend fun after() {
        swallow { harnessImpl?.finish() }
        swallow { harnessImpl?.close() }
        swallow { conn1?.close() }
        swallow { conn2?.close() }
        swallow { scope?.cancel() }
        conn1 = null
        conn2 = null
        harnessImpl = null
        scope = null
    }
}
