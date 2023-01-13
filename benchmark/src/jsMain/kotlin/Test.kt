package com.monkopedia.hauler.benchmark

import Buffer
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.sockets.asConnection
import com.monkopedia.ksrpc.toStub
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.bits.copyTo
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.read
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.khronos.webgl.Uint8Array
import process
import process.global.NodeJS.WriteStream

object Test {

    suspend fun CoroutineScope.run() {
        val connection =
            (stdInByteChannel() to stdOutByteChannel()).asConnection(ksrpcEnvironment { })
        connection.registerDefault(HarnessImpl("JS"), HarnessProtocol)
        while (true) {
            delay(100000)
        }
    }
}

fun CoroutineScope.stdInByteChannel(): ByteReadChannel {
    val channel = ByteChannel(autoFlush = true)
    var last: Job? = null
    process.stdin.on("data") { data: Buffer ->
        val waitOn = last
        last = launch {
            waitOn?.join()
            ByteReadChannel(data).copyTo(channel)
        }
    }
    return channel
}

fun CoroutineScope.stdOutByteChannel(): ByteWriteChannel {
    val channel = ByteChannel(autoFlush = true)
    launch {
        while (!channel.isClosedForRead) {
            val callback: (source: Memory, start: Long, endExclusive: Long) -> Int = { source, start, endExclusive ->
                process.stdout.cowrite(
                    source,
                    start,
                    endExclusive
                )
            }
            channel.read(1, callback)
        }
    }
    return channel
}

fun WriteStream.cowrite(memory: Memory, start: Long, endExclusive: Long): Int {
    val arr = Uint8Array((endExclusive - start).toInt())
    memory.copyTo(arr, start.toInt(), endExclusive.toInt(), 0)
    write(arr)
    return arr.length
}
