package com.batmudcn.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batmudcn.engine.OutputLine
import com.batmudcn.ui.AnsiRenderer
import com.batmudcn.ui.component.CommandInput
import com.batmudcn.ui.component.StatusBar
import com.batmudcn.ui.component.StatusHud
import com.batmudcn.ui.theme.*
import com.batmudcn.viewmodel.GameViewModel

/** Strip ANSI escape sequences from text, returns clean text. */
private fun stripAnsiCodes(text: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < text.length) {
        if (text[i] == '' && i + 1 < text.length && text[i + 1] == '[') {
            i += 2
            while (i < text.length && text[i] != 'm') i++
            if (i < text.length) i++ // skip 'm'
        } else {
            sb.append(text[i])
            i++
        }
    }
    return sb.toString()
}

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onOpenSettings: () -> Unit,
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val outputLines by viewModel.outputLines.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val statusLine by viewModel.statusLine.collectAsStateWithLifecycle()
    val isConnected = connectionState == com.batmudcn.engine.GameEngine.ConnectionState.CONNECTED

    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }

    // Auto-scroll: use snapshotFlow to survive rapid streaming updates.
    // scrollToItem (snap) instead of animateScrollToItem — not cancelled by fast arrivals.
    LaunchedEffect(Unit) {
        snapshotFlow { outputLines.size }
            .collect { size ->
                if (autoScroll && size > 0) {
                    listState.scrollToItem(size - 1)
                }
            }
    }

    // Detect manual scroll up → pause auto-scroll; scroll to bottom → resume
    val isAtBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= outputLines.size - 2
        }
    }
    LaunchedEffect(isAtBottom) {
        autoScroll = isAtBottom
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBg),
    ) {
        StatusBar(
            connectionState = connectionState,
            statusMessage = statusMessage,
            onToggleConnection = { viewModel.toggleConnection() },
            onOpenSettings = onOpenSettings,
        )

        // Fixed character status HUD (HP/SP/EP/Exp)
        StatusHud(statusLine = statusLine)

        // Terminal output — landscape orientation provides enough width for 80-col content
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (outputLines.isEmpty()) {
                item {
                    Text(
                        text = "等待连接...",
                        color = TerminalDimText,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            items(
                count = outputLines.size,
                key = { index -> "${outputLines[index].hashCode()}_$index" },
            ) { index ->
                val line = outputLines[index]
                if (line.text.isEmpty() && !line.isEcho) {
                    Spacer(modifier = Modifier.height(3.dp))
                } else if (line.isEcho) {
                    BasicText(
                        text = AnnotatedString(
                            text = "> ${line.text.removePrefix("> ")}",
                            spanStyles = listOf(
                                AnnotatedString.Range(
                                    SpanStyle(color = TerminalAccent, fontWeight = FontWeight.Bold),
                                    0, 1,
                                ),
                                AnnotatedString.Range(
                                    SpanStyle(color = TerminalAccent.copy(alpha = 0.8f)),
                                    2, line.text.removePrefix("> ").length + 2,
                                ),
                            ),
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TerminalAccent.copy(alpha = 0.8f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        ),
                    )
                } else {
                    // Use AnsiRenderer when rawBytes (with ANSI+translation) is available
                    val displayText = if (line.rawBytes != null && line.rawBytes.isNotEmpty()) {
                        AnsiRenderer.render(line.rawBytes)
                    } else {
                        val displayStr = if (line.html.isNotEmpty() && line.html != line.text) {
                            line.html
                        } else {
                            line.text
                        }
                        AnnotatedString(displayStr)
                    }
                    BasicText(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TerminalText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        ),
                    )
                }
            }
        }

        // Command input at bottom
        CommandInput(
            onSend = { cmd -> viewModel.sendCommand(cmd) },
            connected = isConnected,
        )
    }
}
