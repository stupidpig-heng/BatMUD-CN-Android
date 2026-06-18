package com.batmudcn.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batmudcn.engine.OutputLine
import com.batmudcn.ui.AnsiRenderer
import com.batmudcn.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TerminalOutput(
    outputLines: List<OutputLine>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var autoScroll by remember { mutableStateOf(true) }

    // Auto-scroll to bottom when new lines arrive (if autoScroll is on)
    LaunchedEffect(outputLines.size) {
        if (autoScroll && outputLines.isNotEmpty()) {
            // Small delay to let compose layout
            listState.animateScrollToItem(outputLines.size - 1)
        }
    }

    // Detect manual scroll up (disable auto-scroll)
    val isAtBottom = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= outputLines.size - 2
        }
    }

    LaunchedEffect(isAtBottom.value) {
        autoScroll = isAtBottom.value
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable { /* just for focus */ },
    ) {
        items(outputLines, key = { "${it.hashCode()}_${it.text.take(10)}" }) { line ->
            if (line.text.isEmpty() && !line.isEcho) {
                // Empty line
                Spacer(modifier = Modifier.height(3.dp))
            } else if (line.isEcho) {
                // Echoed command
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
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                // Game output — render ANSI if present
                val displayText = if (line.html.contains("")) {
                    AnsiRenderer.renderText(line.html)
                } else {
                    AnnotatedString(line.text)
                }

                BasicText(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TerminalText,
                    ),
                )
            }
        }
    }
}
