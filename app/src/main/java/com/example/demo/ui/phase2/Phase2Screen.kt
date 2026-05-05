package com.example.demo.ui.phase2

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.demo.model.ActionType
import com.example.demo.model.GameEvent
import com.example.demo.model.Player
import com.example.demo.model.PlayerStatus

/**
 * 第二阶段公开结算页面。
 */
@Composable
fun Phase2Screen(
    eventQueue: List<GameEvent>,
    currentEventIndex: Int,
    systemBroadcast: String,
    silkBagNotice: String = "",
    isHost: Boolean,
    selfPlayer: Player,
    players: List<Player>,
    onHostDecideIndex: (Int) -> Unit,
    onVote: (Boolean) -> Unit,
    onAllianceBetrayal: (String) -> Unit,
    onAllianceBetrayalResult: (String, String, Boolean) -> Unit,
    onGhostInterfere: (Boolean) -> Unit,
    dayNumber: Int,
    isGameOver: Boolean,
    canProceedToNextDay: Boolean,
    onProceedToNextDay: () -> Unit,
    onRestartEvent: () -> Unit,
    onSilkBagClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val requiredConfirmCount = remember(players) { players.requiredConfirmCount() }

    BackHandler {
        Toast.makeText(context, "第二阶段结算中，暂时不能返回。", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(currentEventIndex, eventQueue.size) {
        if (eventQueue.isNotEmpty() && currentEventIndex in eventQueue.indices) {
            listState.animateScrollToItem(currentEventIndex)
        }
    }
    LaunchedEffect(silkBagNotice) {
        if (silkBagNotice.isNotBlank()) {
            Toast.makeText(context, silkBagNotice, Toast.LENGTH_LONG).show()
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
        Phase2Header(
            selfPlayer = selfPlayer,
            systemBroadcast = systemBroadcast,
            silkBagNotice = silkBagNotice,
            dayNumber = dayNumber,
            eventCount = eventQueue.size,
            onSilkBagClick = onSilkBagClick
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(eventQueue) { index, event ->
                EventItemCard(
                    event = event,
                    players = players,
                    requiredConfirmCount = requiredConfirmCount,
                    isPast = index < currentEventIndex,
                    isCurrent = index == currentEventIndex,
                    isFuture = index > currentEventIndex,
                    isHost = isHost,
                    selfPlayer = selfPlayer,
                    onHostDecideIndex = onHostDecideIndex,
                    onVote = onVote,
                    onAllianceBetrayal = onAllianceBetrayal,
                    onAllianceBetrayalResult = onAllianceBetrayalResult,
                    onGhostInterfere = onGhostInterfere,
                    onRestartEvent = onRestartEvent
                )
            }

            val lastEvent = eventQueue.lastOrNull()
            val isLastEventConfirmed = lastEvent != null && lastEvent.confirmCount >= requiredConfirmCount.coerceAtLeast(1)
            if (currentEventIndex >= eventQueue.lastIndex && isLastEventConfirmed && isHost && !isGameOver) {
                item {
                    EndOfDayCard(
                        canProceedToNextDay = canProceedToNextDay,
                        dayNumber = dayNumber,
                        onProceedToNextDay = onProceedToNextDay
                    )
                }
            }
        }
    }
}

@Composable
private fun Phase2Header(
    selfPlayer: Player,
    systemBroadcast: String,
    silkBagNotice: String,
    dayNumber: Int,
    eventCount: Int,
    onSilkBagClick: () -> Unit
) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onSilkBagClick) {
                Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("查看锦囊", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF5C4033).copy(alpha = 0.08f))
                    .border(1.dp, Color(0xFF5C4033).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("灵脉", fontSize = 13.sp, color = Color(0xFF8B7355))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "${selfPlayer.spiritVeins}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5C4033)
                    )
                }
            }
        }

        if (systemBroadcast.isNotBlank()) {
            GlassCard {
                Text(systemBroadcast, color = Color(0xFFB42318), fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (silkBagNotice.isNotBlank() && silkBagNotice != systemBroadcast) {
            GlassCard {
                Text(silkBagNotice, color = Color(0xFFA64B3F), fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = "第二纪元：公开裁决",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF5C4033)
        )
        Text(
            text = "第 $dayNumber 天，共 $eventCount 个事件。房主依次裁定，玩家确认后推进。",
            fontSize = 12.sp,
            color = Color(0xFF8B7355)
        )
    }
}

@Composable
private fun EventItemCard(
    event: GameEvent,
    players: List<Player>,
    requiredConfirmCount: Int,
    isPast: Boolean,
    isCurrent: Boolean,
    isFuture: Boolean,
    isHost: Boolean,
    selfPlayer: Player,
    onHostDecideIndex: (Int) -> Unit,
    onVote: (Boolean) -> Unit,
    onAllianceBetrayal: (String) -> Unit,
    onAllianceBetrayalResult: (String, String, Boolean) -> Unit,
    onGhostInterfere: (Boolean) -> Unit,
    onRestartEvent: () -> Unit
) {
    val liveAttacker = players.find { it.id == event.attacker.id } ?: event.attacker
    val liveDefender = players.find { it.id == event.defender.id } ?: event.defender
    val livePartner = event.alliancePartner?.let { partner -> players.find { it.id == partner.id } ?: partner }
    val safeRequiredCount = requiredConfirmCount.coerceAtLeast(1)
    val isEventFinished = event.confirmCount >= safeRequiredCount
    val cardAlpha = when {
        isCurrent -> 1f
        isPast -> 0.62f
        else -> 0.42f
    }

    var hasVoted by remember(event.id, event.hostDecisionIndex) { mutableStateOf(false) }

    LaunchedEffect(event.confirmCount) {
        if (event.confirmCount == 0) {
            hasVoted = false
        }
    }

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
            EventCardTitle(
                event = event,
                isCurrent = isCurrent,
                isPast = isPast,
                isFuture = isFuture
            )

            Spacer(modifier = Modifier.height(16.dp))

            PlayerLine(
                label = if (event.isAllianceAction && livePartner != null) "行动方" else "发起者",
                name = if (event.isAllianceAction && livePartner != null) {
                    "${liveAttacker.heavenMarkedName()} / ${livePartner.heavenMarkedName()}"
                } else {
                    liveAttacker.heavenMarkedName()
                },
                veins = liveAttacker.spiritVeins,
                color = Color(0xFFB54708)
            )
            Spacer(modifier = Modifier.height(10.dp))
            PlayerLine(
                label = "目标",
                name = liveDefender.heavenMarkedName(),
                veins = liveDefender.spiritVeins,
                color = Color(0xFF175CD3)
            )
            Spacer(modifier = Modifier.height(10.dp))

            EventSummary(event = event)
            AllianceSummary(event = event, players = players)
            SilkBagSummary(event = event, players = players)

            if (isCurrent && !isEventFinished) {
                Spacer(modifier = Modifier.height(20.dp))
                Divider(color = Color(0xFFE6D3A3).copy(alpha = 0.5f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                CurrentEventControls(
                    event = event,
                    isHost = isHost,
                    selfPlayer = selfPlayer,
                    requiredConfirmCount = safeRequiredCount,
                    hasVoted = hasVoted,
                    onVoteStarted = { hasVoted = true },
                    onHostDecideIndex = onHostDecideIndex,
                    onVote = onVote,
                    onAllianceBetrayal = onAllianceBetrayal,
                    onAllianceBetrayalResult = onAllianceBetrayalResult,
                    onGhostInterfere = onGhostInterfere,
                    onRestartEvent = onRestartEvent
                )
            }

            if ((isPast || (isCurrent && isEventFinished)) && event.hostDecisionIndex != null) {
                CompletedDecisionFooter(event = event)
            }
        }
    }
}

@Composable
private fun SilkBagSummary(event: GameEvent, players: List<Player>) {
    if (event.silkBagUseLogs.isEmpty() &&
        event.veinChangesByPlayerId.isEmpty() &&
        event.silkBagChangesByPlayerId.isEmpty()
    ) {
        return
    }
    Spacer(modifier = Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF8E8).copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text("锦囊修正", color = Color(0xFFA64B3F), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        event.silkBagUseLogs.forEach { log ->
            Text(
                text = "【${log.cardName}】${if (log.requiresHostDecision) " · 待房主裁决录入" else " · 已声明"}",
                color = Color(0xFF7A4F2C),
                fontSize = 12.sp
            )
        }
        if (event.veinChangesByPlayerId.isNotEmpty() || event.silkBagChangesByPlayerId.isNotEmpty()) {
            val names = players.associateBy { it.id }
            val veinText = event.veinChangesByPlayerId.entries.joinToString("；") { (id, change) ->
                val sign = if (change > 0) "+" else ""
                "${names[id]?.name ?: "未知"} 灵脉 $sign$change"
            }
            val bagText = event.silkBagChangesByPlayerId.entries.joinToString("；") { (id, change) ->
                val sign = if (change > 0) "+" else ""
                "${names[id]?.name ?: "未知"} 锦囊 $sign$change"
            }
            Text(
                text = listOf(veinText, bagText).filter { it.isNotBlank() }.joinToString("；"),
                color = Color(0xFF5C4033),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun EventCardTitle(
    event: GameEvent,
    isCurrent: Boolean,
    isPast: Boolean,
    isFuture: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "事件 #${event.eventIndex}",
            color = if (isCurrent) Color(0xFFB42318) else Color(0xFF8B7355),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        when {
            isPast -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("已结算", color = Color(0xFF659B7A), fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "已结算",
                        tint = Color(0xFF659B7A),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            isFuture -> Text("等待前序事件", color = Color.Gray, fontSize = 12.sp)
            else -> Text("正在裁定", color = Color(0xFFD4AF37), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlayerLine(
    label: String,
    name: String,
    veins: Int,
    color: Color
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$label：$name", fontWeight = FontWeight.Bold, color = color)
        Text("灵脉 $veins", color = color, fontSize = 14.sp)
    }
}

@Composable
private fun EventSummary(event: GameEvent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "行动：${event.actionType.displayName()}",
            color = Color(0xFF8C6A2F),
            modifier = Modifier.weight(1f)
        )
        if (event.actionType == ActionType.DUEL) {
            Text(
                "赌注 ${event.stake}",
                color = Color(0xFF7A5C2E),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        if (event.isAllianceAction) {
            Text("同盟 x3", color = Color(0xFFB42318), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        if (event.karmicInfluence != 0) {
            val color = if (event.karmicInfluence > 0) Color(0xFF2E7D32) else Color(0xFFB42318)
            val sign = if (event.karmicInfluence > 0) "+" else ""
            Text("因果 $sign${event.karmicInfluence}", color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AllianceSummary(event: GameEvent, players: List<Player>) {
    val plan = event.allianceActionPlan ?: return
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "同盟分配：收益 ${plan.rewardShareFirst}%/${plan.rewardShareSecond}%，惩罚 ${plan.penaltyShareFirst}%/${plan.penaltyShareSecond}%",
        color = Color(0xFF8B7355),
        fontSize = 12.sp
    )

    val betrayerId = event.betrayerId ?: return
    val betrayerName = players.find { it.id == betrayerId }?.name ?: "未知玩家"
    val resultText = when {
        event.betrayalWinnerId == null -> "等待房主录入反水判定"
        event.betrayalSucceeded == true -> "反水成功，结算归属已改写"
        else -> "反水失败，维持原同盟结算"
    }
    Text(
        text = "$betrayerName 声明反水：$resultText",
        color = Color(0xFFB42318),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun CurrentEventControls(
    event: GameEvent,
    isHost: Boolean,
    selfPlayer: Player,
    requiredConfirmCount: Int,
    hasVoted: Boolean,
    onVoteStarted: () -> Unit,
    onHostDecideIndex: (Int) -> Unit,
    onVote: (Boolean) -> Unit,
    onAllianceBetrayal: (String) -> Unit,
    onAllianceBetrayalResult: (String, String, Boolean) -> Unit,
    onGhostInterfere: (Boolean) -> Unit,
    onRestartEvent: () -> Unit
) {
    when {
        event.hostDecisionIndex == null -> HostDecisionControls(
            event = event,
            isHost = isHost,
            onHostDecideIndex = onHostDecideIndex
        )

        event.isSystemDetermined && event.confirmCount == 0 -> SystemDecisionControls(
            event = event,
            isHost = isHost,
            onHostDecideIndex = onHostDecideIndex
        )

        else -> VoteControls(
            event = event,
            isHost = isHost,
            selfPlayer = selfPlayer,
            requiredConfirmCount = requiredConfirmCount,
            hasVoted = hasVoted,
            onVoteStarted = onVoteStarted,
            onVote = onVote,
            onAllianceBetrayal = onAllianceBetrayal,
            onAllianceBetrayalResult = onAllianceBetrayalResult,
            onGhostInterfere = onGhostInterfere,
            onRestartEvent = onRestartEvent
        )
    }
}

@Composable
private fun HostDecisionControls(
    event: GameEvent,
    isHost: Boolean,
    onHostDecideIndex: (Int) -> Unit
) {
    if (!isHost) {
        WaitingCard("等待房主裁定当前事件")
        return
    }

    Text("请根据线下结果选择本次事件裁定：", color = Color(0xFF7A5C2E))
    Spacer(modifier = Modifier.height(12.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        event.actionType.decisionLabels().forEachIndexed { index, label ->
            PrimaryButton(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                text = label,
                color = Color(0xFFD4AF37)
            ) {
                onHostDecideIndex(index)
            }
        }
    }
}

@Composable
private fun SystemDecisionControls(
    event: GameEvent,
    isHost: Boolean,
    onHostDecideIndex: (Int) -> Unit
) {
    if (!isHost) {
        WaitingCard("系统已给出判定，等待房主确认")
        return
    }

    Text("系统判定已触发，请房主确认后进入投票。", color = Color(0xFF7A5C2E))
    Spacer(modifier = Modifier.height(12.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = event.systemMemo.ifBlank { "系统已自动裁定本事件。" },
                color = Color(0xFFB42318),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                text = "确认系统裁定",
                color = Color(0xFFD4AF37)
            ) {
                onHostDecideIndex(event.hostDecisionIndex ?: 0)
            }
        }
    }
}

@Composable
private fun VoteControls(
    event: GameEvent,
    isHost: Boolean,
    selfPlayer: Player,
    requiredConfirmCount: Int,
    hasVoted: Boolean,
    onVoteStarted: () -> Unit,
    onVote: (Boolean) -> Unit,
    onAllianceBetrayal: (String) -> Unit,
    onAllianceBetrayalResult: (String, String, Boolean) -> Unit,
    onGhostInterfere: (Boolean) -> Unit,
    onRestartEvent: () -> Unit
) {
    val decisionText = event.decisionText()
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0.9f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
    )
    val waitingForBetrayalResult = event.isAllianceAction &&
        event.betrayerId != null &&
        event.betrayalSucceeded == null

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (event.isSystemDetermined && event.systemMemo.isNotBlank()) {
            Text(event.systemMemo, color = Color(0xFFB42318), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = "房主裁定：$decisionText",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD4AF37),
            modifier = Modifier.scale(pulse),
            textAlign = TextAlign.Center
        )
        Text(
            text = "确认进度 ${event.confirmCount.coerceAtMost(requiredConfirmCount)}/$requiredConfirmCount",
            fontSize = 12.sp,
            color = Color(0xFF8B7355)
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (event.isAllianceAction && event.allianceActionPlan?.includes(selfPlayer.id) == true && event.betrayerId == null) {
            SecondaryButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                text = "声明反水"
            ) {
                onAllianceBetrayal(event.id)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isHost && event.isAllianceAction && event.betrayerId != null && event.betrayalWinnerId == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PrimaryButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    text = "反水成功",
                    color = Color(0xFFB42318)
                ) {
                    onAllianceBetrayalResult(event.id, event.betrayerId, true)
                }
                SecondaryButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    text = "反水失败"
                ) {
                    onAllianceBetrayalResult(event.id, event.betrayerId, false)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (selfPlayer.status == PlayerStatus.ELIMINATED && !isHost) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    text = "幽魂赐福"
                ) {
                    onGhostInterfere(true)
                }
                SecondaryButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    text = "幽魂降劫"
                ) {
                    onGhostInterfere(false)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isHost && !waitingForBetrayalResult) {
            TextButton(onClick = onRestartEvent, modifier = Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重新裁定本事件", color = Color(0xFFB42318), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        when {
            waitingForBetrayalResult -> WaitingCard("等待房主录入反水判定")
            event.confirmCount >= requiredConfirmCount -> FinishedCard("本事件已确认，正在推进后续结算")
            isHost -> WaitingCard("等待其他玩家确认裁定")
            hasVoted -> WaitingCard("已确认裁定，等待其他玩家")
            else -> PlayerVoteButtons(
                isSystemDetermined = event.isSystemDetermined,
                onVoteStarted = onVoteStarted,
                onVote = onVote
            )
        }
    }
}

@Composable
private fun PlayerVoteButtons(
    isSystemDetermined: Boolean,
    onVoteStarted: () -> Unit,
    onVote: (Boolean) -> Unit
) {
    Column {
        PrimaryButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            text = if (isSystemDetermined) "确认系统裁定" else "确认房主裁定",
            color = Color(0xFFD4AF37)
        ) {
            onVoteStarted()
            onVote(true)
        }
        if (!isSystemDetermined) {
            Spacer(modifier = Modifier.height(8.dp))
            SecondaryButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                text = "不同意，退回重判"
            ) {
                onVoteStarted()
                onVote(false)
            }
        }
    }
}

@Composable
private fun CompletedDecisionFooter(event: GameEvent) {
    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE6D3A3).copy(alpha = 0.5f), thickness = 1.dp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text = "最终裁定：${event.decisionText()}",
            color = Color(0xFFD4AF37),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun EndOfDayCard(
    canProceedToNextDay: Boolean,
    dayNumber: Int,
    onProceedToNextDay: () -> Unit
) {
    Spacer(modifier = Modifier.height(24.dp))
    if (canProceedToNextDay) {
        PrimaryButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            text = "进入第 ${dayNumber + 1} 天",
            color = Color(0xFF5C4033)
        ) {
            onProceedToNextDay()
        }
    } else {
        SecondaryButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            text = "天机推演中..."
        ) {}
    }
}

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
private fun FinishedCard(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
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
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
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

private fun List<Player>.requiredConfirmCount(): Int {
    return count { it.status != PlayerStatus.ELIMINATED && !it.isBot }
}

private fun Player.heavenMarkedName(): String {
    return if (hasHeavenProtection) "$name（天道庇护）" else name
}

private fun ActionType.displayName(): String {
    return when (this) {
        ActionType.DUEL -> "斗法"
        ActionType.RAID -> "奇袭"
        ActionType.DEFEND_ARRAY -> "防御"
        ActionType.EXPLORE -> "探索"
    }
}

private fun ActionType.decisionLabels(): List<String> {
    return when (this) {
        ActionType.DUEL -> listOf("斗法成功", "斗法失败", "对方投降")
        ActionType.RAID -> listOf("奇袭成功", "奇袭失败")
        ActionType.EXPLORE -> listOf("获得灵脉", "获得锦囊")
        ActionType.DEFEND_ARRAY -> emptyList()
    }
}

private fun GameEvent.decisionText(): String {
    return actionType.decisionLabels().getOrNull(hostDecisionIndex ?: 0)
        ?: when (actionType) {
            ActionType.RAID -> if (hostDecisionIndex == 2) "护宗大阵反制" else "已裁定"
            ActionType.DEFEND_ARRAY -> "护宗大阵生效"
            else -> "已裁定"
        }
}
