package com.example.demo.ui.phase1

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.demo.model.Player
import com.example.demo.model.PlayerStatus

/**
 * 房主测试模式面板，用于调整下一天和玩家灵脉。
 */
@Composable
fun Phase1DebugPanel(
    players: List<Player>,
    currentDay: Int,
    pendingDayNumber: Int?,
    pendingSpiritVeins: Map<String, Int>,
    onPendingDayChange: (Int?) -> Unit,
    onPendingSpiritVeinsChange: (String, Int?) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minimumNextDay = currentDay + 1
    val effectiveNextDay = (pendingDayNumber ?: minimumNextDay).coerceAtLeast(minimumNextDay)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color.White.copy(alpha = 0.72f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BugReport, contentDescription = null, tint = Color(0xFFB42318))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("房主调试", fontWeight = FontWeight.Bold, color = Color(0xFF5C4033))
                    Text("设置会在下一天开始时生效", fontSize = 12.sp, color = Color(0xFF8B7355))
                }
                OutlinedButton(onClick = onClear) {
                    Text("清空")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("下一天进入第 $effectiveNextDay 天", modifier = Modifier.weight(1f), color = Color(0xFF5C4033))
                IconButton(onClick = { onPendingDayChange((effectiveNextDay - 1).coerceAtLeast(minimumNextDay)) }) {
                    Icon(Icons.Default.Remove, contentDescription = "减少天数")
                }
                IconButton(onClick = { onPendingDayChange(effectiveNextDay + 1) }) {
                    Icon(Icons.Default.Add, contentDescription = "增加天数")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DebugDayChip(4, effectiveNextDay, onPendingDayChange)
                DebugDayChip(6, effectiveNextDay, onPendingDayChange)
                AssistChip(
                    onClick = { onPendingDayChange(null) },
                    label = { Text("默认 +1") }
                )
            }

            players.forEach { player ->
                val pendingVeins = pendingSpiritVeins[player.id] ?: player.spiritVeins
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = player.name + if (player.status == PlayerStatus.ELIMINATED) "（已断绝）" else "",
                            color = Color(0xFF5C4033),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text("当前 ${player.spiritVeins}，下天 $pendingVeins", fontSize = 12.sp, color = Color(0xFF8B7355))
                    }
                    VeinStepButton("-", enabled = pendingVeins > 0) {
                        onPendingSpiritVeinsChange(player.id, (pendingVeins - 10).coerceAtLeast(0))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    VeinStepButton("+") {
                        onPendingSpiritVeinsChange(player.id, pendingVeins + 10)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(onClick = { onPendingSpiritVeinsChange(player.id, player.spiritVeins) }) {
                        Text("重置")
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugDayChip(
    day: Int,
    selectedDay: Int,
    onPendingDayChange: (Int?) -> Unit
) {
    val label = when (day) {
        4 -> "第 4 天"
        6 -> "第 6 天"
        else -> "第 $day 天"
    }
    if (selectedDay == day) {
        ElevatedAssistChip(onClick = { onPendingDayChange(day) }, label = { Text(label) })
    } else {
        AssistChip(onClick = { onPendingDayChange(day) }, label = { Text(label) })
    }
}

@Composable
private fun VeinStepButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}
