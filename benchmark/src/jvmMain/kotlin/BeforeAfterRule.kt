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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.CountDownLatch

abstract class BeforeAfterRule : TestRule {
    abstract suspend fun before()

    abstract suspend fun after()

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                runSync {
                    before()
                }
                try {
                    base.evaluate()
                } finally {
                    try {
                        runSync {
                            after()
                        }
                    } catch (t: Throwable) {
                    }
                }
            }

            override fun toString(): String = description.toString()
        }
}

internal inline fun runSync(crossinline exec: suspend () -> Unit) {
    val latch = CountDownLatch(1)
    val scope = CoroutineScope(SupervisorJob())
    scope.launch(Dispatchers.IO) {
        exec()
        latch.countDown()
    }
    latch.await()
}
