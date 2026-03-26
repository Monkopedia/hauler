/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

fun main(args: Array<String>) =
    runBlocking {
        val context = newSingleThreadContext("other context")
        withContext(context) {
            withStdInOut(
                ksrpcEnvironment {
                    errorListener =
                        ErrorListener {
                            it.printStackTrace()
                        }
                },
            ) { connection ->
                connection.registerDefault(HarnessImpl("Native"), HarnessProtocol)
                while (true) delay(100000)
            }
        }
    }
