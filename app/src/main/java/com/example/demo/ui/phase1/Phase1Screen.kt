package com.example.demo.ui.phase1

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.text.style.TextAlign
import com.example.demo.model.ActionType
import com.example.demo.model.AllianceActionPlan
import com.example.demo.model.AllianceRequest
import com.example.demo.model.ChatMessage
import com.example.demo.model.ConspiracyRequest
import com.example.demo.model.ConspiracySession
import com.example.demo.model.Player
import com.example.demo.model.PlayerStatus
import com.example.demo.ui.ClickParticleLayer
import com.example.demo.ui.StudioBackground
import com.example.demo.ui.StudioGlass
import com.example.demo.ui.StudioGlow
import com.example.demo.ui.WaitingCard
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 一阶段行动页，承载目标选择、行动提交、同盟、密谋和私聊。
 */
@Composable
fun Phase1Screen(
    players: List<Player>,
    hasSubmittedAction: Boolean,
    incomingAllianceRequest: AllianceRequest? = null,
    incomingConspiracyRequest: ConspiracyRequest? = null,
    conspiracySessions: List<ConspiracySession> = emptyList(),
    allianceActionPlans: List<AllianceActionPlan> = emptyList(),
    allianceNotice: String = "",
    conspiracyNotice: String = "",
    chatMessages: List<ChatMessage> = emptyList(),
    dayNumber: Int = 1,
    isHost: Boolean = false,
    isTestModeEnabled: Boolean = false,
    submittedCount: Int = 0,
    debugNextDayNumber: Int? = null,
    debugNextDaySpiritVeins: Map<String, Int> = emptyMap(),
    onActionSubmit: (Player, ActionType, Int) -> Unit,
    onConspiracyRequest: (Player) -> Unit = {},
    onConspiracyResponse: (ConspiracyRequest, Boolean) -> Unit = { _, _ -> },
    onConspiracyChatSend: (String, String) -> Unit = { _, _ -> },
    onAllianceRequest: (Player) -> Unit = {},
    onAllianceResponse: (AllianceRequest, Boolean) -> Unit = { _, _ -> },
    onAllianceActionPlanPropose: (Player, ActionType, Int, Int, Int) -> Unit = { _, _, _, _, _ -> },
    onAllianceActionPlanConfirm: (AllianceActionPlan) -> Unit = {},
    onAllianceChatSend: (String) -> Unit = {},
    onDebugNextDayChange: (Int?) -> Unit = {},
    onDebugSpiritVeinsChange: (String, Int?) -> Unit = { _, _ -> },
    onClearDebugSettings: () -> Unit = {},
    onForceProceed: () -> Unit = {}
) {
    val context = LocalContext.current
    BackHandler(enabled = true) {
        Toast.makeText(context, "劫数未尽，本尊不可强行出关！", Toast.LENGTH_SHORT).show()
    }

    val validTargets = remember(players) {
        players.filter { !it.isSelf && it.status != PlayerStatus.ELIMINATED }
    }

    var selectedPlayer by remember(players) {
        mutableStateOf(validTargets.firstOrNull() ?: players.first { it.isSelf })
    }

    var isSubmitting by remember(hasSubmittedAction) { mutableStateOf(false) }
    var isAllianceChatOpen by remember { mutableStateOf(false) }
    var isConspiracyChatOpen by remember { mutableStateOf(false) }
    var selectedConspiracySessionId by remember { mutableStateOf<String?>(null) }
    val selfPlayer = remember(players) { players.firstOrNull { it.isSelf } }
    val alliancePartner = remember(players, selfPlayer?.alliancePartnerId) {
        players.find { it.id == selfPlayer?.alliancePartnerId }
    }
    val alliancePlan = remember(allianceActionPlans, selfPlayer?.id, alliancePartner?.id) {
        allianceActionPlans.find { plan ->
            val selfId = selfPlayer?.id
            val partnerId = alliancePartner?.id
            selfId != null && partnerId != null && plan.includes(selfId) && plan.includes(partnerId)
        }
    }
    val allianceMessages = remember(chatMessages, selfPlayer?.id, alliancePartner?.id) {
        val selfId = selfPlayer?.id
        val partnerId = alliancePartner?.id
        chatMessages.filter { message ->
            message.isAllianceChat &&
                ((message.senderId == selfId && message.recipientPlayerId == partnerId) ||
                    (message.senderId == partnerId && message.recipientPlayerId == selfId))
        }
    }

    LaunchedEffect(allianceNotice) {
        if (allianceNotice.isNotBlank()) {
            Toast.makeText(context, allianceNotice, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(conspiracyNotice) {
        if (conspiracyNotice.isNotBlank()) {
            Toast.makeText(context, conspiracyNotice, Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        StudioBackground()
        ClickParticleLayer()

        incomingAllianceRequest?.let { request ->
            AllianceRequestDialog(
                request = request,
                onAccept = { onAllianceResponse(request, true) },
                onReject = { onAllianceResponse(request, false) }
            )
        }
        incomingConspiracyRequest?.let { request ->
            ConspiracyRequestDialog(
                request = request,
                onAccept = { onConspiracyResponse(request, true) },
                onReject = { onConspiracyResponse(request, false) }
            )
        }

        if (isAllianceChatOpen && selfPlayer != null && alliancePartner != null) {
            AllianceChatSheet(
                selfPlayerId = selfPlayer.id,
                partnerName = alliancePartner.name,
                messages = allianceMessages,
                onSend = onAllianceChatSend,
                onDismiss = { isAllianceChatOpen = false }
            )
        }
        if (isConspiracyChatOpen && selfPlayer != null) {
            ConspiracyChatSheet(
                selfPlayerId = selfPlayer.id,
                sessions = conspiracySessions,
                selectedSessionId = selectedConspiracySessionId,
                messages = chatMessages,
                onSessionSelect = { selectedConspiracySessionId = it },
                onSend = onConspiracyChatSend,
                onDismiss = { isConspiracyChatOpen = false }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            PhaseHeader()

            Spacer(modifier = Modifier.height(24.dp))

            if (isHost && isTestModeEnabled) {
                Phase1DebugPanel(
                    players = players,
                    currentDay = dayNumber,
                    pendingDayNumber = debugNextDayNumber,
                    pendingSpiritVeins = debugNextDaySpiritVeins,
                    onPendingDayChange = onDebugNextDayChange,
                    onPendingSpiritVeinsChange = onDebugSpiritVeinsChange,
                    onClear = onClearDebugSettings
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            PlayerRingsRow(
                players = players,
                selectedPlayer = selectedPlayer,
                onPlayerSelect = { player ->

                    if (!player.isSelf && player.status != PlayerStatus.ELIMINATED) {
                        selectedPlayer = player
                    } else if (player.isSelf) {
                        Toast.makeText(context, "不可对自己发动法门", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            if (alliancePartner != null) {
                Spacer(modifier = Modifier.height(16.dp))
                AllianceChatEntry(
                    partnerName = alliancePartner.name,
                    unreadHint = allianceMessages.lastOrNull()?.content.orEmpty(),
                    onClick = { isAllianceChatOpen = true }
                )
            }
            if (conspiracySessions.isNotEmpty()) {
                val conspiracyPartnerNames = remember(conspiracySessions, selfPlayer?.id) {
                    val selfId = selfPlayer?.id
                    conspiracySessions.joinToString("、") { session ->
                        if (session.firstPlayerId == selfId) session.secondPlayerName else session.firstPlayerName
                    }
                }
                val latestConspiracyMessage = remember(chatMessages, conspiracySessions) {
                    val sessionIds = conspiracySessions.map { it.sessionId }.toSet()
                    chatMessages
                        .filter { it.isConspiracyChat && it.conspiracySessionId in sessionIds }
                        .maxByOrNull { it.timestamp }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        selectedConspiracySessionId = selectedConspiracySessionId ?: conspiracySessions.firstOrNull()?.sessionId
                        isConspiracyChatOpen = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.55f))
                ) {
                    Icon(Icons.Default.Markunread, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            "密谋私聊 · $conspiracyPartnerNames",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        latestConspiracyMessage?.let { message ->
                            Text(
                                "${message.senderName}: ${message.content}",
                                fontSize = 11.sp,
                                color = Color(0xFF8B7355),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (hasSubmittedAction) {

                SubmittedOverlay(
                    isHost = isHost,
                    onRestart = onForceProceed
                )
            } else {

                if (!selectedPlayer.isSelf) {
                    ActionPanel(
                        targetPlayer = selectedPlayer,
                        selfPlayer = selfPlayer ?: players.first { it.isSelf },
                        isSubmitting = isSubmitting,
                        isAlliedWithTarget = selfPlayer?.alliancePartnerId == selectedPlayer.id,
                        dayNumber = dayNumber,
                        alliancePartner = alliancePartner,
                        alliancePlan = alliancePlan,
                        canConspire = dayNumber > 3,
                        canAlliance = dayNumber > 5,
                        onConspiracyRequest = { onConspiracyRequest(selectedPlayer) },
                        onAllianceRequest = { onAllianceRequest(selectedPlayer) },
                        onAllianceActionPlanPropose = { target, action, stake, rewardShare, penaltyShare ->
                            onAllianceActionPlanPropose(target, action, stake, rewardShare, penaltyShare)
                        },
                        onAllianceActionPlanConfirm = onAllianceActionPlanConfirm,
                        onActionSubmit = { target, action, stake ->
                            isSubmitting = true
                            onActionSubmit(target, action, stake)
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("请在上方选择一位道友降下法旨", color = Color(0xFF8B7355))
                    }
                }
            }
        }
    }
}

@Composable
private fun AllianceRequestDialog(
    request: AllianceRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        icon = {
            Icon(Icons.Default.People, contentDescription = null, tint = Color(0xFFD4AF37))
        },
        title = {
            Text("结盟请求")
        },
        text = {
            Text("${request.fromPlayerName} 邀请你本回合结盟")
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("同意")
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("拒绝")
            }
        }
    )
}

@Composable
private fun ConspiracyRequestDialog(
    request: ConspiracyRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        icon = {
            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color(0xFFC0625E))
        },
        title = {
            Text("密谋邀请")
        },
        text = {
            Text("${request.fromPlayerName} 邀请你开启本回合密谋")
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("同意")
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("拒绝")
            }
        }
    )
}

@Composable
private fun AllianceChatSheet(
    selfPlayerId: String,
    partnerName: String,
    messages: List<ChatMessage>,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Markunread, contentDescription = null, tint = Color(0xFF659B7A))
        },
        title = {
            Text("与 $partnerName 私聊")
        },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF5C4033).copy(alpha = 0.06f))
                        .padding(12.dp)
                ) {
                    if (messages.isEmpty()) {
                        Text(
                            "结盟已成，可以交换本回合的密谈。",
                            color = Color(0xFF8B7355),
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.Center),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages) { message ->
                                AllianceChatBubble(
                                    message = message,
                                    isSelf = message.senderId == selfPlayerId
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入盟友私聊") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSend(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank()
            ) {
                Text("发送")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun ConspiracyChatSheet(
    selfPlayerId: String,
    sessions: List<ConspiracySession>,
    selectedSessionId: String?,
    messages: List<ChatMessage>,
    onSessionSelect: (String) -> Unit,
    onSend: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    val selectedSession = sessions.find { it.sessionId == selectedSessionId } ?: sessions.firstOrNull()
    val selectedMessages = remember(messages, selectedSession?.sessionId) {
        messages.filter { it.isConspiracyChat && it.conspiracySessionId == selectedSession?.sessionId }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color(0xFFC0625E))
        },
        title = {
            Text("密谋私聊")
        },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    sessions.forEach { session ->
                        val partnerName = if (session.firstPlayerId == selfPlayerId) session.secondPlayerName else session.firstPlayerName
                        OutlinedButton(
                            onClick = { onSessionSelect(session.sessionId) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (session.sessionId == selectedSession?.sessionId) {
                                    Color(0xFFC0625E).copy(alpha = 0.12f)
                                } else {
                                    Color.Transparent
                                }
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(partnerName.take(4), fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF5C4033).copy(alpha = 0.06f))
                        .padding(12.dp)
                ) {
                    if (selectedMessages.isEmpty()) {
                        Text(
                            "密谋内容仅当前双方可见，不会进入公开战报。",
                            color = Color(0xFF8B7355),
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.Center),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selectedMessages) { message ->
                                AllianceChatBubble(
                                    message = message,
                                    isSelf = message.senderId == selfPlayerId
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入密谋内容") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedSession?.let { onSend(it.sessionId, draft) }
                    draft = ""
                },
                enabled = draft.isNotBlank() && selectedSession != null
            ) {
                Text("发送")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun AllianceChatBubble(
    message: ChatMessage,
    isSelf: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 240.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelf) Color(0xFF659B7A).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.8f))
                .border(1.dp, Color(0xFFE6D3A3), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                message.senderName,
                color = Color(0xFF8B7355),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                message.content,
                color = Color(0xFF5C4033),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun AllianceChatEntry(
    partnerName: String,
    unreadHint: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White.copy(alpha = 0.55f)
        )
    ) {
        Icon(Icons.Default.Markunread, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text("盟友私聊 · $partnerName", fontWeight = FontWeight.Bold)
            if (unreadHint.isNotBlank()) {
                Text(
                    unreadHint,
                    fontSize = 11.sp,
                    color = Color(0xFF8B7355),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PlayerRingsRow(
    players: List<Player>,
    selectedPlayer: Player,
    onPlayerSelect: (Player) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        players.forEach { player ->
            PlayerAvatarItem(
                player = player,
                isSelected = player.id == selectedPlayer.id,

                onClick = { onPlayerSelect(player) }
            )
        }
    }
}

@Composable
private fun SubmittedOverlay(
    isHost: Boolean = false,
    onRestart: () -> Unit = {}
) { StudioGlass(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFFD4AF37),
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "法旨已结印",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5C4033)
            )

            Text(
                "静待天机流转，道友请稍候...",
                fontSize = 14.sp,
                color = Color(0xFF8B7355),
                modifier = Modifier.padding(top = 8.dp)
            )
            if (isHost) {
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = onRestart) {
                    Text("时光回溯 (全员重选)", color = Color(0xFFB42318), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PhaseHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "第一纪元 · 谋定乾坤",
            color = Color(0xFF8B7355),
            fontSize = 13.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PlayerAvatarItem(
    player: Player,
    isSelected: Boolean,
    onClick: () -> Unit
) {

    val scale by animateFloatAsState(if (isSelected || player.isSelf) 1.1f else 0.95f, label = "")

    val borderColor = when {
        player.hasHeavenProtection -> Color(0xFF8B5CF6)
        player.isSelf -> Color(0xFF659B7A)
        isSelected -> Color(0xFFD4AF37)
        else -> Color(0xFFCDBA96)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(enabled = !player.isSelf, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f))
                    .border(if (isSelected || player.isSelf || player.hasHeavenProtection) 3.dp else 1.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(player.name.take(1), color = Color(0xFF5C4033), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .offset(y = (8).dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (player.hasHeavenProtection) Color(0xFF8B5CF6) else Color(0xFF5C4033).copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "灵脉 ${player.spiritVeins}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (player.hasHeavenProtection) {
                Text(
                    "✨",
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(player.name, fontSize = 13.sp, color = Color(0xFF5C4033))
        if (player.isBot) {
            Text("机器人", fontSize = 10.sp, color = Color(0xFF8B7355), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionPanel(
    targetPlayer: Player,
    selfPlayer: Player,
    isSubmitting: Boolean,
    isAlliedWithTarget: Boolean,
    dayNumber: Int,
    alliancePartner: Player?,
    alliancePlan: AllianceActionPlan?,
    canConspire: Boolean,
    canAlliance: Boolean,
    onConspiracyRequest: () -> Unit,
    onAllianceRequest: () -> Unit,
    onAllianceActionPlanPropose: (Player, ActionType, Int, Int, Int) -> Unit,
    onAllianceActionPlanConfirm: (AllianceActionPlan) -> Unit,
    onActionSubmit: (Player, ActionType, Int) -> Unit
) {
    var duelStake by remember { mutableFloatStateOf(10f) }
    var rewardShare by remember { mutableFloatStateOf(50f) }
    var penaltyShare by remember { mutableFloatStateOf(50f) }

    StudioGlass(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {

            Text(
                "对【${targetPlayer.name}】降下法旨",
                color = Color(0xFF8B7355),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            StudioGlass(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("斗法彩头 (灵脉)", color = Color(0xFF8B7355), fontSize = 13.sp)
                        Text(
                            "${duelStake.toInt()} 条",
                            color = Color(0xFFD4AF37),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Slider(
                        value = duelStake,
                        onValueChange = { duelStake = it },
                        valueRange = 0f..30f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFD4AF37),
                            activeTrackColor = Color(0xFFD4AF37),
                            inactiveTrackColor = Color(0xFFE6D3A3).copy(alpha = 0.4f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = onConspiracyRequest,
                enabled = !isSubmitting && canConspire,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (canConspire) "发起密谋邀请" else "密谋第 4 天开放")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onAllianceRequest,
                enabled = !isSubmitting && canAlliance && selfPlayer.alliancePartnerId == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (canAlliance) "发起结盟请求" else "同盟第 6 天开放")
            }

            if (isAlliedWithTarget) {
                Text(
                    "你们已结盟，本回合不能互相攻打",
                    color = Color(0xFF8B7355),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            if (alliancePartner != null) {
                Spacer(modifier = Modifier.height(16.dp))
                AllianceActionPlanPanel(
                    targetPlayer = targetPlayer,
                    alliancePartner = alliancePartner,
                    plan = alliancePlan,
                    rewardShare = rewardShare.toInt(),
                    penaltyShare = penaltyShare.toInt(),
                    onRewardShareChange = { rewardShare = it.toFloat() },
                    onPenaltyShareChange = { penaltyShare = it.toFloat() },
                    onPropose = { actionType ->
                        onAllianceActionPlanPropose(
                            targetPlayer,
                            actionType,
                            duelStake.toInt(),
                            rewardShare.toInt(),
                            penaltyShare.toInt()
                        )
                    },
                    onConfirm = onAllianceActionPlanConfirm
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlowActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Whatshot,
                    label = "登仙台斗法",
                    sub = "赢取彩头/服软费",
                    color = Color(0xFFD4AF37),
                    enabled = !isSubmitting && !isAlliedWithTarget
                ) {
                    onActionSubmit(targetPlayer, ActionType.DUEL, duelStake.toInt())
                }

                GlowActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.VisibilityOff,
                    label = "死士奇袭",
                    sub = "暗中窃取10条",
                    color = Color(0xFFC0625E),
                    enabled = !isSubmitting && !isAlliedWithTarget
                ) {
                    onActionSubmit(targetPlayer, ActionType.RAID, 0)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlowActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Shield,
                    label = "护宗大阵",
                    sub = "防守反噬20条",
                    color = Color(0xFF659B7A),
                    enabled = !isSubmitting
                ) {
                    onActionSubmit(targetPlayer, ActionType.DEFEND_ARRAY, 0)
                }

                GlowActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Explore,
                    label = "秘境寻宝",
                    sub = "投骰获取造化",
                    color = Color(0xFF8B5CF6),
                    enabled = !isSubmitting
                ) {
                    onActionSubmit(selfPlayer, ActionType.EXPLORE, 0)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = Color(0xFFB8A98A),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "长按阵眼灌注灵力，结印发送神念",
                    fontSize = 11.sp,
                    color = Color(0xFFB8A98A)
                )
            }
        }
    }
}

@Composable
private fun AllianceActionPlanPanel(
    targetPlayer: Player,
    alliancePartner: Player,
    plan: AllianceActionPlan?,
    rewardShare: Int,
    penaltyShare: Int,
    onRewardShareChange: (Int) -> Unit,
    onPenaltyShareChange: (Int) -> Unit,
    onPropose: (ActionType) -> Unit,
    onConfirm: (AllianceActionPlan) -> Unit
) {
    StudioGlass(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("同盟行动协定", color = Color(0xFF5C4033), fontWeight = FontWeight.Bold)
            Text("总奖励/惩罚 x3，按预先协定分配", color = Color(0xFF8B7355), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(12.dp))

            Text("奖励分配：你 $rewardShare% · ${alliancePartner.name} ${100 - rewardShare}%", color = Color(0xFF8B7355), fontSize = 12.sp)
            Slider(
                value = rewardShare.toFloat(),
                onValueChange = { onRewardShareChange(it.toInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFD4AF37), activeTrackColor = Color(0xFFD4AF37))
            )
            Text("惩罚分配：你 $penaltyShare% · ${alliancePartner.name} ${100 - penaltyShare}%", color = Color(0xFF8B7355), fontSize = 12.sp)
            Slider(
                value = penaltyShare.toFloat(),
                onValueChange = { onPenaltyShareChange(it.toInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFC0625E), activeTrackColor = Color(0xFFC0625E))
            )

            if (plan != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val confirmedText = "${plan.confirmedPlayerIds.size}/2 已确认"
                Text(
                    "当前方案：${plan.actionType.displayName()} → ${plan.targetName.ifBlank { targetPlayer.name }}，$confirmedText",
                    color = Color(0xFF5C4033),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { onConfirm(plan) },
                    enabled = !plan.isFullyConfirmed(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (plan.isFullyConfirmed()) "方案已锁定" else "确认当前同盟方案")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onPropose(ActionType.DUEL) }, modifier = Modifier.weight(1f)) {
                    Text("协定斗法")
                }
                OutlinedButton(onClick = { onPropose(ActionType.RAID) }, modifier = Modifier.weight(1f)) {
                    Text("协定奇袭")
                }
            }
        }
    }
}

private fun ActionType.displayName(): String {
    return when (this) {
        ActionType.DUEL -> "斗法"
        ActionType.RAID -> "奇袭"
        ActionType.DEFEND_ARRAY -> "防御"
        ActionType.EXPLORE -> "寻宝"
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    sub: String,
    bg: Color,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val progress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) bg else Color.LightGray.copy(alpha = 0.2f))
            .border(1.dp, if (enabled) color.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(14.dp))
            .drawWithContent {
                drawContent()
                if (enabled && progress.value > 0f) {
                    drawRect(color = color.copy(alpha = 0.2f), size = Size(size.width * progress.value, size.height))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    val job = coroutineScope.launch { progress.animateTo(1f, tween(1000, easing = LinearEasing)) }
                    tryAwaitRelease()
                    if (progress.value >= 1f) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                        coroutineScope.launch { progress.animateTo(0f, tween(300)) }
                    } else {
                        job.cancel()
                        coroutineScope.launch { progress.animateTo(0f, tween(200)) }
                    }
                })
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (enabled) color else Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, color = if (enabled) color else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(sub, fontSize = 10.sp, color = if (enabled) color.copy(alpha = 0.8f) else Color.Gray)
        }
    }
}

@Composable
private fun GlowActionButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    sub: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(modifier = modifier) {

        StudioGlow(Modifier.matchParentSize())

        ActionButton(
            modifier = Modifier.fillMaxWidth(),
            icon = icon,
            label = label,
            sub = sub,
            bg = Color.White.copy(alpha = 0.6f),
            color = color,
            enabled = enabled,
            onClick = onClick
        )
    }
}
