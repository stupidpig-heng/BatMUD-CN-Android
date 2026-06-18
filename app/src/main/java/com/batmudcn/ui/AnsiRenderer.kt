package com.batmudcn.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.batmudcn.data.model.AnsiState
import com.batmudcn.engine.AnsiParser
import com.batmudcn.util.Constants

/**
 * Render ANSI-escaped text into Compose AnnotatedString.
 * Replaces the Python ansi_to_html + style.css pairing.
 */
object AnsiRenderer {

    // ANSI SGR code → Color mapping
    private val FG_MAP: Map<Int, Color> = Constants.FG_COLORS.mapValues { Color(it.value.toInt()) }
    private val BG_MAP: Map<Int, Color> = Constants.BG_COLORS.mapValues { Color(it.value.toInt()) }

    /**
     * Convert a raw byte array (with ANSI escape codes) to AnnotatedString.
     */
    fun render(raw: ByteArray): AnnotatedString {
        return buildAnnotatedString {
            val segments = AnsiParser.splitByAnsi(raw)
            var state = AnsiState.DEFAULT

            for ((isAnsi, data) in segments) {
                if (isAnsi) {
                    val params = AnsiParser.extractSgrParams(data)
                    state = AnsiParser.applySgrParams(params, state)
                } else {
                    val text = data.toString(Charsets.UTF_8)
                    val style = stateToSpanStyle(state)
                    if (style != SpanStyle()) {
                        pushStyle(style)
                        append(text)
                        pop()
                    } else {
                        append(text)
                    }
                }
            }
        }
    }

    /**
     * Render text string that may contain ANSI codes to AnnotatedString.
     */
    fun renderText(text: String): AnnotatedString {
        val raw = text.toByteArray(Charsets.UTF_8)
        // Check if contains ANSI
        return if (raw.contains(Constants.ESC)) {
            render(raw)
        } else {
            AnnotatedString(text)
        }
    }

    /**
     * Convert an AnsiState to Compose SpanStyle.
     * Mirrors CSS classes in Python style.css.
     */
    private fun stateToSpanStyle(state: AnsiState): SpanStyle {
        if (state.isDefault && state.fg == 37) {
            // Pure default — let parent style apply
            return SpanStyle()
        }

        return SpanStyle(
            color = FG_MAP[state.fg] ?: Color.Unspecified,
            background = state.bg?.let { BG_MAP[it] } ?: Color.Unspecified,
            fontWeight = if (state.bold) FontWeight.Bold else null,
            textDecoration = if (state.underline) TextDecoration.Underline else TextDecoration.None,
        )
    }

    /**
     * Parse ANSI text and return segments with their styles, for advanced rendering.
     */
    fun parseAnsiSegments(raw: ByteArray): List<AnsiStyledSegment> {
        val segments = AnsiParser.splitByAnsi(raw)
        val result = mutableListOf<AnsiStyledSegment>()
        var state = AnsiState.DEFAULT

        for ((isAnsi, data) in segments) {
            if (isAnsi) {
                val params = AnsiParser.extractSgrParams(data)
                state = AnsiParser.applySgrParams(params, state)
            } else {
                val text = data.toString(Charsets.UTF_8)
                result.add(AnsiStyledSegment(text, state))
            }
        }
        return result
    }

    data class AnsiStyledSegment(
        val text: String,
        val state: AnsiState,
    )
}
