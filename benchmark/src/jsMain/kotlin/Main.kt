
import com.monkopedia.hauler.benchmark.Test
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asPromise
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.js.Promise

fun main() {
    GlobalScope.async {
        coroutineScope {
            with(Test) {
                run()
            }
        }
    }.asPromise()
}
