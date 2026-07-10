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
package com.monkopedia.hauler

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * JVM-specific log-source attribution for [CallSign.loggingName] and the two ship paths.
 *
 * On JVM both the suspend [Hauler.ship] path (via [CallSign.loggingName]) and the
 * non-suspend [AsyncHauler.ship] path (via the top-level [loggingName]) fall back to the
 * current thread name when no [CallSign] is present, so the same log gets a thread name
 * regardless of which entry point emitted it.
 */
class LoggingNameJvmTest {
    @Test
    fun loggingName_fallsBackToCurrentThreadName_whenNoCallSign() =
        runTest {
            assertEquals(Thread.currentThread().name, CallSign.loggingName())
        }

    @Test
    fun loggingName_prefersCallSignName_overThreadName() =
        runTest {
            withContext(CallSign("request-handler")) {
                assertEquals("request-handler", CallSign.loggingName())
            }
        }

    @Test
    fun shipPaths_agreeOnThreadNameAttribution_whenNoCallSign() =
        runTest {
            val collected = mutableListOf<Box>()
            val hauler = Hauler { collected.add(it) }

            hauler.ship(Level.INFO, "sync")
            hauler.asAsync(this).ship(Level.INFO, "async")
            withTimeout(2.seconds) {
                while (collected.size < 2) delay(10)
            }

            // Before the fix the suspend path recorded threadName = null here while the
            // async path recorded the thread name; both now attribute a thread name.
            assertNotNull(collected.first { it.message == "sync" }.threadName)
            assertNotNull(collected.first { it.message == "async" }.threadName)
        }
}
