package com.example.demo.ui.phase2

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.example.demo.model.ActionType
import com.example.demo.model.GameEvent
import com.example.demo.model.Player
import com.example.demo.model.PlayerStatus

@Composable
fun Phase2Screen(
    eventQueue: List<GameEvent>,   // 完整队列
    currentEventIndex: Int,        // 当前进行到第几个
    systemBroadcast: String,
    isHost: Boolean,
    selfPlayer: Player,
    players: List<Player>,
    onHostDecideIndex: (Int) -> Unit,
    onVote: (Boolean) -> Unit,
    onGhostInterfere: (Boolean) -> Unit,
    dayNumber: Int,
    isGameOver: Boolean,
    canProceedToNextDay: Boolean,
    onProceedToNextDay: () -> Unit,
    onRestartEvent: () -> Unit
) {
    val context = LocalContext.current
    BackHandler {
        Toast.makeText(context, "庭审进行中，无法退出！", Toast.LENGTH_SHORT).show()
    }

    // 用于控制事件流的滚动状态
    val listState = rememberLazyListState()

    // 自动平滑滚动到当前正在裁定的事件
    LaunchedEffect(currentEventIndex) {
        if (eventQueue.isNotEmpty() && currentEventIndex < eventQueue.size) {
            listState.animateScrollToItem(currentEventIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFFDF8),
                        Color(0xFFF7EEDC),
                        Color(0xFFEFE1C6)
                    )
                )
            )
            .statusBarsPadding()
    ) {
        // ================= 1. 顶部固定区域 (HUD & 播报) =================
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
            // 本尊灵脉 HUD
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF5C4033).copy(alpha = 0.08f))
                        .border(1.dp, Color(0xFF5C4033).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✨", fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                        Text("本尊灵脉: ", fontSize = 13.sp, color = Color(0xFF8B7355))
                        Text("${selfPlayer.spiritVeins}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5C4033))
                    }
                }
            }

            // 系统播报
            if (systemBroadcast.isNotEmpty()) {
                GlassCard {
                    Text(systemBroadcast, color = Color(0xFFB42318), fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text("阶段二 · 公开结算庭", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5C4033))
            Text("纵览天机因果，裁定生死流转", fontSize = 12.sp, color = Color(0xFF8B7355))
        }

        // ================= 2. 滚动因果卷轴 (事件列表) =================
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(eventQueue) { index, event ->
                val isPast = index < currentEventIndex
                val isCurrent = index == currentEventIndex
                val isFuture = index > currentEventIndex

                EventItemCard(
                    event = event,
                    players = players,
                    isPast = isPast,
                    isCurrent = isCurrent,
                    isFuture = isFuture,
                    isHost = isHost,
                    onHostDecideIndex = onHostDecideIndex,
                    onVote = onVote,
                    onRestartEvent = onRestartEvent
                )
            }

            // ================= 3. 卷轴最底部的跳转按钮 =================
            if (eventQueue.isNotEmpty() && currentEventIndex >= eventQueue.size - 1) {
                val lastEvent = eventQueue.last()
                // 只有当最后一个事件也被全员确认，且是房主，且游戏未结束时，才显示跳转按钮
                if (lastEvent.confirmCount >= lastEvent.totalPlayers && isHost && !isGameOver) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        // 根据 ViewModel 里的 canProceedToNextDay 状态切换显示
                        if (canProceedToNextDay) {
                            PrimaryButton(
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                                text = "开启下一纪元 (Day ${dayNumber + 1})",
                                color = Color(0xFF5C4033)
                            ) {
                                onProceedToNextDay()
                            }
                        } else {
                            // ✨ 3秒等待期间，显示为灰色且无响应的“天机推演中”
                            SecondaryButton(
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                                text = "天机推演中 (3s)...", // 提示正在冷却
                                onClick = { /* 此时点击无效 */ }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ================= 因果卡片组件 =================
@Composable
private fun EventItemCard(
    event: GameEvent,
    players: List<Player>,
    isPast: Boolean,
    isCurrent: Boolean,
    isFuture: Boolean,
    isHost: Boolean,
    onHostDecideIndex: (Int) -> Unit,
    onVote: (Boolean) -> Unit,
    onRestartEvent: () -> Unit
) {
    val actionName = when (event.actionType) {
        ActionType.DUEL -> "登仙台斗法"
        ActionType.RAID -> "死士奇袭"
        ActionType.DEFEND_ARRAY -> "护宗大阵"
        ActionType.EXPLORE -> "秘境寻宝"
    }

    // 实时抓取最新的玩家数值
    val liveAttacker = players.find { it.id == event.attacker.id } ?: event.attacker
    val liveDefender = players.find { it.id == event.defender.id } ?: event.defender

    // 独立维护每张卡片的投票状态
    var hasVoted by remember(event.id, event.hostDecisionIndex) { mutableStateOf(false) }

    LaunchedEffect(event.confirmCount) {
        if (event.confirmCount == 0) {
            hasVoted = false
        }
    }

    // 透明度：当前的最高亮，过去的半透明，未来的最暗
    val cardAlpha = if (isCurrent) 1f else if (isPast) 0.6f else 0.4f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isCurrent) 0.9f else 0.6f),
                        Color.White.copy(alpha = if (isCurrent) 0.7f else 0.4f)
                    )
                )
            )
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = if (isCurrent) Color(0xFFD4AF37) else Color(0xFFE6D3A3),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            // --- 卡片头部 ---
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "因果 #${event.eventIndex}",
                    color = if (isCurrent) Color(0xFFB42318) else Color(0xFF8B7355),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (isPast) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("已了结", color = Color(0xFF659B7A), fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.CheckCircle, "已了结", tint = Color(0xFF659B7A), modifier = Modifier.size(14.dp))
                    }
                }
                if (isFuture) {
                    Text("天机未至", color = Color.Gray, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 攻守双方信息 ---
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("攻方 · ${liveAttacker.name}", fontWeight = FontWeight.Bold, color = Color(0xFFB54708))
                Text("底蕴: ${liveAttacker.spiritVeins}", color = Color(0xFFB54708), fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("守方 · ${liveDefender.name}", fontWeight = FontWeight.Bold, color = Color(0xFF175CD3))
                Text("底蕴: ${liveDefender.spiritVeins}", color = Color(0xFF175CD3), fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("法门 · $actionName", color = Color(0xFF8C6A2F), modifier = Modifier.weight(1f))
                if (event.karmicInfluence != 0) {
                    val color = if (event.karmicInfluence > 0) Color(0xFF34C759) else Color(0xFFFF3B30)
                    val sign = if (event.karmicInfluence > 0) "+" else ""
                    Text("因果 $sign${event.karmicInfluence}", color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // ================= 当前事件的判定互动区 =================
            if (isCurrent) {
                Spacer(modifier = Modifier.height(20.dp))
                Divider(color = Color(0xFFE6D3A3).copy(alpha = 0.5f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                if (event.hostDecisionIndex == null) {
                    if (isHost) {
                        Text("录入天机裁定", color = Color(0xFF7A5C2E))
                        Spacer(modifier = Modifier.height(12.dp))

                        val buttons = when(event.actionType) {
                            ActionType.DUEL -> listOf("判定成功" to 0, "判定失败" to 1, "对方投降" to 2)
                            ActionType.RAID -> listOf("奇袭成功" to 0, "奇袭失败" to 1)
                            ActionType.EXPLORE -> listOf("获得灵脉" to 0, "获得锦囊" to 1)
                            else -> emptyList()
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            buttons.forEach { (label, idx) ->
                                PrimaryButton(
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    text = label,
                                    color = Color(0xFFD4AF37)
                                ) {
                                    onHostDecideIndex(idx)
                                }
                            }
                        }
                    } else {
                        WaitingCard("众人议事中…")
                    }
                }
                else if (event.isSystemDetermined && event.confirmCount == 0) {
                    if (isHost) {
                        Text("天道共鸣", color = Color(0xFF7A5C2E))
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha=0.5f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text(event.systemMemo, color = Color(0xFFB42318), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(16.dp))
                                PrimaryButton(
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    text = "昭告天下 (执行结算)",
                                    color = Color(0xFFD4AF37)
                                ) {
                                    onHostDecideIndex(event.hostDecisionIndex ?: 0)
                                }
                            }
                        }
                    } else {
                        WaitingCard("天机显化中…")
                    }
                }
                else {
                    val decisionText = when (event.actionType) {
                        ActionType.DUEL -> listOf("判定成功", "判定失败", "对方投降").getOrNull(event.hostDecisionIndex ?: 0)
                        ActionType.RAID -> listOf("奇袭成功", "奇袭失败", "被防御").getOrNull(event.hostDecisionIndex ?: 0)
                        ActionType.EXPLORE -> listOf("获得灵脉", "获得锦囊").getOrNull(event.hostDecisionIndex ?: 0)
                        else -> "已裁定"
                    }

                    val pulse by rememberInfiniteTransition().animateFloat(
                        initialValue = 0.9f, targetValue = 1.05f,
                        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = ""
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        if (event.isSystemDetermined) {
                            Text(event.systemMemo, color = Color(0xFFB42318), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Text("天机裁定：$decisionText", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD4AF37), modifier = Modifier.scale(pulse))
                        Text("确认 ${event.confirmCount}/${event.totalPlayers}", fontSize = 12.sp, color = Color(0xFF8B7355))
                        Spacer(modifier = Modifier.height(16.dp))

                        // 重置按钮
                        if (isHost) {
                            val isLastEventFinished = event.eventIndex == event.totalEvents && event.confirmCount >= event.totalPlayers

                            // 只有在非“最后因果已了结”的情况下，才显示重置按钮
                            if (!isLastEventFinished) {
                                TextButton(
                                    onClick = { onRestartEvent() },
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("推翻重审 (重置本条因果)", color = Color(0xFFB42318), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        if (event.confirmCount >= event.totalPlayers) {
                            Box(
                                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha=0.5f), RoundedCornerShape(12.dp)).padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("天意已定，因果昭彰", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        } else {
                            if (isHost) {
                                WaitingCard("静候众人回应…")
                            } else {
                                if (hasVoted) {
                                    WaitingCard("已表态，等待他人…")
                                } else {
                                    Column {
                                        PrimaryButton(
                                            modifier = Modifier.fillMaxWidth().height(110.dp),
                                            text = if (event.isSystemDetermined) "顺应天道 (确认)" else "同意裁定",
                                            color = Color(0xFFD4AF37)
                                        ) {
                                            hasVoted = true
                                            onVote(true)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (!event.isSystemDetermined) {
                                            SecondaryButton(
                                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                                text = "提出异议"
                                            ) {
                                                hasVoted = true
                                                onVote(false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ================= 过去事件的裁定结果展示 =================
            if (isPast && event.hostDecisionIndex != null) {
                val decisionText = when (event.actionType) {
                    ActionType.DUEL -> listOf("判定成功", "判定失败", "对方投降").getOrNull(event.hostDecisionIndex ?: 0)
                    ActionType.RAID -> listOf("奇袭成功", "奇袭失败", "被防御").getOrNull(event.hostDecisionIndex ?: 0)
                    ActionType.EXPLORE -> listOf("获得灵脉", "获得锦囊").getOrNull(event.hostDecisionIndex ?: 0)
                    else -> "已了结"
                }
                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE6D3A3).copy(alpha = 0.5f), thickness = 1.dp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("天意裁定: $decisionText", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ================= 底层 UI 积木 =================
@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.6f)
                    )
                )
            )
            .border(1.dp, Color(0xFFE6D3A3), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun WaitingCard(text: String) {
    GlassCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color(0xFF8C6A2F), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PrimaryButton(
    modifier: Modifier,
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .scale(if (pressed) 0.97f else 1f)
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color, RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun SecondaryButton(
    modifier: Modifier,
    text: String,
    onClick: () -> Unit
) {
    PrimaryButton(
        modifier = modifier,
        text = text,
        color = Color(0xFF6B7280),
        onClick = onClick
    )
}