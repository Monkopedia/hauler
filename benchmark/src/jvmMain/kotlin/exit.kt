package com.monkopedia.hauler.benchmark

import com.monkopedia.hauler.Shipper
import kotlin.system.exitProcess

actual fun exit(code: Int) {
    exitProcess(code)
}

actual fun createPlatformShipper(
    exec: ConnectionSpec,
    lastShipper: Shipper?
): Shipper? = null