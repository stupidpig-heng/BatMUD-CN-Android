package com.batmudcn.engine

import android.util.Log
import com.batmudcn.data.model.AnsiSegment
import com.batmudcn.data.model.AnsiState
import com.batmudcn.data.model.ParsedLine
import com.batmudcn.translate.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Processes Telnet byte streams into parsed lines, handles
 * hard-break line merging, and orchestrates ANSI-aware translation.
 *
 * Mirrors: Python mud_client.py (MudClient._process_line,
 *          _merge_message_lines, _merge_line_list) +
 *          parser.py (TextParser)
 */
class LineProcessor(
    private val translator: Translator?,
    private val minChars: Int = 4,
) {
    companion object {
        private const val TAG = "LineProcessor"
    }

    // Cross-chunk pending message buffer
    private var pendingMsg: ParsedLine? = null

    // Partial line accumulator
    private val lineBuffer = ByteBuffer(4096)

    /**
     * Feed incoming raw bytes, process/translate each line, and emit via callback
     * as soon as each line is ready. This gives the UI a streaming feel — lines appear
     * one by one instead of all at once after the batch completes.
     */
    suspend fun feed(data: ByteArray, onLine: suspend (ProcessedLine) -> Unit) {
        lineBuffer.append(data)

        // Step 1: Parse raw bytes into ParsedLine objects
        val parsedLines = mutableListOf<ParsedLine>()
        while (true) {
            val line = lineBuffer.consumeUpTo('\n'.code.toByte()) ?: break
            // Strip trailing \r
            val cleanLine = if (line.isNotEmpty() && line.last() == '\r'.code.toByte()) {
                line.copyOf(line.size - 1)
            } else {
                line
            }

            if (cleanLine.isEmpty()) {
                parsedLines.add(ParsedLine(
                    text = "\n",
                    rawBytes = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()),
                    ansiSegments = emptyList(),
                    isPrompt = false,
                    isGmcp = false,
                    isTelnet = false,
                    skipTranslation = true,
                ))
                continue
            }

            parsedLines.add(parseLine(cleanLine))
        }

        // Step 2: Merge continuation lines BEFORE translation (so translator sees complete sentences)
        val merged = mergeParsedLines(parsedLines)

        // Step 3: Process/translate each merged line — emit immediately after each one
        for (parsed in merged) {
            if (parsed.text == "\n") {
                onLine(ProcessedLine(text = "\n", debug = emptyDebug()))
            } else {
                val processed = processLine(parsed)
                if (processed != null) {
                    onLine(processed)
                }
            }
        }
    }

    /**
     * Merge continuation lines BEFORE translation.
     * Mirrors Python MudClient._merge_message_lines:
     *   - Handles cross-chunk pending (ANSI-unbalanced at chunk boundary)
     *   - Detects continuation via: ANSI-unbalanced, space-indented, or sentence split
     *   - Cleans up ANSI boundary pairs during merge
     */
    private fun mergeParsedLines(lines: List<ParsedLine>): List<ParsedLine> {
        // Prepend cross-chunk pending line
        val allLines = if (pendingMsg != null) {
            val list = mutableListOf(pendingMsg!!)
            list.addAll(lines)
            pendingMsg = null
            list
        } else {
            lines
        }

        if (allLines.isEmpty()) return allLines

        val result = mutableListOf<ParsedLine>()
        var idx = 0

        while (idx < allLines.size) {
            val line = allLines[idx]

            // Skip telnet lines (passthrough)
            if (line.isTelnet) {
                result.add(line)
                idx++
                continue
            }

            // Skip prompt / GMCP — don't merge these
            if (line.isPrompt || line.isGmcp) {
                result.add(line)
                idx++
                continue
            }

            // Start a merge group
            val group = mutableListOf(line)
            idx++

            while (idx < allLines.size) {
                val nxt = allLines[idx]

                if (nxt.isTelnet) break
                if (nxt.isPrompt || nxt.isGmcp) break

                // Check if next is continuation of previous in group
                val prev = group.last()
                val isCont = (AnsiParser.hasUnbalancedAnsi(prev.rawBytes)
                        || isContinuationLine(nxt))

                if (isCont) {
                    group.add(nxt)
                    idx++
                } else {
                    break
                }
            }

            if (group.size > 1) {
                result.add(mergeLineGroup(group))
            } else {
                result.add(group[0])
            }
        }

        // Cross-chunk: buffer last line if ANSI-unbalanced (will prepend next chunk)
        if (result.isNotEmpty()) {
            val last = result.last()
            if (AnsiParser.hasUnbalancedAnsi(last.rawBytes)
                && !last.isPrompt && !last.isGmcp && !last.isTelnet) {
                pendingMsg = result.removeAt(result.size - 1)
            }
        }

        return result
    }

    /**
     * Check if a ParsedLine is a continuation (the "next" line after a previous one).
     * Mirrors Python MudClient._is_continuation_line.
     */
    private fun isContinuationLine(line: ParsedLine): Boolean {
        if (line.isTelnet || line.isPrompt || line.isGmcp) return false
        if (line.skipTranslation) return false

        val raw = line.rawBytes
        val rawNoCrlf = if (raw.size >= 2 && raw[raw.size - 2] == '\r'.code.toByte()
            && raw[raw.size - 1] == '\n'.code.toByte()) {
            raw.copyOf(raw.size - 2)
        } else raw

        // Pattern 1: Space-indented continuation (channel messages / wrapped text)
        if (rawNoCrlf.isNotEmpty() && rawNoCrlf[0] == ' '.code.toByte()) {
            val text = line.text.trimStart(' ')
            return text.isNotEmpty() && text.any { it.isLetter() && it.code < 128 }
        }

        // Pattern 2: ANSI SGR code + space → NPC dialogue continuation
        // e.g., "\x1b[33m 'Hello...'"
        val sgrMatch = Regex("\\x1b\\[[\\d;]*m").find(rawNoCrlf.toString(Charsets.US_ASCII))
        if (sgrMatch != null) {
            val afterAnsi = rawNoCrlf.copyOfRange(sgrMatch.range.last + 1, rawNoCrlf.size)
            if (afterAnsi.isNotEmpty() && afterAnsi[0] == ' '.code.toByte()) {
                return afterAnsi.size > 1
            }
        }

        return false
    }

    /**
     * Merge a group of continuation ParsedLines into one.
     * Mirrors Python MudClient._merge_line_list:
     *   - Strips ANSI boundary pairs (\x1b[0m at prev end + \x1b[SGR]m at next start)
     *   - Strips leading space from continuation lines
     *   - Joins with space
     */
    private fun mergeLineGroup(lines: List<ParsedLine>): ParsedLine {
        if (lines.size == 1) return lines[0]

        val mergedRaw = ByteArrayOutputStream()
        // First line: strip trailing \r\n
        var firstRaw = lines[0].rawBytes.trimCrLf()
        mergedRaw.write(firstRaw)

        for (i in 1 until lines.size) {
            var raw = lines[i].rawBytes.trimCrLf()

            // Strip ANSI boundary pair: prev ends with \x1b[0m, current starts with \x1b[SGR]m
            val mergedBytes = mergedRaw.toByteArray()
            val esc0m = byteArrayOf(0x1B, '['.code.toByte(), '0'.code.toByte(), 'm'.code.toByte())
            if (mergedBytes.endsWith(esc0m)) {
                // Check if current line starts with ANSI SGR
                if (raw.isNotEmpty() && raw[0] == 0x1B.toByte() && raw.size > 1 && raw[1] == '['.code.toByte()) {
                    // Find the 'm' terminator
                    var mPos = 2
                    while (mPos < raw.size && raw[mPos] != 'm'.code.toByte()) mPos++
                    if (mPos < raw.size) {
                        // Strip the \x1b[0m from merged and the leading \x1b[SGR]m from raw
                        mergedRaw.reset()
                        mergedRaw.write(mergedBytes, 0, mergedBytes.size - esc0m.size)
                        raw = raw.copyOfRange(mPos + 1, raw.size)
                    }
                }
            }

            // Strip leading space (indentation continuation)
            raw = raw.trimLeadingSpaces()

            mergedRaw.write(' '.code)
            mergedRaw.write(raw)
        }

        // Add back \r\n
        mergedRaw.write('\r'.code)
        mergedRaw.write('\n'.code)

        val finalBytes = mergedRaw.toByteArray()

        // Re-parse to get correct text + ANSI segments (feeds through parseLine)
        val reparsed = parseLine(finalBytes)
        return reparsed.copy(
            isPrompt = false,
            isGmcp = false,
            isTelnet = false,
        )
    }

    /**
     * Parse raw bytes of a single line into a ParsedLine.
     * Mirrors Python parser.py TextParser.
     */
    private fun parseLine(raw: ByteArray): ParsedLine {
        // Detect telnet data
        val isTelnet = raw.isNotEmpty() && raw[0] == TelnetConstants.IAC

        // Detect GMCP (starts with IAC SB GMCP or IAC SB MSSP)
        val isGmcp = isTelnet && raw.size > 3

        // Extract ANSI segments
        val ansiSegments = AnsiParser.findSgrSequences(raw)
        val ansiStrings = ansiSegments.map { (s, e) ->
            raw.copyOfRange(s, e).toString(Charsets.US_ASCII)
        }

        // Extract clean text (strip ANSI)
        val cleanText = stripAnsi(raw)

        // Detect prompt: "Hp:318/318 Sp:25/25 Ep:183/183 Exp:356 >"
        val isPrompt = isPromptLine(cleanText)

        // Decide skip translation
        val skipTranslation = isTelnet || isGmcp || isPrompt ||
                cleanText.trim().length < minChars

        return ParsedLine(
            text = cleanText,
            rawBytes = raw + byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()), // restore CRLF
            ansiSegments = ansiStrings,
            isPrompt = isPrompt,
            isGmcp = isGmcp,
            isTelnet = isTelnet,
            skipTranslation = skipTranslation,
        )
    }

    /**
     * Process a single parsed line: per-segment translation with ANSI state tracking.
     * Mirrors Python mud_client.py _process_line exactly:
     *   - Split raw bytes by ANSI SGR boundaries
     *   - Track ANSI state (fg/bg/bold/etc.) across segments
     *   - Default-color text → Chinese only
     *   - Styled/colored text → English(Chinese) so player knows the command word
     *   - Short segments / low-alpha → passthrough unchanged
     */
    private suspend fun processLine(line: ParsedLine): ProcessedLine? {
        val raw = line.rawBytes
        // Strip CRLF for processing
        val rawNoCrlf = if (raw.size >= 2 && raw[raw.size - 2] == '\r'.code.toByte() && raw[raw.size - 1] == '\n'.code.toByte()) {
            raw.copyOf(raw.size - 2)
        } else {
            raw
        }

        if (line.isTelnet) return null
        if (rawNoCrlf.isEmpty()) {
            return ProcessedLine(text = "\n", debug = emptyDebug(), isPrompt = line.isPrompt)
        }

        val debug = mutableMapOf<String, Any>(
            "raw_hex" to rawNoCrlf.joinToString(" ") { "%02x".format(it) },
            "text" to line.text,
            "ansi_count" to line.ansiSegments.size,
            "skip" to line.skipTranslation,
            "is_prompt" to line.isPrompt,
        )

        if (line.skipTranslation || translator == null) {
            // Passthrough: no translation, raw bytes through for AnsiRenderer
            debug["mode"] = "passthrough"
            return ProcessedLine(
                text = line.text,
                html = line.text,
                rawBytes = rawNoCrlf,
                debug = debug,
                isPrompt = line.isPrompt,
            )
        }

        // ---- Per-segment translation with ANSI state tracking ----
        // Mirrors Python _process_line steps 1-5 exactly
        debug["mode"] = "translate"

        // 1. Split raw bytes by ANSI SGR boundaries
        val segments = AnsiParser.splitByAnsi(rawNoCrlf)

        // 2. Collect text segments that need translation
        //    Skip ≤2 char segments and low-alpha-ratio segments (ASCII art / maps)
        data class TextToTranslate(val segIndex: Int, val text: String)
        val toTranslate = mutableListOf<TextToTranslate>()
        for ((idx, seg) in segments.withIndex()) {
            if (!seg.isAnsi) {
                val text = seg.data.toString(Charsets.UTF_8)
                val stripped = text.trimEnd('\r', '\n')
                val strippedClean = stripped.trim()
                if (strippedClean.isEmpty() || strippedClean.length <= 2) continue
                val alphaCount = strippedClean.count { it.isLetter() && it.code < 128 }
                if (alphaCount == 0) continue
                // Skip ASCII-art / map lines: < 40% alpha
                if (alphaCount < strippedClean.length * 0.4) continue
                toTranslate.add(TextToTranslate(idx, stripped))
            }
        }

        // 3. Translate each segment (with cache hits handled by Translator)
        val transMap = mutableMapOf<String, String>()
        for ((_, stripped) in toTranslate) {
            if (stripped !in transMap) {
                transMap[stripped] = try {
                    translator?.translate(stripped) ?: stripped
                } catch (e: Exception) {
                    Log.w(TAG, "Translate seg failed: ${e.message}")
                    stripped
                }
            }
        }

        debug["segments"] = segments.size
        debug["translated_segs"] = transMap.size
        debug["trans_map"] = transMap.entries.take(5).associate { (k, v) -> k to v }

        // 4. Reconstruct byte stream: track ANSI state, decide output per segment
        //    Mirrors Python step 4 exactly:
        //    - Default style (fg=37, no bg, no bold/underline/etc.) → Chinese only
        //    - Styled text (>2 chars, has alpha) → English(Chinese)
        //    - Short / non-alpha → passthrough
        var state = AnsiState.DEFAULT
        val out = ByteArrayOutputStream()

        for ((idx, seg) in segments.withIndex()) {
            if (seg.isAnsi) {
                out.write(seg.data)
                // Update ANSI state
                val params = AnsiParser.extractSgrParams(seg.data)
                state = AnsiParser.applySgrParams(params, state)
            } else {
                val text = seg.data.toString(Charsets.UTF_8)
                val stripped = text.trimEnd('\r', '\n')
                val trailing = text.substring(stripped.length)

                val translated = transMap[stripped] ?: stripped
                val hasStyle = !state.isDefault
                val hasAlpha = stripped.any { it.isLetter() && it.code < 128 }

                if (hasStyle && stripped.trim().isNotEmpty() && hasAlpha && stripped.trim().length > 2) {
                    // Styled text (>2 chars) → English(Chinese) so player sees item name
                    if (stripped != translated) {
                        out.write("$stripped($translated)".toByteArray(Charsets.UTF_8))
                    } else {
                        out.write(stripped.toByteArray(Charsets.UTF_8))
                    }
                } else {
                    // Default-color text → Chinese only for immersion
                    out.write(translated.toByteArray(Charsets.UTF_8))
                }
                out.write(trailing.toByteArray(Charsets.UTF_8))
            }
        }

        val translatedBytes = out.toByteArray()

        // 5. Build plain translated text for the html field (backward compat)
        val plainTranslated = buildString {
            for ((idx, seg) in segments.withIndex()) {
                if (seg.isAnsi) continue
                val text = seg.data.toString(Charsets.UTF_8)
                val stripped = text.trimEnd('\r', '\n')
                val trailing = text.substring(stripped.length)
                append(transMap[stripped] ?: stripped)
                append(trailing)
            }
        }

        return ProcessedLine(
            text = line.text,               // original clean text
            html = plainTranslated,          // plain translated text (no ANSI)
            rawBytes = translatedBytes,      // ANSI + translation for AnsiRenderer
            debug = debug,
            isPrompt = line.isPrompt,
        )
    }


    /**
     * Merge hard-break continuation lines.
     * Mirrors Python mud_client.py _merge_message_lines.
     */
    fun mergeMessageLines(lines: List<ProcessedLine>): List<ProcessedLine> {
        if (lines.isEmpty()) return lines

        val allLines = mutableListOf<ProcessedLine>()
        // Prepend pending message from previous chunk
        if (pendingMsg != null) {
            // pendingMsg is already a parsed line; we need to find its processed form
            pendingMsg = null
        }

        val result = mutableListOf<ProcessedLine>()
        var idx = 0

        while (idx < lines.size) {
            val line = lines[idx]

            // Check if this is a continuation of the previous line
            if (idx > 0) {
                val prev = result.lastOrNull()
                if (prev != null && isContinuation(prev, line)) {
                    // Merge with previous
                    result[result.size - 1] = mergeTwoLines(prev, line)
                    idx++
                    continue
                }
            }

            result.add(line)
            idx++
        }

        return result
    }

    private fun isContinuation(prev: ProcessedLine, next: ProcessedLine): Boolean {
        // Check ANSI unbalanced
        if (prev.rawBytes != null && AnsiParser.hasUnbalancedAnsi(prev.rawBytes)) {
            return true
        }
        // Check if next line starts with space (indentation continuation)
        val nextText = next.text
        if (nextText.startsWith(" ")) {
            val trimmed = nextText.trimStart(' ')
            return trimmed.isNotEmpty() && trimmed.any { it.isLetter() && it.code < 128 }
        }
        return false
    }

    private fun mergeTwoLines(first: ProcessedLine, second: ProcessedLine): ProcessedLine {
        val mergedText = first.text.trimEnd('\r', '\n') + " " + second.text.trimStart(' ', '\r', '\n')
        return ProcessedLine(
            text = mergedText,
            html = mergedText,
            rawBytes = null, // Merged lines don't keep raw bytes
            debug = first.debug,
        )
    }

    /**
     * Strip ANSI escape sequences from text.
     */
    private fun stripAnsi(raw: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < raw.size) {
            if (raw[i] == 0x1B.toByte() && i + 1 < raw.size && raw[i + 1] == '['.code.toByte()) {
                // Skip until 'm'
                i += 2
                while (i < raw.size && raw[i] != 'm'.code.toByte()) i++
                if (i < raw.size) i++ // skip 'm'
            } else {
                sb.append(raw[i].toInt().toChar())
                i++
            }
        }
        return sb.toString()
    }

    /**
     * Detect MUD prompt line.
     * Pattern: "Hp:318/318 Sp:25/25 Ep:183/183 Exp:356 >"
     */
    private fun isPromptLine(text: String): Boolean {
        val promptPattern = Regex("""^(Hp|Sp|Ep|Exp|Tnl|Party):\s*\d+""")
        return promptPattern.containsMatchIn(text.trim())
    }

    private fun emptyDebug() = mapOf<String, Any>()

    /** Flush any pending state (call on disconnect / reconnect) */
    fun reset() {
        pendingMsg = null
        lineBuffer.clear()
    }

    /** Clear cross-chunk pending and partial line buffer (call when player sends a command) */
    fun clearPending() {
        pendingMsg = null
        lineBuffer.clear()
    }
}

