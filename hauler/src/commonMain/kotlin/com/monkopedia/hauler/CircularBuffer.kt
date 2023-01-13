package com.monkopedia.hauler

/**
 * Circular buffer, implemented primarily for behavior over performonce.
 */
class CircularBuffer<T>(private val size: Int) {
    private var nextIndex = 0
    private val list = mutableListOf<T>()

    fun add(item: T) {
        if (list.size < size) {
            list.add(item)
        } else {
            list[nextIndex++] = item
            if (nextIndex == list.size) {
                nextIndex = 0
            }
        }
    }

    fun toListAndClear(): List<T> {
        return (if (nextIndex == 0) list.toList() else splitList())
            .also { list.clear() }
    }

    private fun splitList(): List<T> {
        val split = size - nextIndex
        return List(size) {
            if (it < split) {
                list[it + nextIndex]
            } else {
                list[it - split]
            }
        }
    }
}
