package com.batmudcn.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Stat color mapping
private val HpColor = Color(0xFF44CC44)      // green
private val SpColor = Color(0xFF5588FF)      // blue
private val EpColor = Color(0xFFFFCC55)      // gold
private val ExpColor = Color(0xFFCC55FF)     // purple
private val HudBg = Color(0xFF222233)        // slightly blue dark bg
private val HudBorder = Color(0xFF333344)
private val DimLabel = Color(0xFF888899)

/**
 * Extracts a stat from prompt text like "Hp:318/318".
 * Returns null if not found.
 */
private fun extractStat(prompt: String, label: String): StatInfo? {
    val regex = Regex("""$label[:\s]*(\d+)/(\d+)""", RegexOption.IGNORE_CASE)
    val match = regex.find(prompt) ?: return null
    val current = match.groupValues[1].toIntOrNull() ?: return null
    val max = match.groupValues[2].toIntOrNull() ?: return null
    return StatInfo(label.uppercase(), current, max)
}

private data class StatInfo(
    val label: String,
    val current: Int,
    val max: Int,
)

/**
 * Fixed character status HUD — displays HP/SP/EP/Exp in a compact bar.
 * Placed between the connection status bar and the scrolling terminal.
 */
@Composable
fun StatusHud(
    statusLine: String,
    modifier: Modifier = Modifier,
) {
    if (statusLine.isBlank()) return

    // Parse stats from prompt text
    val stats = remember(statusLine) {
        listOfNotNull(
            extractStat(statusLine, "Hp"),
            extractStat(statusLine, "Sp"),
            extractStat(statusLine, "Ep"),
            extractStat(statusLine, "Exp"),
        )
    }

    // Also try "Tnl" (to next level) as a simpler value
    val tnlRegex = remember { Regex("""Tnl[:\s]*(\d+)""", RegexOption.IGNORE_CASE) }
    val tnl = remember(statusLine) {
        tnlRegex.find(statusLine)?.groupValues?.get(1)?.toIntOrNull()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HudBg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        if (stats.isEmpty()) {
            // Fallback: show raw prompt text
            Text(
                text = statusLine.trim(),
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                stats.forEach { stat ->
                    StatBadge(stat)
                }
                if (tnl != null) {
                    Text(
                        text = "TNL $tnl",
                        color = DimLabel,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBadge(stat: StatInfo) {
    val color = when (stat.label) {
        "HP" -> HpColor
        "SP" -> SpColor
        "EP" -> EpColor
        "EXP" -> ExpColor
        else -> DimLabel
    }
    val pct = if (stat.max > 0) stat.current.toFloat() / stat.max.toFloat() else 0f
    val barColor = if (pct > 0.6f) color else if (pct > 0.3f) Color(0xFFFFAA00) else Color(0xFFFF4444)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Label
        Text(
            text = stat.label,
            color = color.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
        // Current / Max
        Text(
            text = "${stat.current}/${stat.max}",
            color = Color(0xFFCCCCCC),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        // Mini health bar
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(HudBorder),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pct.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor),
            )
        }
    }
}
