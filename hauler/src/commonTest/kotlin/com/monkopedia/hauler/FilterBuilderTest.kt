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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FilterBuilderTest {

    private fun box(
        level: Level = Level.INFO,
        loggerName: String = "com.example.Test",
        message: String = "test message",
        timestamp: Long = 1000L,
        threadName: String? = "main",
    ) = Box(level, loggerName, message, timestamp, threadName)

    @Test
    fun singleFilter_passthrough() {
        val filter = weighStation {
            level(LevelMatchMode.EQ, Level.INFO)
        }
        assertIs<LevelFilter>(filter)
        assertTrue(box() in filter)
        assertFalse(box(level = Level.WARN) in filter)
    }

    @Test
    fun multipleFilters_implicitAnd() {
        val filter = weighStation {
            level(LevelMatchMode.GT, Level.DEBUG)
            logger(LoggerMatchMode.PREFIX, "com.example")
        }
        assertIs<AndFilter>(filter)
        assertTrue(box(level = Level.INFO) in filter)
        assertFalse(box(level = Level.TRACE) in filter)
        assertFalse(box(level = Level.INFO, loggerName = "org.other") in filter)
    }

    @Test
    fun orBlock() {
        val filter = weighStation {
            or {
                logger(LoggerMatchMode.PREFIX, "com.example")
                message(LoggerMatchMode.REGEX, ".*error.*")
            }
        }
        assertTrue(box() in filter)
        assertTrue(box(loggerName = "org.other", message = "an error occurred") in filter)
        assertFalse(box(loggerName = "org.other", message = "all good") in filter)
    }

    @Test
    fun notBlock() {
        val filter = weighStation {
            not {
                level(LevelMatchMode.EQ, Level.DEBUG)
            }
        }
        assertIs<NotFilter>(filter)
        assertTrue(box(level = Level.INFO) in filter)
        assertFalse(box(level = Level.DEBUG) in filter)
    }

    @Test
    fun nestedComposition() {
        val filter = weighStation {
            level(LevelMatchMode.GT, Level.INFO)
            or {
                logger(LoggerMatchMode.PREFIX, "com.example")
                message(LoggerMatchMode.REGEX, ".*error.*")
            }
        }
        // WARN + com.example -> true
        assertTrue(box(level = Level.WARN) in filter)
        // ERROR + message matches -> true
        assertTrue(box(level = Level.ERROR, loggerName = "org.other", message = "an error occurred") in filter)
        // DEBUG (not > INFO) -> false
        assertFalse(box(level = Level.DEBUG) in filter)
        // WARN + neither logger nor message match -> false
        assertFalse(box(level = Level.WARN, loggerName = "org.other", message = "all good") in filter)
    }

    @Test
    fun explicitAndBlock() {
        val filter = weighStation {
            and {
                level(LevelMatchMode.GT, Level.DEBUG)
                logger(LoggerMatchMode.PREFIX, "com")
            }
        }
        assertTrue(box(level = Level.INFO) in filter)
        assertFalse(box(level = Level.TRACE) in filter)
    }

    @Test
    fun emptyBlock_throws() {
        assertFailsWith<IllegalArgumentException> {
            weighStation { }
        }
    }

    @Test
    fun threadAndTimeFilters() {
        val filter = weighStation {
            thread(LoggerMatchMode.EXACT, "main")
            time(LevelMatchMode.GT, 500L)
        }
        assertTrue(box() in filter)
        assertFalse(box(threadName = "worker") in filter)
        assertFalse(box(timestamp = 100L) in filter)
    }
}
