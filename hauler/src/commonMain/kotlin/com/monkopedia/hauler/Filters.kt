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

import kotlinx.serialization.Serializable

/**
 * Monitors truck contents, regulates what gets through.
 */
@Serializable
sealed class WeighStation {
    abstract operator fun contains(logEvent: Box): Boolean
}

/** Passes a [Box] only if both filters accept it. */
@Serializable
data class AndFilter(
    val first: WeighStation,
    val second: WeighStation,
) : WeighStation() {
    override fun contains(logEvent: Box): Boolean = logEvent in first && logEvent in second
}

/** Passes a [Box] if either filter accepts it. */
@Serializable
data class OrFilter(
    val first: WeighStation,
    val second: WeighStation,
) : WeighStation() {
    override fun contains(logEvent: Box): Boolean = logEvent in first || logEvent in second
}

/** Inverts a filter: passes a [Box] only if the base filter rejects it. */
@Serializable
data class NotFilter(
    val base: WeighStation,
) : WeighStation() {
    override fun contains(logEvent: Box): Boolean = logEvent !in base
}

/** String matching strategies for logger name, thread name, and message filters. */
enum class LoggerMatchMode {
    EXACT {
        override fun checkMatch(
            query: String,
            actual: String,
        ): Boolean = query == actual
    },
    PREFIX {
        override fun checkMatch(
            query: String,
            actual: String,
        ): Boolean = actual.startsWith(query)
    },
    REGEX {
        // Copy-on-write cache: immutable map replaced atomically.
        // Tolerates duplicate compilations under races but never corrupts state.
        private var cache = mapOf<String, Regex>()

        override fun checkMatch(
            query: String,
            actual: String,
        ): Boolean {
            cache[query]?.let { return it.matches(actual) }
            val regex = Regex(query)
            cache = cache + (query to regex)
            return regex.matches(actual)
        }
    }, ;

    abstract fun checkMatch(
        query: String,
        actual: String,
    ): Boolean
}

/** Filter [Box]es by their [Box.loggerName] using the given [matchMode]. */
@Serializable
data class LoggerNameFilter(
    val matchMode: LoggerMatchMode,
    val query: String,
) : WeighStation() {
    override fun contains(logEvent: Box): Boolean = matchMode.checkMatch(query, logEvent.loggerName)
}

/** Filter [Box]es by their [Box.threadName]. Returns false for boxes with null thread name. */
@Serializable
data class ThreadNameFilter(
    val matchMode: LoggerMatchMode,
    val query: String,
) : WeighStation() {
    override fun contains(logEvent: Box): Boolean {
        return matchMode.checkMatch(query, logEvent.threadName ?: return false)
    }
}

/** Filter [Box]es by their [Box.message] content using the given [matchMode]. */
@Serializable
data class MessageFilter(
    val matchMode: LoggerMatchMode,
    val query: String,
) : WeighStation() {
    override fun contains(logEvent: Box): Boolean = matchMode.checkMatch(query, logEvent.message)
}

/** Comparison strategies for [LevelFilter] and [TimeFilter]. */
enum class LevelMatchMode {
    EQ {
        override fun <T : Comparable<T>> checkMatch(
            query: T,
            actual: T,
        ): Boolean = query.compareTo(actual) == 0
    },
    LT {
        override fun <T : Comparable<T>> checkMatch(
            query: T,
            actual: T,
        ): Boolean = actual < query
    },
    GT {
        override fun <T : Comparable<T>> checkMatch(
            query: T,
            actual: T,
        ): Boolean = actual > query
    }, ;

    abstract fun <T : Comparable<T>> checkMatch(
        query: T,
        actual: T,
    ): Boolean
}

/** Filter [Box]es by their [Level] using the given comparison [mode]. */
@Serializable
data class LevelFilter(
    val mode: LevelMatchMode,
    val level: Level,
) : WeighStation() {
    override fun contains(logEvent: Box): Boolean = mode.checkMatch(level.intLevel, logEvent.level.intLevel)
}

/** Filter [Box]es by their [Box.timestamp] using the given comparison [mode]. */
@Serializable
data class TimeFilter(
    val mode: LevelMatchMode,
    val time: Long,
) : WeighStation() {
    override fun contains(logEvent: Box): Boolean = mode.checkMatch(time, logEvent.timestamp)
}
