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

import kotlinx.coroutines.runBlocking
import org.junit.internal.TextListener
import org.junit.runner.JUnitCore
import org.junit.runner.Result
import kotlin.system.exitProcess

lateinit var nativeArgs: List<String>
lateinit var nodeArgs: List<String>

fun main(args: Array<String>): Unit =
    runBlocking {
        val numArgs = args.first().toInt()
        nodeArgs = args.toList().subList(1, numArgs + 1)
        nativeArgs = args.toList().subList(numArgs + 1, args.size)

        val junit = JUnitCore()
        junit.addListener(TextListener(System.out))

        val result: Result =
            junit.run(
                BasicNodeTest::class.java,
                BasicNativeTest::class.java,
                BasicJvmTest::class.java,
                HighVolumeNodeTest::class.java,
                HighVolumeNativeTest::class.java,
                HighVolumeJvmTest::class.java,
            )

        resultReport(result)

        exitProcess(0)
    }

fun resultReport(result: Result) {
    println(
        "Finished. Result: Failures: ${result.failureCount}. " +
            "Ignored: ${result.ignoreCount}. " +
            "Tests run: ${result.runCount}. " +
            "Time: ${result.runTime}ms.",
    )
}
