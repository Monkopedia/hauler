package com.monkopedia.hauler

actual fun loggingName(): String? {
    return CallSign.threadLoggingName ?: Thread.currentThread().name
}
