package com.example.demo.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.demo.model.GameRoom

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    rooms: List<GameRoom>,
    isWifiConnected: Boolean = true,
    isScanning: Boolean = true,
    isRefreshing: Boolean = false, // 【新增】
    onRefresh: () -> Unit,        // 【新增】
    myPlayerName: String,
    onUpdateName: (String) -> Unit,
    onRoomClick: (GameRoom) -> Unit,
    onCreateRoom: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var roomNameInput by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var tempNameInput by remember { mutableStateOf(myPlayerName) }

    Scaffold(
        containerColor = Color(0xFFF5F5F7),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "寻找战局", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1C1E))
                    IconButton(onClick = {
                        tempNameInput = myPlayerName
                        showSettingsDialog = true
                    }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "设置", tint = Color(0xFF5856D6))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(if (isWifiConnected) Color(0xFF34C759) else Color(0xFFFF3B30), CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRefreshing) "正在重新扫描房间..." else "当前昵称: $myPlayerName",
                        fontSize = 13.sp,
                        color = Color(0xFF8E8E93)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            // 【核心修改】：使用 PullToRefreshBox 包裹列表
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = paddingValues
                ) {
                    items(rooms) { room ->
                        RoomCardItem(room = room, onClick = { onRoomClick(room) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            CreateRoomButton(onClick = { showCreateDialog = true })
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 设置和创建房间的弹窗保持不变...
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text(text = "游戏设置", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(value = tempNameInput, onValueChange = { tempNameInput = it }, label = { Text("修改你的昵称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = { onUpdateName(if (tempNameInput.isNotBlank()) tempNameInput else "神秘玩家"); showSettingsDialog = false }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("取消", color = Color.Gray) }
            }
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(text = "创建新房间", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(value = roomNameInput, onValueChange = { roomNameInput = it }, label = { Text("房间名称 (如：周末桌游局)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = { onCreateRoom(if (roomNameInput.isNotBlank()) roomNameInput else "我的神秘房间"); showCreateDialog = false; roomNameInput = "" }) { Text("确认创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消", color = Color.Gray) }
            }
        )
    }
}

@Composable
private fun RoomCardItem(room: GameRoom, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Brush.radialGradient(colors = listOf(Color(0xFFE8E0FF), Color(0xFFD4C6F9)))), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF5856D6), modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = room.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1C1C1E))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "房主: ${room.host.name} · ${room.currentPlayers}/${room.maxPlayers}人", fontSize = 12.sp, color = Color(0xFF8E8E93))
        }
        Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFF0ECFF)), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF5856D6), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CreateRoomButton(onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF1C1C1E)).clickable(onClick = onClick).padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = "创建新房间", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}