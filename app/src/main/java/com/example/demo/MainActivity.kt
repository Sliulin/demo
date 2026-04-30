package com.example.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.demo.model.Player
import com.example.demo.ui.home.HomeScreen
import com.example.demo.ui.phase1.Phase1Screen
import com.example.demo.ui.phase2.Phase2Screen
import com.example.demo.ui.theme.DemoTheme
import com.example.demo.viewmodel.GameViewModel
import com.example.demo.viewmodel.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: GameViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.currentScreen) {
        val route = when(uiState.currentScreen) {
            Screen.HOME -> "home"
            Screen.WAITING_ROOM -> "waiting_room"
            Screen.PHASE1 -> "phase1"
            Screen.PHASE2 -> "phase2"
        }
        if (navController.currentDestination?.route != route) {
            navController.navigate(route) {
                launchSingleTop = true
                // 【新增】：如果目标是回到首页，清空之前的导航栈，防止页面层层叠叠
                if (route == "home") {
                    popUpTo("home") { inclusive = false }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                rooms = uiState.rooms,
                isWifiConnected = uiState.isWifiConnected,
                isScanning = uiState.isScanning,
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refreshRooms() },
                myPlayerName = uiState.myPlayerName, // 【新增】传入全局昵称
                onUpdateName = { newName -> viewModel.updatePlayerName(newName) }, // 【新增】修改昵称的回调
                onRoomClick = { room -> viewModel.joinRoom(room) }, // 【修改】不再需要传名字
                onCreateRoom = { roomName -> viewModel.createRoom(roomName) } // 【修改】不再需要传名字
            )
        }
        composable("waiting_room") {
            WaitingRoomScreen(
                players = uiState.players,
                isHost = uiState.isHost,
                onStartGame = { viewModel.startGame() },
                onLeaveRoom = { viewModel.leaveRoom() } // 【新增】：触发 ViewModel 的清理逻辑
            )
        }

        composable("phase1") {
            Phase1Screen(
                players = uiState.players,
                hasSubmittedAction = uiState.hasSubmittedAction,
                isHost = uiState.isHost,
                submittedCount = uiState.submittedActions.size,
                onActionSubmit = { target, actionType, stake ->
                    viewModel.submitAction(target, actionType, stake)
                },
                onForceProceed = { viewModel.restartPhase1() } // 根据您采用的方案调用
            )
        }

        composable(route = "phase2") {
            val currentEvent = uiState.currentEvent ?: return@composable
            // 从玩家列表中找到“自己”
            val self = uiState.players.find { it.isSelf } ?: return@composable

            Phase2Screen(
                eventQueue = uiState.eventQueue, // 传入完整队列
                currentEventIndex = uiState.currentEventIndex,
                systemBroadcast = uiState.systemBroadcast,
                isHost = uiState.isHost,
                selfPlayer = self,
                players = uiState.players,
                dayNumber = uiState.dayNumber, // 把 viewModel 里的天数传进去
                isGameOver = uiState.isGameOver,
                canProceedToNextDay = uiState.canProceedToNextDay,
                onHostDecideIndex = { index -> viewModel.submitHostDecisionIndex(index) },
                onVote = { isConfirm -> viewModel.castVote(isConfirm) },
                onGhostInterfere = { isBlessing -> viewModel.submitGhostInterference(isBlessing) },
                onProceedToNextDay = { viewModel.proceedToNextDay() },
                onRestartEvent = { viewModel.restartCurrentEvent() }
            )
        }
    }
}

@Composable
private fun WaitingRoomScreen(
    players: List<Player>,
    isHost: Boolean,
    onStartGame: () -> Unit,
    onLeaveRoom: () -> Unit
) {

    BackHandler {
        onLeaveRoom()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F7))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "等待室", style = MaterialTheme.typography.headlineLarge)

        Spacer(modifier = Modifier.height(24.dp))

        players.forEach { player ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = player.name, fontWeight = FontWeight.Bold)
                    if (player.isHost) {
                        Text(text = " (房主)", color = Color.Gray)
                    }
                }
            }
        }

        if (isHost) {
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onStartGame) {
                Text("开始游戏")
            }
        }
    }
}
