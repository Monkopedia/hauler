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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Transforms a [Box] into one or more display strings. */
typealias Formatter = suspend FlowCollector<String>.(Box) -> Unit

/** A sink for formatted log strings (e.g., console output). */
typealias Display = FlowCollector<String>

fun Deliveries.route(
    scope: CoroutineScope,
    display: Display,
    formatter: Formatter = DefaultFormat,
): Job =
    scope.launch {
        route(display, formatter)
    }

suspend fun Deliveries.route(
    display: Display,
    formatter: Formatter = DefaultFormat,
) {
    transform(formatter).collect(display)
}

val DefaultFormat: Formatter = FlowCollector<String>::defaultFormat

private val defaultTimeZone = TimeZone.currentSystemDefault()

/** Format a [Box] as "timestamp (thread) logger - message", one line per newline in the message. */
suspend fun FlowCollector<String>.defaultFormat(box: Box) {
    val time =
        Instant
            .fromEpochMilliseconds(box.timestamp)
            .toLocalDateTime(defaultTimeZone)
    val prefix = "$time (${box.threadName}) ${box.loggerName} - "
    box.message.split("\n").forEach {
        emit(prefix + it)
    }
}
