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

@DslMarker
annotation class FilterDsl

@FilterDsl
class WeighStationBuilder internal constructor() {
    private val filters = mutableListOf<WeighStation>()

    fun level(mode: LevelMatchMode, level: Level) {
        filters.add(LevelFilter(mode, level))
    }

    fun logger(mode: LoggerMatchMode, query: String) {
        filters.add(LoggerNameFilter(mode, query))
    }

    fun thread(mode: LoggerMatchMode, query: String) {
        filters.add(ThreadNameFilter(mode, query))
    }

    fun message(mode: LoggerMatchMode, query: String) {
        filters.add(MessageFilter(mode, query))
    }

    fun time(mode: LevelMatchMode, timestamp: Long) {
        filters.add(TimeFilter(mode, timestamp))
    }

    fun not(block: WeighStationBuilder.() -> Unit) {
        filters.add(NotFilter(WeighStationBuilder().apply(block).build()))
    }

    fun and(block: WeighStationBuilder.() -> Unit) {
        filters.add(WeighStationBuilder().apply(block).build())
    }

    fun or(block: WeighStationBuilder.() -> Unit) {
        filters.add(WeighStationBuilder().apply(block).buildOr())
    }

    internal fun build(): WeighStation {
        require(filters.isNotEmpty()) { "WeighStation block must contain at least one filter" }
        return filters.reduce { acc, filter -> AndFilter(acc, filter) }
    }

    internal fun buildOr(): WeighStation {
        require(filters.isNotEmpty()) { "WeighStation block must contain at least one filter" }
        return filters.reduce { acc, filter -> OrFilter(acc, filter) }
    }
}

fun weighStation(block: WeighStationBuilder.() -> Unit): WeighStation =
    WeighStationBuilder().apply(block).build()
