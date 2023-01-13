import com.monkopedia.hauler.benchmark.HarnessImpl
import com.monkopedia.hauler.benchmark.HarnessProtocol
import com.monkopedia.ksrpc.ErrorListener
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.sockets.withStdInOut
import kotlinx.coroutines.delay
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

fun main(args: Array<String>) = runBlocking {
    val context = newSingleThreadContext("other context")
    withContext(context) {
        withStdInOut(ksrpcEnvironment {
            errorListener = ErrorListener {
                it.printStackTrace()
            }
        }) { connection ->
            connection.registerDefault(HarnessImpl("Native"), HarnessProtocol)
            while (true) delay(100000)
        }
    }
}
