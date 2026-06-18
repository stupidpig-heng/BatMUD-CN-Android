package com.batmudcn.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batmudcn.engine.DebugLine
import com.batmudcn.ui.theme.*

@Composable
fun DebugPanel(
    debugLines: List<DebugLine>,
    isVisible: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isVisible) return

    val listState = rememberLazyListState()

    LaunchedEffect(debugLines.size) {
        if (debugLines.isNotEmpty()) {
            listState.animateScrollToItem(debugLines.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.35f)
            .background(TerminalBg)
            .padding(top = 2.dp),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalBgLighter)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "调试面板",
                color = TerminalAccent,
                fontSize = 12.sp,
            )
            Button(
                onClick = onClear,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TerminalBorder,
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "清空",
                    color = TerminalDimText,
                    fontSize = 11.sp,
                )
            }
        }

        // Debug entries
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            items(debugLines) { line ->
                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                    // Mode tag
                    Text(
                        text = buildString {
                            val modeLabel = if (line.mode == "passthrough") "透传" else "翻译"
                            val modeColor = if (line.mode == "passthrough") "#666" else "#44cc44"
                            append("[$modeLabel] ")
                            append("ANSI:${line.ansiCount} ")
                            append("segs:${line.segmentCount} ")
                            if (line.mode == "translate") {
                                append("trans:${line.translatedSegs} ")
                            }
                        },
                        color = TerminalDimText,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )

                    if (line.textPreview.isNotEmpty()) {
                        Text(
                            text = "\"${line.textPreview.take(40)}\"",
                            color = TerminalText.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }

                    if (line.htmlPreview.isNotEmpty()) {
                        Text(
                            text = "→ ${line.htmlPreview.take(60)}",
                            color = TerminalSuccess.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }

                    if (line.rawHex.isNotEmpty()) {
                        Text(
                            text = "hex: ${line.rawHex.take(80)}",
                            color = TerminalDimText.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }
        }
    }
}
