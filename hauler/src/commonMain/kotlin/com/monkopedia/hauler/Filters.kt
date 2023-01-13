package com.monkopedia.hauler

import kotlinx.serialization.Serializable

/**
 * Monitors truck contents, regulates what gets through.
 */
@Serializable
sealed class WeighStation {
    abstract operator fun contains(logEvent: Box): Boolean
}

@Serializable
data class AndFilter(val first: WeighStation, val second: WeighStation) : WeighStation() {
    override fun contains(logEvent: Box): Boolean {
        return logEvent in first && logEvent in second
    }
}

@Serializable
data class OrFilter(val first: WeighStation, val second: WeighStation) : WeighStation() {
    override fun contains(logEvent: Box): Boolean {
        return logEvent in first || logEvent in second
    }
}

@Serializable
data class NotFilter(val base: WeighStation) : WeighStation() {
    override fun contains(logEvent: Box): Boolean {
        return logEvent !in base
    }
}

enum class LoggerMatchMode {
    EXACT {
        override fun checkMatch(query: String, actual: String): Boolean = query == actual
    },
    PREFIX {
        override fun checkMatch(query: String, actual: String): Boolean = actual.startsWith(query)
    },
    REGEX {
        override fun checkMatch(query: String, actual: String): Boolean =
            Regex(query).matches(actual)
    };

    abstract fun checkMatch(query: String, actual: String): Boolean
}

@Serializable
data class LoggerNameFilter(val matchMode: LoggerMatchMode, val query: String) : WeighStation() {
    override fun contains(logEvent: Box): Boolean {
        return matchMode.checkMatch(query, logEvent.loggerName)
    }
}

@Serializable
data class ThreadNameFilter(val matchMode: LoggerMatchMode, val query: String) : WeighStation() {
    override fun contains(logEvent: Box): Boolean {
        return matchMode.checkMatch(query, logEvent.threadName ?: return false)
    }
}

@Serializable
data class MessageFilter(val matchMode: LoggerMatchMode, val query: String) : WeighStation() {
    override fun contains(logEvent: Box): Boolean {
        return matchMode.checkMatch(query, logEvent.message)
    }
}

enum class LevelMatchMode {
    EQ {
        override fun <T : Comparable<T>> checkMatch(query: T, actual: T): Boolean {
            return query.compareTo(actual) == 0
        }
    },
    LT {
        override fun <T : Comparable<T>> checkMatch(query: T, actual: T): Boolean {
            return actual < query
        }
    },
    GT {
        override fun <T : Comparable<T>> checkMatch(query: T, actual: T): Boolean {
            return actual > query
        }
    };

    abstract fun <T: Comparable<T>>checkMatch(query: T, actual: T): Boolean
}

@Serializable
data class LevelFilter(val mode: LevelMatchMode, val level: Level) : WeighStation() {
    override fun contains(logEvent: Box): Boolean {
        return mode.checkMatch(level, logEvent.level)
    }
}

@Serializable
data class TimeFilter(val mode: LevelMatchMode, val time: Long) : WeighStation() {
    override fun contains(logEvent: Box): Boolean {
        return mode.checkMatch(time, logEvent.timestamp)
    }
}
