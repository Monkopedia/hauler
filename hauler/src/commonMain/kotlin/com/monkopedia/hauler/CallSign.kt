package com.monkopedia.hauler

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.CoroutineContext.Key

expect class CallSign(name: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CallSign> {
        suspend fun loggingName(): String?
    }

    override val key: Key
}
