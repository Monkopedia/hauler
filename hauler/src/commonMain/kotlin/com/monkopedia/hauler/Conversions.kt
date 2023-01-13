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

fun List<Box>.pack(): Palette {
    val tags = buildSet {
        this@pack.forEach {
            add(it.loggerName)
        }
    }.toList()
    val threads = buildSet {
        this@pack.forEach {
            it.threadName?.let(::add)
        }
    }.toList()
    return Palette(
        tags,
        threads,
        this@pack.map {
            Package(
                it.level.intLevel,
                tags.indexOf(it.loggerName),
                it.message,
                it.timestamp,
                it.threadName?.let(threads::indexOf)
            )
        }
    )
}

fun Deliveries.pack(deliveryRates: DeliveryRates = DeliveryRates()): Flow<Palette> {
    val logPacker = LogPacker()
    return merge(
        this,
        UnitFlow.onEach { delay(deliveryRates.defaultPaletteInterval) }
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

fun Palette.unpack(): List<Box> {
    return messages.map {
        unpack(it)
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun Palette.unpack(event: Package) = Box(
    event.intLevel.asLevel(),
    loggerNames[event.loggerName],
    event.message,
    event.timestamp,
    event.threadName?.let(threadNames::get)
)

inline fun Palette.forEach(onLog: (Box) -> Unit) {
    messages.forEach {
        onLog(unpack(it))
    }
}

fun Flow<Palette>.unpack(): Deliveries {
    return transform {
        it.forEach {
            emit(it)
        }
    }
}

class LogPacker {
    private val tagsLists = listOf(mutableListOf<String>(), mutableListOf())
    private val threadsLists = listOf(mutableListOf<String>(), mutableListOf())
    private val messagesLists = listOf(mutableListOf<Package>(), mutableListOf())
    private var currentIndex = 0
    private val tagsList
        get() = tagsLists[currentIndex]
    private val threadsList
        get() = threadsLists[currentIndex]
    private val messagesList
        get() = messagesLists[currentIndex]
    private val lock = Mutex()

    val size: Int
        get() = messagesList.size

    private fun swapBuffers() {
        currentIndex = 1 - currentIndex
        tagsList.clear()
        threadsList.clear()
        messagesList.clear()
    }

    suspend fun dumpLogs(): Palette = lock.withLock {
        Palette(
            tagsList,
            threadsList,
            messagesList
        ).also {
            swapBuffers()
        }
    }

    suspend fun log(logEvent: Box): Unit = lock.withLock {
        messagesList.add(
            Package(
                logEvent.level.intLevel,
                tagsList.indexOf(logEvent.loggerName).takeIf { it >= 0 }
                    ?: tagsList.size.also { tagsList.add(logEvent.loggerName) },
                logEvent.message,
                logEvent.timestamp,
                logEvent.threadName?.let { threadName ->
                    threadsList.indexOf(threadName).takeIf { it >= 0 }
                        ?: threadsList.size.also { threadsList.add(threadName) }
                }
            )
        )
    }
}
