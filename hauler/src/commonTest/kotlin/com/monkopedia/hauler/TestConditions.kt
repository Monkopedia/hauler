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
 * [description] is REPORTED IN THE FAILURE. Two of the four private copies this
 * replaces took a description and never read it, and 18 call sites polled
 * through them across four files -- including exactly the tests that fail
 * intermittently (#51). A caller who supplies a description reasonably believes
 * they have made the failure diagnosable; that belief has to be true.
 *
 * ADDS to the runtime's message, never replaces it -- and which runtime message
 * you get depends on the clock, which is the part I got wrong first time and
 * then measured:
 *
 *     on runTest's VIRTUAL clock:
 *       "Timed out after 5s of _virtual_ (kotlinx.coroutines.test) time. To use
 *        the real time, wrap 'withTimeout' in
 *        'withContext(Dispatchers.Default.limitedParallelism(1))'"
 *
 *     inside withContext(Dispatchers.Default):
 *       "Timed out waiting for 5000 ms"
 *
 * The rich text comes from kotlinx-coroutines-test's TestDispatcher and fires
 * ONLY on the test scheduler. **15 of the 18 call sites are on a real
 * dispatcher**, where the runtime says nothing about which clock or what to do.
 * So #51's original complaint -- that a timeout named nothing -- was RIGHT for
 * the majority of sites, and the correction I first wrote here (that the
 * runtime "unconditionally" names the clock and the remedy) was measured on the
 * virtual case alone and generalised. It is withdrawn.
 *
 * That makes the description MORE load-bearing than I claimed, not less: for
 * most callers it is the only thing in the message that identifies what was
 * being awaited. [e] is passed as the cause, so whichever runtime text applies
 * survives along with its stack trace.
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
