package com.batmudcn.engine

import android.util.Log
import com.batmudcn.data.model.AnsiSegment
import com.batmudcn.data.model.AnsiState
import com.batmudcn.util.Constants.ESC

/**
 * ANSI SGR escape sequence parser.
 * Splits byte streams by ANSI boundaries and tracks color state.
 *
 * Mirrors: Python mud_client.py — _SGR_CODE_RE, _apply_sgr_state,
 *          _state_is_default, _split_raw_by_ansi, _extract_primary_style
 */
object AnsiParser {

    private const val TAG = "AnsiParser"

    // Regex pattern for ANSI SGR sequences: ESC [ <params> m
    // We match on byte arrays for performance
    private val SGR_START = byteArrayOf(ESC, '['.code.toByte())

    /**
     * Split a raw byte array into (isAnsi, data) segments at ANSI SGR boundaries.
     * Mirrors Python _split_raw_by_ansi.
     */
    fun splitByAnsi(raw: ByteArray): List<AnsiSegment> {
        val segments = mutableListOf<AnsiSegment>()
        var pos = 0
        val n = raw.size

        while (pos < n) {
            // Look for ESC [ sequence
            if (raw[pos] == ESC && pos + 1 < n && raw[pos + 1] == '['.code.toByte()) {
                // Found ANSI start — save any preceding text
                if (pos > 0) {
                    // Find the start of this segment (pos is at ESC now)
                    // We need to find the last segment end
                }

                // Find the 'm' terminator
                var end = pos + 2
                while (end < n && raw[end] != 'm'.code.toByte()) {
                    end++
                }
                if (end < n) end++ // include the 'm'

                // Save preceding text
                val textStart = if (segments.isEmpty() || segments.last().isAnsi) pos else {
                    // Find where the last text segment started
                    -1 // will handle below
                }

                segments.add(AnsiSegment(true, raw.copyOfRange(pos, end)))

                pos = end
            } else {
                // Text byte — find how much text until next ESC or end
                val textStart = pos
                while (pos < n && !(raw[pos] == ESC && pos + 1 < n && raw[pos + 1] == '['.code.toByte())) {
                    pos++
                }
                if (pos > textStart) {
                    segments.add(AnsiSegment(false, raw.copyOfRange(textStart, pos)))
                }
                // pos is now at ESC (or n) — loop continues
            }
        }

        return segments
    }

    /**
     * Apply ANSI SGR parameters to a state object (mutable).
     * Mirrors Python _apply_sgr_state.
     */
    fun applySgrParams(paramsStr: String, state: AnsiState): AnsiState {
        if (paramsStr.isEmpty()) {
            return AnsiState.DEFAULT
        }

        var fg = state.fg
        var bg = state.bg
        var bold = state.bold
        var underline = state.underline
        var dim = state.dim
        var blink = state.blink
        var reverse = state.reverse

        val codes = paramsStr.split(";").mapNotNull { it.toIntOrNull() }
        for (c in codes) {
            when (c) {
                0 -> {
                    fg = 37; bg = null
                    bold = false; underline = false; dim = false; blink = false; reverse = false
                }
                1 -> bold = true
                2 -> dim = true
                4 -> underline = true
                5 -> blink = true
                7 -> reverse = true
                22 -> bold = false
                24 -> underline = false
                25 -> blink = false
                27 -> reverse = false
                in 30..37, in 90..97 -> fg = c
                39 -> fg = 37
                in 40..47, in 100..107 -> bg = c
                49 -> bg = null
            }
        }

        return AnsiState(fg, bg, bold, underline, dim, blink, reverse)
    }

    /**
     * Extract SGR parameter string (inside ESC[...m) from an ANSI sequence byte array.
     * e.g., bytes for "\x1b[1;32m" → "1;32"
     */
    fun extractSgrParams(ansiData: ByteArray): String {
        if (ansiData.size < 4) return ""
        // Skip ESC [
        val inner = ansiData.copyOfRange(2, ansiData.size - 1) // -1 to strip 'm'
        return inner.toString(Charsets.US_ASCII)
    }

