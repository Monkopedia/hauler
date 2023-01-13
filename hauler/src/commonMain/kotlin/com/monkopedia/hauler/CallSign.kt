package com.monkopedia.hauler

import kotlin.coroutines.CoroutineContext

expect class CallSign(name: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CallSign> {
        suspend fun loggingName(): String?
    }
}
