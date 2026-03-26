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

import com.monkopedia.hauler.Level.Companion.asLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Compress a list of [Box]es into a [Palette] for efficient transport, deduplicating logger and thread names. */
fun List<Box>.pack(): Palette {
    val tagsIndex = hashMapOf<String, Int>()
    val tagsList = mutableListOf<String>()
    val threadsIndex = hashMapOf<String, Int>()
    val threadsList = mutableListOf<String>()
    val packages = ArrayList<Package>(size)
    for (box in this) {
        val tagIdx = tagsIndex.getOrPut(box.loggerName) {
            tagsList.size.also { tagsList.add(box.loggerName) }
        }
        val threadIdx = box.threadName?.let { name ->
            threadsIndex.getOrPut(name) {
                threadsList.size.also { threadsList.add(name) }
            }
        }
        packages.add(
            Package(box.level.intLevel, tagIdx, box.message, box.timestamp, threadIdx, box.metadata),
        )
    }
    return Palette(tagsList, threadsList, packages)
}

/** Batch a stream of [Box]es into [Palette]s, flushing by size or time interval. */
fun Deliveries.pack(deliveryRates: DeliveryRates = DeliveryRates()): Flow<Palette> {
    val logPacker = LogPacker()
    return merge(
        this,
        UnitFlow.onEach { delay(deliveryRates.defaultPaletteInterval) },
    ).transform { v ->
        if (v is Box) {
            logPacker.log(v)
            if (logPacker.size >= deliveryRates.defaultPaletteSize) {
                emit(logPacker.dumpLogs())
            }
        } else {
            emit(logPacker.dumpLogs())
        }
    }
}

/** Expand this [Palette] back into individual [Box]es. Inverse of [List.pack]. */
fun Palette.unpack(): List<Box> =
    messages.map {
        unpack(it)
    }

fun Palette.unpack(event: Package) =
    Box(
        event.intLevel.asLevel(),
        loggerNames[event.loggerName],
        event.message,
        event.timestamp,
        event.threadName?.let(threadNames::get),
        event.metadata,
    )

/** Iterate over unpacked [Box]es without allocating an intermediate list. */
inline fun Palette.forEach(onLog: (Box) -> Unit) {
    messages.forEach {
        onLog(unpack(it))
    }
}

/** Expand a stream of [Palette]s into individual [Box] deliveries. */
fun Flow<Palette>.unpack(): Deliveries =
    transform {
        it.forEach {
            emit(it)
        }
    }

/**
 * Streaming packer that incrementally builds a [Palette] from individual [Box] emissions.
 *
 * Uses double-buffering: two sets of lists/maps are maintained so that [dumpLogs] can
 * return the current batch as a [Palette] and immediately swap to the alternate buffer,
 * avoiding allocation of new collections on each flush. All operations are protected
 * by a [Mutex] for coroutine safety.
 */
internal class LogPacker {
    private val tagsLists = listOf(mutableListOf<String>(), mutableListOf())
    private val tagsIndexMaps = listOf(mutableMapOf<String, Int>(), mutableMapOf())
    private val threadsLists = listOf(mutableListOf<String>(), mutableListOf())
    private val threadsIndexMaps = listOf(mutableMapOf<String, Int>(), mutableMapOf())
    private val messagesLists = listOf(mutableListOf<Package>(), mutableListOf())
    private var currentIndex = 0
    private val tagsList
        get() = tagsLists[currentIndex]
    private val tagsIndexMap
        get() = tagsIndexMaps[currentIndex]
    private val threadsList
        get() = threadsLists[currentIndex]
    private val threadsIndexMap
        get() = threadsIndexMaps[currentIndex]
    private val messagesList
        get() = messagesLists[currentIndex]
    private val lock = Mutex()

    val size: Int
        get() = messagesList.size

    private fun swapBuffers() {
        currentIndex = 1 - currentIndex
        tagsList.clear()
        tagsIndexMap.clear()
        threadsList.clear()
        threadsIndexMap.clear()
        messagesList.clear()
    }

    suspend fun dumpLogs(): Palette =
        lock.withLock {
            Palette(
                tagsList.toList(),
                threadsList.toList(),
                messagesList.toList(),
            ).also {
                swapBuffers()
            }
        }

    suspend fun log(logEvent: Box): Unit =
        lock.withLock {
            messagesList.add(
                Package(
                    logEvent.level.intLevel,
                    tagsIndexMap.getOrPut(logEvent.loggerName) {
                        tagsList.size.also { tagsList.add(logEvent.loggerName) }
                    },
                    logEvent.message,
                    logEvent.timestamp,
                    logEvent.threadName?.let { threadName ->
                        threadsIndexMap.getOrPut(threadName) {
                            threadsList.size.also { threadsList.add(threadName) }
                        }
                    },
                    logEvent.metadata,
                ),
            )
        }
}
