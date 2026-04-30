package com.example.demo.ui.phase1

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.demo.model.Player
import com.example.demo.model.PlayerStatus
import com.example.demo.ui.ClickParticleLayer
import com.example.demo.ui.StudioBackground
import com.example.demo.ui.StudioGlass
import com.example.demo.ui.StudioGlow
import com.example.demo.ui.WaitingCard
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun Phase1Screen(
    players: List<Player>,
    hasSubmittedAction: Boolean,
    isHost: Boolean = false,              // ✨ 新增：房主标识
    submittedCount: Int = 0,              // ✨ 新增：已提交人数
    onActionSubmit: (Player, ActionType, Int) -> Unit,
    onForceProceed: () -> Unit = {}       // ✨ 新增：房主重置/补救操作回调
) {
    val context = LocalContext.current
    BackHandler(enabled = true) {
        Toast.makeText(context, "劫数未尽，本尊不可强行出关！", Toast.LENGTH_SHORT).show()
    }

    // 1. 定义“合法目标”列表（用于判断谁可以被攻击）
    val validTargets = remember(players) {
        players.filter { !it.isSelf && it.status != PlayerStatus.ELIMINATED }
    }

    // 2. 初始选择逻辑：优先选中第一个敌人，如果没敌人（比如只有自己）才选自己
    var selectedPlayer by remember(players) {
        mutableStateOf(validTargets.firstOrNull() ?: players.first { it.isSelf })
    }

    var isSubmitting by remember(hasSubmittedAction) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        StudioBackground()
        ClickParticleLayer()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // ✨ 修改：不再需要传入倒计时和进度参数
            PhaseHeader()

            Spacer(modifier = Modifier.height(24.dp))

            // 【核心修复 3】：只将存活的玩家传入头像选择行
            PlayerRingsRow(
                players = players,
                selectedPlayer = selectedPlayer,
                onPlayerSelect = { player ->
                    // 逻辑：只有点击“非本尊且存活”的玩家，才允许切换选中状态
                    if (!player.isSelf && player.status != PlayerStatus.ELIMINATED) {
                        selectedPlayer = player
                    } else if (player.isSelf) {
                        Toast.makeText(context, "不可对自己发动法门", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            if (hasSubmittedAction) {
                // ✨ 修改：传入房主参数和重置回调，以便在有人卡死时房主可以点击“时光回溯”
                SubmittedOverlay(
                    isHost = isHost,
                    onRestart = onForceProceed // 将回调传给内部的按钮
                )
            } else {
                // 如果场上没活人了（通常终局已拦截，但这里做个兜底）
                if (!selectedPlayer.isSelf) {
                    ActionPanel(
                        targetPlayer = selectedPlayer,
                        selfPlayer = players.first { it.isSelf },
                        isSubmitting = isSubmitting,
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

// ====== 【修复】：新增缺失的 PlayerRingsRow 函数 ======
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
                // 这里不要过滤，把点击事件传出去，让上层决定能不能选
                onClick = { onPlayerSelect(player) }
            )
        }
    }
}
// ====================================================

@Composable
private fun SubmittedOverlay(
    isHost: Boolean = false,
    onRestart: () -> Unit = {} // 新增回调
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

        // 稍微保留一点底部间距，让它和下方的玩家头像不至于贴得太紧
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

    // 天道庇佑的光效颜色：如果是庇佑状态，边框变淡紫色/金色流光
    val borderColor = when {
        player.hasHeavenProtection -> Color(0xFF8B5CF6) // 天道庇佑：紫色
        player.isSelf -> Color(0xFF659B7A)           // 本尊：绿色
        isSelected -> Color(0xFFD4AF37)              // 选中：金色
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

            // --- 修改位置：灵脉数值标签 ---
            Box(
                modifier = Modifier
                    .offset(y = (8).dp) // 往下挪一点
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

            // 天道庇佑的小角标
            if (player.hasHeavenProtection) {
                Text(
                    "✨",
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(player.name, fontSize = 13.sp, color = Color(0xFF5C4033))
    }
}

@Composable
private fun ActionPanel(
    targetPlayer: Player,
    selfPlayer: Player,
    isSubmitting: Boolean,
    onActionSubmit: (Player, ActionType, Int) -> Unit
) {
    var duelStake by remember { mutableFloatStateOf(10f) }

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

            // ===== 灵脉押注池（玻璃子卡片）=====
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

            // ===== 四大行动按钮 =====
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlowActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Whatshot,
                    label = "登仙台斗法",
                    sub = "赢取彩头/服软费",
                    color = Color(0xFFD4AF37),
                    enabled = !isSubmitting
                ) {
                    onActionSubmit(targetPlayer, ActionType.DUEL, duelStake.toInt())
                }

                GlowActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.VisibilityOff,
                    label = "死士奇袭",
                    sub = "暗中窃取10条",
                    color = Color(0xFFC0625E),
                    enabled = !isSubmitting
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

// 带有炫酷长按结印动画的按钮
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

        // ✨ 呼吸光
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