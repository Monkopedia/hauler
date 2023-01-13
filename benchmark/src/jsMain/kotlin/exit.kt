package com.monkopedia.hauler.benchmark

import com.monkopedia.hauler.Shipper
import process

actual fun exit(code: Int) {
    process.exit(code)
}

actual fun createPlatformShipper(
    exec: ConnectionSpec,
    lastShipper: Shipper?
): Shipper? = null