    /**
     * Find all ANSI SGR sequences in raw bytes.
     * Returns list of (start, end) index pairs.
     */
    fun findSgrSequences(raw: ByteArray): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < raw.size - 2) {
            if (raw[i] == ESC && raw[i + 1] == '['.code.toByte()) {
                var end = i + 2
                while (end < raw.size && raw[end] != 'm'.code.toByte()) {
                    end++
                }
                if (end < raw.size) {
                    end++ // include 'm'
                    result.add(i to end)
                    i = end
                    continue
                }
            }
            i++
        }
        return result
    }

    /**
     * Check if a line has unbalanced ANSI (more openers than closers).
     * Mirrors Python _has_unbalanced_ansi.
     */
    fun hasUnbalancedAnsi(raw: ByteArray): Boolean {
        val sequences = findSgrSequences(raw)
        if (sequences.isEmpty()) return false

        var closers = 0
        var openers = 0

        for ((start, end) in sequences) {
            val params = raw.copyOfRange(start + 2, end - 1).toString(Charsets.US_ASCII)
            if (params.isEmpty() || params == "0") {
                closers++
            } else {
                openers++
            }
        }
        return openers > closers
    }

    /**
     * Extract the "primary" (dominant) style of a line by counting
     * how many bytes each ANSI state covers. Returns the state
     * that covers the most text bytes.
     * Mirrors Python _extract_primary_style.
     */
    fun extractPrimaryStyle(raw: ByteArray): AnsiState {
        val sgrMatches = findSgrSequences(raw)

        if (sgrMatches.isEmpty()) {
            return AnsiState.DEFAULT
        }

        data class StateKey(
            val fg: Int, val bg: Int?, val bold: Boolean,
            val underline: Boolean, val dim: Boolean, val blink: Boolean, val reverse: Boolean,
        )

        val stateBytes = mutableMapOf<StateKey, Int>()
        var curState = AnsiState.DEFAULT
        var pos = 0

        for ((start, end) in sgrMatches) {
            // Text before this ANSI code belongs to current state
            val textLen = start - pos
            if (textLen > 0) {
                val key = StateKey(curState.fg, curState.bg, curState.bold,
                    curState.underline, curState.dim, curState.blink, curState.reverse)
                stateBytes[key] = (stateBytes[key] ?: 0) + textLen
            }

            // Apply this ANSI code
            val paramsStr = raw.copyOfRange(start + 2, end - 1).toString(Charsets.US_ASCII)
            curState = applySgrParams(paramsStr, curState)
            pos = end
        }

        // Text after last ANSI code
        val remaining = raw.size - pos
        if (remaining > 0) {
            // Exclude trailing \r\n
            var actualRemaining = remaining
            if (raw.size >= 2 && raw[raw.size - 2] == '\r'.code.toByte() && raw[raw.size - 1] == '\n'.code.toByte()) {
                actualRemaining -= 2
            }
            if (actualRemaining > 0) {
                val key = StateKey(curState.fg, curState.bg, curState.bold,
                    curState.underline, curState.dim, curState.blink, curState.reverse)
                stateBytes[key] = (stateBytes[key] ?: 0) + actualRemaining
            }
        }

        if (stateBytes.isEmpty()) return AnsiState.DEFAULT

        // Find the state with most bytes
        val dominantKey = stateBytes.maxByOrNull { it.value }?.key ?: return AnsiState.DEFAULT

        return AnsiState(
            fg = dominantKey.fg,
            bg = dominantKey.bg,
            bold = dominantKey.bold,
            underline = dominantKey.underline,
            dim = dominantKey.dim,
            blink = dominantKey.blink,
            reverse = dominantKey.reverse,
        )
    }
}
