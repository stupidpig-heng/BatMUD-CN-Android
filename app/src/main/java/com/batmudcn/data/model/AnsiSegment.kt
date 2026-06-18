package com.batmudcn.data.model

/**
 * A segment of the byte stream split by ANSI SGR boundaries.
 * Mirrors (is_ansi, data) tuples in Python mud_client.py _split_raw_by_ansi.
 */
data class AnsiSegment(
    val isAnsi: Boolean,        // true = ANSI escape sequence, false = text
    val data: ByteArray,        // raw bytes of this segment
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnsiSegment) return false
        return isAnsi == other.isAnsi && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        return isAnsi.hashCode() * 31 + data.contentHashCode()
    }
}
