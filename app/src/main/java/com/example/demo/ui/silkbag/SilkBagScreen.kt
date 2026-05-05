package com.example.demo.ui.silkbag

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.demo.model.Player
import com.example.demo.model.PlayerStatus
import com.example.demo.model.SilkBagCatalog
import com.example.demo.model.SilkBagDefinition
import com.example.demo.model.SilkBagInstance

/**
 * 锦囊卡牌页，第一阶段和第二阶段共用。
 */
@Composable
fun SilkBagScreen(
    players: List<Player>,
    dayNumber: Int,
    sourcePhaseName: String,
    showCodex: Boolean,
    onCodexClick: () -> Unit,
    onUseSilkBag: (String, String?) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val selfPlayer = remember(players) { players.firstOrNull { it.isSelf } }
    val selfCards = selfPlayer?.silkBagCards.orEmpty()
    val visibleCards = remember(showCodex, selfCards) {
        if (showCodex) {
            SilkBagCatalog.definitions.map { VisibleSilkBagCard(it, null) }
        } else {
            selfCards.map { instance ->
                VisibleSilkBagCard(SilkBagCatalog.get(instance.cardId), instance)
            }
        }
    }
    val targets = remember(players) {
        players.filter { it.status != PlayerStatus.ELIMINATED }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFF2E2BE),
                        Color(0xFFE5C891),
                        Color(0xFFD7B174)
                    )
                )
            )
            .statusBarsPadding()
    ) {
        SilkBagHeader(
            selfPlayer = selfPlayer,
            dayNumber = dayNumber,
            sourcePhaseName = sourcePhaseName,
            showCodex = showCodex,
            onCodexClick = onCodexClick,
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (visibleCards.isEmpty()) {
                item { EmptySilkBagCard() }
            } else {
                items(visibleCards.chunked(2), key = { row -> row.joinToString("-") { it.key } }) { rowCards ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        rowCards.forEach { card ->
                            SilkBagCardView(
                                card = card,
                                showUseButton = !showCodex,
                                targets = targets,
                                onUseSilkBag = onUseSilkBag,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowCards.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SilkBagHeader(
    selfPlayer: Player?,
    dayNumber: Int,
    sourcePhaseName: String,
    showCodex: Boolean,
    onCodexClick: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color(0xFF5C4033))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (showCodex) "锦囊图鉴" else "锦囊",
                    color = Color(0xFF5C4033),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$sourcePhaseName · 第 $dayNumber 天",
                    color = Color(0xFF6E5435),
                    fontSize = 12.sp
                )
            }
            if (showCodex) {
                SilkBagCountPill(count = selfPlayer?.silkBagCards?.size ?: selfPlayer?.silkBag ?: 0)
            } else {
                SilkBagCodexButton(onClick = onCodexClick)
            }
        }
    }
}

@Composable
private fun SilkBagCodexButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFF8E8).copy(alpha = 0.86f))
            .border(1.dp, Color(0xFFA8733F), RoundedCornerShape(14.dp))
    ) {
        Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFFA8733F), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(6.dp))
        Text("锦囊图鉴", color = Color(0xFF5C4033), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SilkBagCountPill(count: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFF8E8).copy(alpha = 0.86f))
            .border(1.dp, Color(0xFFA8733F), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFFA8733F), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(6.dp))
        Text("我的锦囊 $count", color = Color(0xFF5C4033), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptySilkBagCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFC38A4C))
            .border(2.dp, Color(0xFF9B6735), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFFFFAEA))
                .border(1.dp, Color(0xFFB98A58), RoundedCornerShape(4.dp))
                .padding(horizontal = 20.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFFB98A58).copy(alpha = 0.58f), modifier = Modifier.size(42.dp))
            Spacer(modifier = Modifier.height(14.dp))
            Text("当前还没有锦囊", color = Color(0xFF7A4F2C), fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                text = "通过探索获得锦囊后，会在这里显示。",
                color = Color(0xFF6E5435),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SilkBagCardView(
    card: VisibleSilkBagCard,
    showUseButton: Boolean,
    targets: List<Player>,
    onUseSilkBag: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val definition = card.definition
    var selectedTargetId by remember(card.key, targets) {
        mutableStateOf(targets.firstOrNull { !it.isSelf }?.id ?: targets.firstOrNull()?.id)
    }

    Box(
        modifier = modifier
            .height(if (showUseButton) 430.dp else 360.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFC38A4C))
            .border(2.dp, Color(0xFF9B6735), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFFFFAEA))
                .border(1.dp, Color(0xFFB98A58), RoundedCornerShape(4.dp))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = definition.name,
                color = Color(0xFF7A4F2C),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFF4E8D5))
                    .border(1.dp, Color(0xFFB98A58), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFFB98A58).copy(alpha = 0.38f), modifier = Modifier.size(44.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "${definition.timingText}：",
                color = Color(0xFF7A4F2C),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Text(
                text = definition.description,
                color = Color(0xFF5C4033),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 5,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (definition.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "（${definition.note}）",
                    color = Color(0xFFA64B3F),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }

            if (showUseButton && card.instance != null) {
                Spacer(modifier = Modifier.height(8.dp))
                if (definition.needsTarget && targets.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        targets.take(3).forEach { target ->
                            OutlinedButton(
                                onClick = { selectedTargetId = target.id },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = if (target.id == selectedTargetId) "选中${target.name.take(2)}" else target.name.take(3),
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        onUseSilkBag(
                            card.instance.instanceId,
                            if (definition.needsTarget) selectedTargetId else null
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("使用锦囊", color = Color(0xFFA64B3F), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private data class VisibleSilkBagCard(
    val definition: SilkBagDefinition,
    val instance: SilkBagInstance?
) {
    val key: String = instance?.instanceId ?: "codex_${definition.number}"
}
