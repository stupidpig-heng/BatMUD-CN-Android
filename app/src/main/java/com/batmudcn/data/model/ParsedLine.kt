package com.batmudcn.data.model

/**
 * A parsed line from the Telnet byte stream.
 * Mirrors ParsedLine NamedTuple in Python parser.py.
 */
data class ParsedLine(
    val text: String,                       // cleaned text (no ANSI, no line endings)
    val rawBytes: ByteArray,                // original bytes including ANSI + line endings
    val ansiSegments: List<String> = emptyList(),  // raw ANSI code strings found
    val isPrompt: Boolean = false,          // is this a status prompt line?
    val isGmcp: Boolean = false,            // is this GMCP/MSDP data?
    val isTelnet: Boolean = false,          // is this telnet negotiation?
    val skipTranslation: Boolean = false,   // should translation be skipped?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParsedLine) return false
        return text == other.text && rawBytes.contentEquals(other.rawBytes) &&
                ansiSegments == other.ansiSegments && isPrompt == other.isPrompt &&
                isGmcp == other.isGmcp && isTelnet == other.isTelnet &&
                skipTranslation == other.skipTranslation
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        result = 31 * result + ansiSegments.hashCode()
        result = 31 * result + isPrompt.hashCode()
        result = 31 * result + isGmcp.hashCode()
        result = 31 * result + isTelnet.hashCode()
        result = 31 * result + skipTranslation.hashCode()
        return result
    }
}
