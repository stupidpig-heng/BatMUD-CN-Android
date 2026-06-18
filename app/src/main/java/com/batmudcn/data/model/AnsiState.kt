package com.batmudcn.data.model

/**
 * Represents the current ANSI SGR state at a point in the byte stream.
 * Mirrors the state dict in Python mud_client.py.
 */
data class AnsiState(
    val fg: Int = 37,           // foreground color code (30-37, 90-97)
    val bg: Int? = null,        // background color code (40-47, 100-107) or null
    val bold: Boolean = false,
    val underline: Boolean = false,
    val dim: Boolean = false,
    val blink: Boolean = false,
    val reverse: Boolean = false,
) {
    /** True when state is default (no color / no style) */
    val isDefault: Boolean
        get() = fg == 37 && bg == null && !bold && !underline && !dim && !blink && !reverse

    companion object {
        val DEFAULT = AnsiState()
    }
}
