package com.monkopedia.hauler

import com.monkopedia.hauler.Level.DEBUG
import com.monkopedia.hauler.Level.ERROR
import com.monkopedia.hauler.Level.INFO
import com.monkopedia.hauler.Level.TRACE
import com.monkopedia.hauler.Level.WARN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.datetime.Clock.System

typealias Hauler = FlowCollector<Box>

suspend inline fun Hauler.ship(level: Level, message: String, throwable: Throwable? = null) =
    emit(convert(level, message, throwable, CallSign.loggingName()))

fun convert(
    level: Level,
    message: String,
    throwable: Throwable?,
    threadName: String?
) = Box(
    level,
    "Hauler",
    combine(message, throwable),
    System.now().toEpochMilliseconds(),
    threadName
)

fun combine(message: String, throwable: Throwable?): String {
    if (throwable == null) return message
    return "${message}\n${throwable.stackTraceToString()}"
}

fun Hauler.asAsync(scope: CoroutineScope): AsyncHauler {
    return AsyncHaulerImpl(this, scope)
}

suspend inline fun Hauler.error(message: String, throwable: Throwable? = null) =
    ship(ERROR, message, throwable)

suspend inline fun Hauler.warn(message: String, throwable: Throwable? = null) =
    ship(WARN, message, throwable)

suspend inline fun Hauler.info(message: String, throwable: Throwable? = null) =
    ship(INFO, message, throwable)

suspend inline fun Hauler.debug(message: String, throwable: Throwable? = null) =
    ship(DEBUG, message, throwable)

suspend inline fun Hauler.trace(message: String, throwable: Throwable? = null) =
    ship(TRACE, message, throwable)

interface AsyncHauler {
    fun emit(box: Box)
}

expect fun loggingName(): String?

inline fun AsyncHauler.ship(level: Level, message: String, throwable: Throwable? = null) =
    emit(convert(level, message, throwable, loggingName()))

inline fun AsyncHauler.error(message: String, throwable: Throwable? = null) =
    ship(ERROR, message, throwable)

inline fun AsyncHauler.warn(message: String, throwable: Throwable? = null) =
    ship(WARN, message, throwable)

inline fun AsyncHauler.info(message: String, throwable: Throwable? = null) =
    ship(INFO, message, throwable)

inline fun AsyncHauler.debug(message: String, throwable: Throwable? = null) =
    ship(DEBUG, message, throwable)

inline fun AsyncHauler.trace(message: String, throwable: Throwable? = null) =
    ship(TRACE, message, throwable)
