package com.monkopedia.hauler

/**
 * A place that can have [Box]s put in it, but they don't go anywhere.
 */
object Container : Hauler {
    override suspend fun emit(value: Box) {
    }
}

/**
 * A truck that can transport things, but they are sitting out in the open.
 */
val Flatbed: Display = Display { line: String ->
    println(line)
}
