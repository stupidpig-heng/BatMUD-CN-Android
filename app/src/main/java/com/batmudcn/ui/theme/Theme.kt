package com.batmudcn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TerminalColorScheme = darkColorScheme(
    primary = TerminalAccent,
    secondary = TerminalSuccess,
    background = TerminalBg,
    surface = TerminalBgLighter,
    onPrimary = TerminalBg,
    onSecondary = TerminalBg,
    onBackground = TerminalText,
    onSurface = TerminalText,
    error = TerminalError,
    onError = TerminalTextBright,
    outline = TerminalBorder,
)

@Composable
fun BatMudTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TerminalColorScheme,
        typography = TerminalTypography,
        content = content,
    )
}
