package com.monkopedia.hauler.benchmark

import kotlinx.coroutines.runBlocking
import org.junit.internal.TextListener
import org.junit.runner.JUnitCore
import org.junit.runner.Result
import kotlin.system.exitProcess

lateinit var nativeArgs: List<String>
lateinit var nodeArgs: List<String>

fun main(args: Array<String>): Unit = runBlocking {
    val numArgs = args.first().toInt()
    nodeArgs = args.toList().subList(1, numArgs + 1)
    nativeArgs = args.toList().subList(numArgs + 1, args.size)

    val junit = JUnitCore()
    junit.addListener(TextListener(System.out))

    val result: Result = junit.run(
        BasicNodeTest::class.java,
        BasicNativeTest::class.java,
        BasicJvmTest::class.java,
        HighVolumeNodeTest::class.java,
        HighVolumeNativeTest::class.java,
        HighVolumeJvmTest::class.java
    )

    resultReport(result)

    exitProcess(0)
}

fun resultReport(result: Result) {
    println(
        "Finished. Result: Failures: ${result.failureCount}. " +
            "Ignored: ${result.ignoreCount}. " +
            "Tests run: ${result.runCount}. " +
            "Time: ${result.runTime}ms."
    )
}
