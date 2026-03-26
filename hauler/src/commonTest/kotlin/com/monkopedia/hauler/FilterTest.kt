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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterTest {

    private fun box(
        level: Level = Level.INFO,
        loggerName: String = "com.example.Test",
        message: String = "test message",
        timestamp: Long = 1000L,
        threadName: String? = "main",
    ) = Box(level, loggerName, message, timestamp, threadName)

    // --- LoggerNameFilter ---

    @Test
    fun loggerNameFilter_exact_matches() {
        val filter = LoggerNameFilter(LoggerMatchMode.EXACT, "com.example.Test")
        assertTrue(box() in filter)
    }

    @Test
    fun loggerNameFilter_exact_noMatch() {
        val filter = LoggerNameFilter(LoggerMatchMode.EXACT, "com.example.Other")
        assertFalse(box() in filter)
    }

    @Test
    fun loggerNameFilter_prefix_matches() {
        val filter = LoggerNameFilter(LoggerMatchMode.PREFIX, "com.example")
        assertTrue(box() in filter)
    }

    @Test
    fun loggerNameFilter_prefix_noMatch() {
        val filter = LoggerNameFilter(LoggerMatchMode.PREFIX, "org.other")
        assertFalse(box() in filter)
    }

    @Test
    fun loggerNameFilter_regex_matches() {
        val filter = LoggerNameFilter(LoggerMatchMode.REGEX, "com\\.example\\..*")
        assertTrue(box() in filter)
    }

    @Test
    fun loggerNameFilter_regex_noMatch() {
        val filter = LoggerNameFilter(LoggerMatchMode.REGEX, "org\\..*")
        assertFalse(box() in filter)
    }

    // --- ThreadNameFilter ---

    @Test
    fun threadNameFilter_exact_matches() {
        val filter = ThreadNameFilter(LoggerMatchMode.EXACT, "main")
        assertTrue(box() in filter)
    }

    @Test
    fun threadNameFilter_nullThreadName_returnsFalse() {
        val filter = ThreadNameFilter(LoggerMatchMode.EXACT, "main")
        assertFalse(box(threadName = null) in filter)
    }

    @Test
    fun threadNameFilter_prefix_matches() {
        val filter = ThreadNameFilter(LoggerMatchMode.PREFIX, "ma")
        assertTrue(box() in filter)
    }

    @Test
    fun threadNameFilter_regex_matches() {
        val filter = ThreadNameFilter(LoggerMatchMode.REGEX, "m.*n")
        assertTrue(box() in filter)
    }

    // --- MessageFilter ---

    @Test
    fun messageFilter_exact_matches() {
        val filter = MessageFilter(LoggerMatchMode.EXACT, "test message")
        assertTrue(box() in filter)
    }

    @Test
    fun messageFilter_exact_noMatch() {
        val filter = MessageFilter(LoggerMatchMode.EXACT, "other message")
        assertFalse(box() in filter)
    }

    @Test
    fun messageFilter_prefix_matches() {
        val filter = MessageFilter(LoggerMatchMode.PREFIX, "test")
        assertTrue(box() in filter)
    }

    @Test
    fun messageFilter_regex_matches() {
        val filter = MessageFilter(LoggerMatchMode.REGEX, "test.*")
        assertTrue(box() in filter)
    }

    // --- LevelFilter ---

    @Test
    fun levelFilter_eq_matches() {
        val filter = LevelFilter(LevelMatchMode.EQ, Level.INFO)
        assertTrue(box(level = Level.INFO) in filter)
    }

    @Test
    fun levelFilter_eq_noMatch() {
        val filter = LevelFilter(LevelMatchMode.EQ, Level.INFO)
        assertFalse(box(level = Level.WARN) in filter)
    }

    // Note: LevelFilter compares by intLevel (severity): TRACE(0) < DEBUG(10) < INFO(20) < WARN(30) < ERROR(40)

    @Test
    fun levelFilter_gt_matches() {
        val filter = LevelFilter(LevelMatchMode.GT, Level.INFO)
        assertTrue(box(level = Level.WARN) in filter)
        assertTrue(box(level = Level.ERROR) in filter)
    }

    @Test
    fun levelFilter_gt_noMatch() {
        val filter = LevelFilter(LevelMatchMode.GT, Level.INFO)
        assertFalse(box(level = Level.INFO) in filter)
        assertFalse(box(level = Level.DEBUG) in filter)
    }

    @Test
    fun levelFilter_lt_matches() {
        val filter = LevelFilter(LevelMatchMode.LT, Level.INFO)
        assertTrue(box(level = Level.DEBUG) in filter)
        assertTrue(box(level = Level.TRACE) in filter)
    }

    @Test
    fun levelFilter_lt_noMatch() {
        val filter = LevelFilter(LevelMatchMode.LT, Level.INFO)
        assertFalse(box(level = Level.INFO) in filter)
        assertFalse(box(level = Level.WARN) in filter)
    }

    // --- TimeFilter ---

    @Test
    fun timeFilter_eq_matches() {
        val filter = TimeFilter(LevelMatchMode.EQ, 1000L)
        assertTrue(box(timestamp = 1000L) in filter)
    }

    @Test
    fun timeFilter_gt_matches() {
        val filter = TimeFilter(LevelMatchMode.GT, 1000L)
        assertTrue(box(timestamp = 2000L) in filter)
        assertFalse(box(timestamp = 1000L) in filter)
    }

    @Test
    fun timeFilter_lt_matches() {
        val filter = TimeFilter(LevelMatchMode.LT, 1000L)
        assertTrue(box(timestamp = 500L) in filter)
        assertFalse(box(timestamp = 1000L) in filter)
    }

    // --- Composite Filters ---

    @Test
    fun andFilter_bothTrue() {
        val filter = AndFilter(
            LevelFilter(LevelMatchMode.EQ, Level.INFO),
            LoggerNameFilter(LoggerMatchMode.PREFIX, "com.example"),
        )
        assertTrue(box() in filter)
    }

    @Test
    fun andFilter_oneFalse() {
        val filter = AndFilter(
            LevelFilter(LevelMatchMode.EQ, Level.ERROR),
            LoggerNameFilter(LoggerMatchMode.PREFIX, "com.example"),
        )
        assertFalse(box() in filter)
    }

    @Test
    fun orFilter_oneTrue() {
        val filter = OrFilter(
            LevelFilter(LevelMatchMode.EQ, Level.ERROR),
            LoggerNameFilter(LoggerMatchMode.PREFIX, "com.example"),
        )
        assertTrue(box() in filter)
    }

    @Test
    fun orFilter_bothFalse() {
        val filter = OrFilter(
            LevelFilter(LevelMatchMode.EQ, Level.ERROR),
            LoggerNameFilter(LoggerMatchMode.EXACT, "org.other"),
        )
        assertFalse(box() in filter)
    }

    @Test
    fun notFilter_invertsTrueToFalse() {
        val filter = NotFilter(LevelFilter(LevelMatchMode.EQ, Level.INFO))
        assertFalse(box() in filter)
    }

    @Test
    fun notFilter_invertsFalseToTrue() {
        val filter = NotFilter(LevelFilter(LevelMatchMode.EQ, Level.ERROR))
        assertTrue(box() in filter)
    }

    @Test
    fun doubleNot_isIdentity() {
        val inner = LevelFilter(LevelMatchMode.EQ, Level.INFO)
        val filter = NotFilter(NotFilter(inner))
        assertTrue(box() in filter)
    }

    @Test
    fun deepComposition() {
        // (level > INFO by severity) AND (logger starts with "com" OR message matches ".*error.*")
        val filter = AndFilter(
            LevelFilter(LevelMatchMode.GT, Level.INFO),
            OrFilter(
                LoggerNameFilter(LoggerMatchMode.PREFIX, "com"),
                MessageFilter(LoggerMatchMode.REGEX, ".*error.*"),
            ),
        )
        assertTrue(box(level = Level.WARN) in filter)
        assertFalse(box(level = Level.DEBUG) in filter)
        assertTrue(box(level = Level.ERROR, loggerName = "org.other", message = "an error occurred") in filter)
    }

    // --- Regex edge cases ---

    @Test
    fun regex_emptyPattern() {
        val filter = LoggerNameFilter(LoggerMatchMode.REGEX, "")
        assertTrue(box(loggerName = "") in filter)
        assertFalse(box(loggerName = "anything") in filter)
    }

    @Test
    fun regex_specialCharacters() {
        val filter = MessageFilter(LoggerMatchMode.REGEX, "hello\\?world")
        assertTrue(box(message = "hello?world") in filter)
        assertFalse(box(message = "helloXworld") in filter)
    }

    @Test
    fun regex_cachingReturnsSameResult() {
        val filter = LoggerNameFilter(LoggerMatchMode.REGEX, "com\\..*")
        // Call multiple times to exercise cache
        assertTrue(box() in filter)
        assertTrue(box() in filter)
        assertTrue(box() in filter)
        assertFalse(box(loggerName = "org.other") in filter)
    }
}
