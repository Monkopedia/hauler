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

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
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
                    } catch (t: Throwable) { }
                }
            }

            override fun toString(): String {
                return description.toString()
            }
        }
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
