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
 * took a description and never read it, so a timeout named no condition at all
 * while seven tests polled through it -- exactly the tests that fail
 * intermittently (#51). A caller who supplies a description reasonably believes
 * they have made the failure diagnosable; that belief has to be true.
 *
 * ADDS to the runtime's message, never replaces it. Measured, not assumed:
 *
 *     Timed out after 5s of _virtual_ (kotlinx.coroutines.test) time. To use the
 *     real time, wrap 'withTimeout' in
 *     'withContext(Dispatchers.Default.limitedParallelism(1))'
 *
 * That already names WHICH CLOCK ran out and WHAT TO DO -- unconditionally, and
 * it is the single most expensive thing the #44 investigation had to learn. #51
 * quoted it truncated at "time." and concluded it "named nothing"; it named
 * everything except the one thing this helper knows, which is what was being
 * awaited. So the description is prepended and [e] is passed as the cause, which
 * keeps the original message and its stack trace.
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
            "awaitCondition gave up waiting for " +
                description.ifEmpty { "an UNNAMED condition (pass a description)" } +
                " -- " + e.message,
            e,
        )
    }
}
