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

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationTest {

    private val json = Json { encodeDefaults = true }
    private val jsonNoDefaults = Json { encodeDefaults = false }

    // --- Box ---

    @Test
    fun box_roundtrip() {
        val box = Box(Level.WARN, "com.example", "hello", 12345L, "main")
        val encoded = json.encodeToString(Box.serializer(), box)
        val decoded = json.decodeFromString(Box.serializer(), encoded)
        assertEquals(box, decoded)
    }

    @Test
    fun box_nullThreadName_roundtrip() {
        val box = Box(Level.INFO, "logger", "msg", 0L, null)
        val encoded = json.encodeToString(Box.serializer(), box)
        val decoded = json.decodeFromString(Box.serializer(), encoded)
        assertEquals(box, decoded)
    }

    @Test
    fun box_withMetadata_roundtrip() {
        val box = Box(Level.ERROR, "logger", "msg", 99L, "t1", mapOf("k" to "v"))
        val encoded = json.encodeToString(Box.serializer(), box)
        val decoded = json.decodeFromString(Box.serializer(), encoded)
        assertEquals(box, decoded)
    }

    @Test
    fun box_nullMetadata_omittedByDefault() {
        val box = Box(Level.INFO, "logger", "msg", 0L, "main")
        val encoded = jsonNoDefaults.encodeToString(Box.serializer(), box)
        val decoded = jsonNoDefaults.decodeFromString(Box.serializer(), encoded)
        assertEquals(box, decoded)
        assertEquals(null, decoded.metadata)
    }

    // --- Package ---

    @Test
    fun package_roundtrip() {
        val pkg = Package(20, 0, "message", 1000L, 1)
        val encoded = json.encodeToString(Package.serializer(), pkg)
        val decoded = json.decodeFromString(Package.serializer(), encoded)
        assertEquals(pkg, decoded)
    }

    @Test
    fun package_nullFields_roundtrip() {
        val pkg = Package(10, 0, "msg", 0L, null, null)
        val encoded = json.encodeToString(Package.serializer(), pkg)
        val decoded = json.decodeFromString(Package.serializer(), encoded)
        assertEquals(pkg, decoded)
    }

    @Test
    fun package_withMetadata_roundtrip() {
        val pkg = Package(40, 2, "msg", 100L, 0, mapOf("a" to "b"))
        val encoded = json.encodeToString(Package.serializer(), pkg)
        val decoded = json.decodeFromString(Package.serializer(), encoded)
        assertEquals(pkg, decoded)
    }

    @Test
    fun package_usesSerialNames() {
        val pkg = Package(20, 0, "msg", 100L, 1)
        val encoded = json.encodeToString(Package.serializer(), pkg)
        // Verify short serial names are used
        assertEquals(true, encoded.contains("\"l\":"))
        assertEquals(true, encoded.contains("\"t\":"))
        assertEquals(true, encoded.contains("\"m\":"))
        assertEquals(true, encoded.contains("\"w\":"))
        assertEquals(true, encoded.contains("\"i\":"))
    }

    // --- Palette ---

    @Test
    fun palette_roundtrip() {
        val palette = Palette(
            loggerNames = listOf("A", "B"),
            threadNames = listOf("main"),
            messages = listOf(
                Package(20, 0, "msg1", 100L, 0),
                Package(30, 1, "msg2", 200L, 0),
            ),
        )
        val encoded = json.encodeToString(Palette.serializer(), palette)
        val decoded = json.decodeFromString(Palette.serializer(), encoded)
        assertEquals(palette, decoded)
    }

    @Test
    fun palette_emptyThreadNames_defaultOnDecode() {
        val palette = Palette(
            loggerNames = listOf("A"),
            messages = listOf(Package(20, 0, "msg", 100L, null)),
        )
        val encoded = jsonNoDefaults.encodeToString(Palette.serializer(), palette)
        val decoded = jsonNoDefaults.decodeFromString(Palette.serializer(), encoded)
        assertEquals(palette, decoded)
    }

    // --- Full pack/unpack via JSON ---

    @Test
    fun palette_jsonRoundtrip_matchesPackUnpack() {
        val boxes = listOf(
            Box(Level.INFO, "com.example.A", "hello", 1000L, "main"),
            Box(Level.ERROR, "com.example.B", "fail", 2000L, "worker"),
        )
        val palette = boxes.pack()
        val encoded = json.encodeToString(Palette.serializer(), palette)
        val decoded = json.decodeFromString(Palette.serializer(), encoded)
        assertEquals(boxes, decoded.unpack())
    }

    // --- WeighStation filters ---

    @Test
    fun levelFilter_roundtrip() {
        val filter: WeighStation = LevelFilter(LevelMatchMode.GT, Level.INFO)
        val encoded = json.encodeToString(WeighStation.serializer(), filter)
        val decoded = json.decodeFromString(WeighStation.serializer(), encoded)
        assertEquals(filter, decoded)
    }

    @Test
    fun loggerNameFilter_roundtrip() {
        val filter: WeighStation = LoggerNameFilter(LoggerMatchMode.PREFIX, "com.example")
        val encoded = json.encodeToString(WeighStation.serializer(), filter)
        val decoded = json.decodeFromString(WeighStation.serializer(), encoded)
        assertEquals(filter, decoded)
    }

    @Test
    fun threadNameFilter_roundtrip() {
        val filter: WeighStation = ThreadNameFilter(LoggerMatchMode.EXACT, "main")
        val encoded = json.encodeToString(WeighStation.serializer(), filter)
        val decoded = json.decodeFromString(WeighStation.serializer(), encoded)
        assertEquals(filter, decoded)
    }

    @Test
    fun messageFilter_roundtrip() {
        val filter: WeighStation = MessageFilter(LoggerMatchMode.REGEX, ".*error.*")
        val encoded = json.encodeToString(WeighStation.serializer(), filter)
        val decoded = json.decodeFromString(WeighStation.serializer(), encoded)
        assertEquals(filter, decoded)
    }

    @Test
    fun timeFilter_roundtrip() {
        val filter: WeighStation = TimeFilter(LevelMatchMode.LT, 9999L)
        val encoded = json.encodeToString(WeighStation.serializer(), filter)
        val decoded = json.decodeFromString(WeighStation.serializer(), encoded)
        assertEquals(filter, decoded)
    }

    @Test
    fun andFilter_roundtrip() {
        val filter: WeighStation = AndFilter(
            LevelFilter(LevelMatchMode.GT, Level.DEBUG),
            LoggerNameFilter(LoggerMatchMode.PREFIX, "com"),
        )
        val encoded = json.encodeToString(WeighStation.serializer(), filter)
        val decoded = json.decodeFromString(WeighStation.serializer(), encoded)
        assertEquals(filter, decoded)
    }

    @Test
    fun orFilter_roundtrip() {
        val filter: WeighStation = OrFilter(
            LevelFilter(LevelMatchMode.EQ, Level.ERROR),
            MessageFilter(LoggerMatchMode.EXACT, "critical"),
        )
        val encoded = json.encodeToString(WeighStation.serializer(), filter)
        val decoded = json.decodeFromString(WeighStation.serializer(), encoded)
        assertEquals(filter, decoded)
    }

    @Test
    fun notFilter_roundtrip() {
        val filter: WeighStation = NotFilter(LevelFilter(LevelMatchMode.LT, Level.WARN))
        val encoded = json.encodeToString(WeighStation.serializer(), filter)
        val decoded = json.decodeFromString(WeighStation.serializer(), encoded)
        assertEquals(filter, decoded)
    }

    @Test
    fun nestedFilter_roundtrip() {
        val filter: WeighStation = AndFilter(
            OrFilter(
                LevelFilter(LevelMatchMode.GT, Level.INFO),
                LoggerNameFilter(LoggerMatchMode.PREFIX, "com.example"),
            ),
            NotFilter(MessageFilter(LoggerMatchMode.REGEX, ".*ignore.*")),
        )
        val encoded = json.encodeToString(WeighStation.serializer(), filter)
        val decoded = json.decodeFromString(WeighStation.serializer(), encoded)
        assertEquals(filter, decoded)
    }
}
