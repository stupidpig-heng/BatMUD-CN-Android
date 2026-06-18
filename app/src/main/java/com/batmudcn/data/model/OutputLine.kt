package com.batmudcn.data.model

/**
 * A line ready for display in the terminal UI.
 * Contains the rendered text and optional debug info.
 */
data class OutputLine(
    val text: String,                       // plain text (for echo/debug)
    val annotatedHtml: String = "",         // HTML-like annotated string (ANSI→HTML)
    val isEcho: Boolean = false,            // is this a locally echoed command?
    val debug: DebugInfo? = null,           // debug metadata
)

data class DebugInfo(
    val mode: String = "",                  // "translate" or "passthrough"
    val ansiCount: Int = 0,
    val segmentCount: Int = 0,
    val translatedSegs: Int = 0,
    val rawHex: String = "",
    val text: String = "",
    val html: String = "",
    val skip: String = "",                  // skip reason
    val transMap: Map<String, String> = emptyMap(),
)
