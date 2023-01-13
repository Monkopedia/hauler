package com.monkopedia.hauler

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

actual class CallSign actual constructor(val name: String) : ThreadContextElement<String?>, CoroutineContext.Element {
    actual companion object Key : CoroutineContext.Key<CallSign> {
        private val currentName = ThreadLocal<String>()

        val threadLoggingName: String?
            get() = currentName.get()

        actual suspend fun loggingName(): String? {
            return currentName.get() ?: coroutineContext[Key]?.name
        }
    }

    override val key: CoroutineContext.Key<CallSign>
        get() = Key

    override fun updateThreadContext(context: CoroutineContext): String? {
        return currentName.get().also {
            currentName.set(name)
        }
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: String?) {
        currentName.set(oldState)
    }
}
