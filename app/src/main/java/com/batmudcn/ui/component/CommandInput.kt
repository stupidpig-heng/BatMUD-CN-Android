package com.batmudcn.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import com.batmudcn.ui.theme.*

@Composable
fun CommandInput(
    onSend: (String) -> Unit,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    // Command history
    val history = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var tempSaved by remember { mutableStateOf("") }

    fun handleSend() {
        val cmd = text.trim()
        if (cmd.isNotEmpty()) {
            onSend(cmd)
            history.add(cmd)
            if (history.size > 500) history.removeAt(0)
            historyIndex = history.size
        }
        text = ""
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TerminalBgLighter)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Prompt
        Text(
            text = ">",
            color = TerminalAccent,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Input field — always enabled so user can tap to get keyboard
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            enabled = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = TerminalTextBright,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            ),
            cursorBrush = SolidColor(TerminalAccent),
            singleLine = true,
            // IME actions — handles soft keyboard Enter/Send
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = { handleSend() },
            ),
            modifier = Modifier
                .weight(1f)
                // Hardware keyboard support
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Enter -> {
                                handleSend()
                                true
                            }
                            Key.DirectionUp -> {
                                if (history.isNotEmpty()) {
                                    if (historyIndex == history.size) tempSaved = text
                                    if (historyIndex > 0) {
                                        historyIndex--
                                        text = history[historyIndex]
                                    }
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                if (historyIndex < history.size - 1) {
                                    historyIndex++
                                    text = history[historyIndex]
                                } else {
                                    historyIndex = history.size
                                    text = tempSaved
                                }
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            decorationBox = { innerTextField ->
                Box {
                    if (text.isEmpty()) {
                        Text(
                            text = if (connected) "输入命令..." else "已断开连接 — 请点击右上角设置服务器",
                            color = TerminalDimText,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
