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

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.CoroutineContext.Key

/**
 * Identifies the source of a log transmission, analogous to a radio call sign.
 *
 * Add a [CallSign] to a coroutine context to tag all logs emitted within it:
 * ```
 * withContext(CallSign("request-handler")) {
 *     hauler.info("Processing request")  // threadName = "request-handler"
 * }
 * ```
 *
 * On JVM, captures the thread name via ThreadLocal when no CallSign is in the
 * coroutine context. On JS/Native/WASM, relies on coroutine context only.
 */
expect class CallSign(
    name: String,
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CallSign> {
        suspend fun loggingName(): String?
    }

    override val key: Key
}
