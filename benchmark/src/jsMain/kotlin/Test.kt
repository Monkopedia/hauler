package com.monkopedia.hauler.benchmark

import WriteStream
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.sockets.asConnection
import io.ktor.util.toByteArray
import io.ktor.util.toJsArray
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.read
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.khronos.webgl.ArrayBufferView
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import process


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
    process.stdin.on("data") { data: ArrayBufferView ->
        val waitOn = last
        last = launch {
            waitOn?.join()
            ByteReadChannel(Int8Array(data.buffer, 0, data.byteLength).toByteArray())
                .copyTo(channel)
        }
    }
    return channel
}

fun CoroutineScope.stdOutByteChannel(): ByteWriteChannel {
    val channel = ByteChannel(autoFlush = true)
    launch {
        while (!channel.isClosedForRead) {
            val callback: (source: ByteArray, start: Int, endExclusive: Int) -> Int = { source, start, endExclusive ->
                process.stdout.cowrite(
                    source,
                    start,
                    endExclusive
                )
            }
            channel.read(callback)
        }
    }
    return channel
}

fun WriteStream.cowrite(memory: ByteArray, start: Int, endExclusive: Int): Int {
    write(Uint8Array(memory.toJsArray().buffer, start, endExclusive))
    return endExclusive - start
}