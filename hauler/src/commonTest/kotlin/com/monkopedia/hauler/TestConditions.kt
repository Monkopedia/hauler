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

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

/**
 * Polls [check] until it returns true, failing the test after five seconds.
 *
 * [description] is REPORTED IN THE FAILURE. Four private copies of this helper
 * used to take a description and never read it, so a timeout named nothing and
 * seven tests polled through it -- exactly the tests that fail intermittently
 * (#51). A caller who supplies a description reasonably believes they have made
 * the failure diagnosable; that belief has to be true.
 *
 * The message also names the virtual-clock trap, because that is what the raw
 * timeout looked like during the #44 investigation and it cost hours to read:
 * inside `runTest`, five seconds of VIRTUAL time can elapse in milliseconds of
 * real time, so a collector on a real dispatcher gets almost no wall clock.
 */
internal suspend fun awaitCondition(
    description: String = "",
    check: suspend () -> Boolean,
) {
    try {
        withTimeout(5.seconds) {
            while (!check()) delay(1)
        }
    } catch (e: TimeoutCancellationException) {
        fail(
            "awaitCondition timed out after 5s waiting for " +
                description.ifEmpty { "an UNNAMED condition (pass a description)" } +
                ". If this test is not wrapped in withContext(Dispatchers.Default) " +
                "that was 5s of VIRTUAL time, which can pass in milliseconds of " +
                "real time -- see #44.",
        )
    }
}
