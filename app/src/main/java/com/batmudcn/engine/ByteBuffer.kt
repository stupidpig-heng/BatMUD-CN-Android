package com.batmudcn.engine

/**
 * Simple resizable byte buffer for assembling incoming data chunks
 * before parsing into lines.
 */
class ByteBuffer(initialCapacity: Int = 4096) {
    private var buf = ByteArray(initialCapacity)
    var size: Int = 0
        private set

    fun append(data: ByteArray) {
        ensureCapacity(size + data.size)
        System.arraycopy(data, 0, buf, size, data.size)
        size += data.size
    }

    /** Return all buffered bytes and clear */
    fun consume(): ByteArray {
        val result = buf.copyOf(size)
        size = 0
        return result
    }

    /** Return buffered bytes up to (but not including) the delimiter, consuming them.
     *  Returns null if delimiter not found. */
    fun consumeUpTo(delimiter: Byte): ByteArray? {
        val idx = buf.indexOf(delimiter, 0, size)
        if (idx == -1) return null
        val result = buf.copyOfRange(0, idx)
        val remaining = size - (idx + 1)
        if (remaining > 0) {
            System.arraycopy(buf, idx + 1, buf, 0, remaining)
        }
        size = remaining
        return result
    }

    fun clear() {
        size = 0
    }

    private fun ensureCapacity(required: Int) {
        if (required > buf.size) {
            var newSize = buf.size * 2
            while (newSize < required) newSize *= 2
            buf = buf.copyOf(newSize)
        }
    }

    /** IndexOf within range */
    private fun ByteArray.indexOf(b: Byte, start: Int, end: Int): Int {
        for (i in start until end) {
            if (this[i] == b) return i
        }
        return -1
    }
}
