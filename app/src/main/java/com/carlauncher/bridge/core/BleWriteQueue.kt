package com.carlauncher.bridge.core

class BleWriteQueue {
    data class QueueItem(
        val uuid: String,
        val data: ByteArray,
        val overwrite: Boolean = true
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is QueueItem) return false
            return uuid == other.uuid && data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = uuid.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    private val queue = mutableListOf<QueueItem>()

    fun add(newItem: QueueItem) {
        if (newItem.overwrite && queue.any { it.uuid == newItem.uuid }) {
            queue.removeAll { it.uuid == newItem.uuid }
        }
        queue.add(newItem)
    }

    fun pop(): QueueItem = queue.removeAt(0)

    val size: Int
        get() = queue.size

    fun clear() {
        queue.clear()
    }
}
