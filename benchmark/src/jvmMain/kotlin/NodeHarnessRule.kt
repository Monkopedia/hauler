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
        connection =
            ProcessBuilder(nodeArgs)
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
