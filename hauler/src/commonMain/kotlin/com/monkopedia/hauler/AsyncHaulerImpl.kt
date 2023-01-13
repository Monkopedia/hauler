package com.monkopedia.hauler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AsyncHaulerImpl(private val hauler: Hauler, private val scope: CoroutineScope) : AsyncHauler {
    override fun emit(box: Box) {
        scope.launch {
            hauler.emit(box)
        }
    }
}
