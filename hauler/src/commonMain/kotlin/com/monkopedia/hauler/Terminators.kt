package com.monkopedia.hauler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

typealias Formatter = suspend FlowCollector<String>.(Box) -> Unit

typealias Display = FlowCollector<String>

fun Deliveries.route(
    scope: CoroutineScope,
    display: Display,
    formatter: Formatter = DefaultFormat
): Job {
    return scope.launch {
        route(display, formatter)
    }
}

suspend fun Deliveries.route(display: Display, formatter: Formatter = DefaultFormat) {
    transform(formatter).collect(display)
}

val DefaultFormat: Formatter = FlowCollector<String>::defaultFormat

suspend fun FlowCollector<String>.defaultFormat(box: Box) {
    val time = Instant.fromEpochMilliseconds(box.timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val prefix = "$time (${box.threadName}) ${box.loggerName} - "
    box.message.split("\n").forEach {
        emit(prefix + it)
    }
}