/**
 * Output of line processing — either plain text or HTML with ANSI,
 * plus optional debug metadata.
 */
data class ProcessedLine(
    val text: String,                           // plain text
    val html: String = "",                      // HTML output (or annotated text)
    val rawBytes: ByteArray? = null,            // raw bytes for ANSI rendering
    val debug: Map<String, Any> = emptyMap(),
    val isPrompt: Boolean = false,              // true if this is a status prompt (HP/SP/EP)
) {
    val isNewline: Boolean get() = text == "\n"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProcessedLine) return false
        return text == other.text && html == other.html &&
                (rawBytes?.contentEquals(other.rawBytes) ?: (other.rawBytes == null)) &&
                debug == other.debug && isPrompt == other.isPrompt
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + html.hashCode()
        result = 31 * result + (rawBytes?.contentHashCode() ?: 0)
        result = 31 * result + debug.hashCode()
        result = 31 * result + isPrompt.hashCode()
        return result
    }
}

/** Strip trailing \r\n from a byte array. */
private fun ByteArray.trimCrLf(): ByteArray {
    var len = size
    while (len > 0 && (this[len - 1] == '\n'.code.toByte() || this[len - 1] == '\r'.code.toByte())) {
        len--
    }
    return if (len == size) this else copyOf(len)
}

/** Strip leading space bytes. */
private fun ByteArray.trimLeadingSpaces(): ByteArray {
    var start = 0
    while (start < size && this[start] == ' '.code.toByte()) start++
    return if (start == 0) this else copyOfRange(start, size)
}

/** Check if this byte array ends with the given suffix. */
private fun ByteArray.endsWith(suffix: ByteArray): Boolean {
    if (size < suffix.size) return false
    for (i in suffix.indices) {
        if (this[size - suffix.size + i] != suffix[i]) return false
    }
    return true
}
