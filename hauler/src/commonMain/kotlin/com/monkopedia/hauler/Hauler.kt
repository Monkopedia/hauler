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

import com.monkopedia.hauler.Level.DEBUG
import com.monkopedia.hauler.Level.ERROR
import com.monkopedia.hauler.Level.INFO
import com.monkopedia.hauler.Level.TRACE
import com.monkopedia.hauler.Level.WARN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlin.time.Clock

/**
 * The core logging interface. A Hauler accepts [Box]es (log messages) via [emit].
 *
 * Use [ship], [error], [warn], [info], [debug], or [trace] extension functions
 * to create and emit log messages with appropriate metadata.
 */
typealias Hauler = FlowCollector<Box>

/** Emit a log message at the given [level], optionally including a [throwable] stack trace. */
suspend inline fun Hauler.ship(
    level: Level,
    message: String,
    throwable: Throwable? = null,
    metadata: Map<String, String>? = null,
) = emit(convert(level, message, throwable, CallSign.loggingName(), metadata))

/** Create a [Box] from the given log parameters without emitting it. */
@PublishedApi
internal fun convert(
    level: Level,
    message: String,
    throwable: Throwable?,
    threadName: String?,
    metadata: Map<String, String>? = null,
) = Box(
    level,
    "Hauler",
    combine(message, throwable),
    Clock.System.now().toEpochMilliseconds(),
    threadName,
    metadata,
)

/** Combine a message with an optional throwable's stack trace. */
@PublishedApi
internal fun combine(
    message: String,
    throwable: Throwable?,
): String {
    if (throwable == null) return message
    return "${message}\n${throwable.stackTraceToString()}"
}

/** Wrap this [Hauler] for non-suspend usage, launching emissions in the given [scope]. */
fun Hauler.asAsync(scope: CoroutineScope): AsyncHauler = AsyncHaulerImpl(this, scope)

suspend inline fun Hauler.error(
    message: String,
    throwable: Throwable? = null,
    metadata: Map<String, String>? = null,
) = ship(ERROR, message, throwable, metadata)

suspend inline fun Hauler.warn(
    message: String,
    throwable: Throwable? = null,
    metadata: Map<String, String>? = null,
) = ship(WARN, message, throwable, metadata)

suspend inline fun Hauler.info(
    message: String,
    throwable: Throwable? = null,
    metadata: Map<String, String>? = null,
) = ship(INFO, message, throwable, metadata)

suspend inline fun Hauler.debug(
    message: String,
    throwable: Throwable? = null,
    metadata: Map<String, String>? = null,
) = ship(DEBUG, message, throwable, metadata)

suspend inline fun Hauler.trace(
    message: String,
    throwable: Throwable? = null,
    metadata: Map<String, String>? = null,
) = ship(TRACE, message, throwable, metadata)

/** Non-suspend variant of [Hauler] for use outside coroutine contexts. */
interface AsyncHauler {
    fun emit(box: Box)
}

/**
 * Get the current logging name for this execution context.
 * On JVM, returns the thread name. On other platforms, returns null.
 */
expect fun loggingName(): String?

inline fun AsyncHauler.ship(
    level: Level,
    message: String,
    throwable: Throwable? = null,
    metadata: Map<String, String>? = null,
) = emit(convert(level, message, throwable, loggingName(), metadata))

inline fun AsyncHauler.error(
    message: String,
    throwable: Throwable? = null,
    metadata: Map<String, String>? = null,
) = ship(ERROR, message, throwable, metadata)

inline fun AsyncHauler.warn(
    message: String,
    throwable: Throwable? = null,
    metadata: Map<String, String>? = null,
) = ship(WARN, message, throwable, metadata)

inline fun AsyncHauler.info(
    message: String,
    throwable: Throwable? = null,
    metadata: Map<String, String>? = null,
) = ship(INFO, message, throwable, metadata)

inline fun AsyncHauler.debug(
    message: String,
    throwable: Throwable? = null,
    metadata: Map<String, String>? = null,
) = ship(DEBUG, message, throwable, metadata)

inline fun AsyncHauler.trace(
    message: String,
    throwable: Throwable? = null,
    metadata: Map<String, String>? = null,
) = ship(TRACE, message, throwable, metadata)
