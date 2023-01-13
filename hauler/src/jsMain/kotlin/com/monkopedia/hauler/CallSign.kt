package com.monkopedia.hauler

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

actual class CallSign actual constructor(val name: String) : CoroutineContext.Element {
    actual companion object Key : CoroutineContext.Key<CallSign> {
        actual suspend fun loggingName(): String? {
            return coroutineContext[Key]?.name
        }
    }

    override val key: CoroutineContext.Key<CallSign>
        get() = Key
}