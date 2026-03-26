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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val TRACE_INT = 0
const val DEBUG_INT = 10
const val INFO_INT = 20
const val WARN_INT = 30
const val ERROR_INT = 40

@Serializable
enum class Level(
    val intLevel: Int,
) {
    ERROR(ERROR_INT),
    WARN(WARN_INT),
    INFO(INFO_INT),
    DEBUG(DEBUG_INT),
    TRACE(TRACE_INT),
    ;

    companion object {
        fun Int.asLevel(): Level =
            when (this) {
                TRACE_INT -> TRACE
                DEBUG_INT -> DEBUG
                INFO_INT -> INFO
                WARN_INT -> WARN
                ERROR_INT -> ERROR
                else -> throw IllegalArgumentException("Level integer [$this] not recognized.")
            }
    }
}

/**
 * Holds one message and some contents for shipping
 */
@Serializable
data class Box(
    val level: Level,
    val loggerName: String,
    val message: String,
    val timestamp: Long,
    val threadName: String?,
    val metadata: Map<String, String>? = null,
)

/**
 * A [Box] all packed up for transit
 */
@Serializable
data class Package(
    @SerialName("l")
    val intLevel: Int,
    @SerialName("t")
    val loggerName: Int,
    @SerialName("m")
    val message: String,
    @SerialName("w")
    val timestamp: Long,
    @SerialName("i")
    val threadName: Int?,
    @SerialName("d")
    val metadata: Map<String, String>? = null,
)

/**
 * A bunch of [Package]s all lined up on a palette for easy transit.
 */
@Serializable
data class Palette(
    @SerialName("n")
    val loggerNames: List<String>,
    @SerialName("t")
    val threadNames: List<String> = emptyList(),
    @SerialName("m")
    val messages: List<Package>,
)
