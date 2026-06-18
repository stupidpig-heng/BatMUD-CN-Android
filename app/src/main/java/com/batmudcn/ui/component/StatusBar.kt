package com.batmudcn.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batmudcn.engine.GameEngine.ConnectionState
import com.batmudcn.ui.theme.TerminalAccent
import com.batmudcn.ui.theme.TerminalDimText
import com.batmudcn.ui.theme.TerminalError
import com.batmudcn.ui.theme.TerminalSuccess
import com.batmudcn.ui.theme.TerminalText

@Composable
fun StatusBar(
    connectionState: ConnectionState,
    statusMessage: String,
    onToggleConnection: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (dotColor, stateText) = when (connectionState) {
        ConnectionState.CONNECTED -> TerminalSuccess to "已连接"
        ConnectionState.CONNECTING -> Color(0xFFFFAA00) to "连接中…"
        ConnectionState.ERROR -> TerminalError to "错误"
        ConnectionState.DISCONNECTED -> TerminalError to "已断开"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status dot + text (clickable to toggle connection)
        Row(
            modifier = Modifier
                .clickable { onToggleConnection() }
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stateText,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = statusMessage.take(30),
                color = TerminalDimText,
                fontSize = 11.sp,
            )

            // Hint for tap
            if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "(点击重连)",
                    color = TerminalAccent,
                    fontSize = 10.sp,
                )
            }
        }

        // Settings button
        Text(
            text = "☰",
            color = TerminalText,
            fontSize = 18.sp,
            modifier = Modifier
                .clickable { onOpenSettings() }
                .padding(4.dp),
        )
    }
}
