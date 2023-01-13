package com.monkopedia.hauler

import kotlinx.coroutines.flow.flow

val UnitFlow = flow {
    while (true) emit(Unit)
}
