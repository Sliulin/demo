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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.demo.model.Player
import com.example.demo.ui.home.HomeScreen
import com.example.demo.ui.phase1.Phase1Screen
import com.example.demo.ui.phase2.Phase2Screen
import com.example.demo.ui.silkbag.SilkBagScreen
import com.example.demo.ui.theme.DemoTheme
import com.example.demo.viewmodel.GameViewModel
import com.example.demo.viewmodel.Screen

/**
 * 应用入口 Activity，承载 Compose 导航树。
 */
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

/**
 * 将 GameUiState 中的页面状态映射到 Compose 导航目的地。
 */
@Composable
fun AppNavigation(viewModel: GameViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.onAppForegrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                myPlayerName = uiState.myPlayerName,
                isTestModeEnabled = uiState.isTestModeEnabled,
                onUpdateTestMode = { enabled -> viewModel.updateTestModeEnabled(enabled) },
                onUpdateName = { newName -> viewModel.updatePlayerName(newName) },
                onRoomClick = { room -> viewModel.joinRoom(room) },
                onCreateRoom = { roomName -> viewModel.createRoom(roomName) }
            )
        }
        composable("waiting_room") {
            WaitingRoomScreen(
                players = uiState.players,
                isHost = uiState.isHost,
                onStartGame = { viewModel.startGame() },
                onLeaveRoom = { viewModel.leaveRoom() }
            )
        }

        composable("phase1") {
            Phase1Screen(
                players = uiState.players,
                hasSubmittedAction = uiState.hasSubmittedAction,
                incomingAllianceRequest = uiState.incomingAllianceRequest,
                incomingConspiracyRequest = uiState.incomingConspiracyRequest,
                conspiracySessions = uiState.conspiracySessions,
                allianceActionPlans = uiState.allianceActionPlans,
                allianceNotice = uiState.allianceNotice,
                silkBagNotice = uiState.silkBagNotice,
                conspiracyNotice = uiState.conspiracyNotice,
                chatMessages = uiState.chatMessages,
                dayNumber = uiState.dayNumber,
                isHost = uiState.isHost,
                isTestModeEnabled = uiState.isTestModeEnabled,
                submittedCount = uiState.submittedActions.size,
                debugNextDayNumber = uiState.debugNextDayNumber,
                debugNextDaySpiritVeins = uiState.debugNextDaySpiritVeins,
                onActionSubmit = { target, actionType, stake ->
                    viewModel.submitAction(target, actionType, stake)
                },
                onConspiracyRequest = { target ->
                    viewModel.requestConspiracy(target)
                },
                onConspiracyResponse = { request, accepted ->
                    viewModel.respondConspiracy(request, accepted)
                },
                onConspiracyChatSend = { sessionId, content ->
                    viewModel.sendConspiracyChat(sessionId, content)
                },
                onAllianceRequest = { target ->
                    viewModel.requestAlliance(target)
                },
                onAllianceResponse = { request, accepted ->
                    viewModel.respondAlliance(request, accepted)
                },
                onAllianceActionPlanPropose = { target, actionType, stake, rewardShare, penaltyShare ->
                    viewModel.proposeAllianceActionPlan(target, actionType, stake, rewardShare, penaltyShare)
                },
                onAllianceActionPlanConfirm = { plan ->
                    viewModel.confirmAllianceActionPlan(plan)
                },
                onAllianceChatSend = { content ->
                    viewModel.sendAllianceChat(content)
                },
                onDebugNextDayChange = { dayNumber ->
                    viewModel.setDebugNextDayNumber(dayNumber)
                },
                onDebugSpiritVeinsChange = { playerId, spiritVeins ->
                    viewModel.setDebugNextDaySpiritVeins(playerId, spiritVeins)
                },
                onClearDebugSettings = {
                    viewModel.clearDebugNextDaySettings()
                },
                onForceProceed = { viewModel.restartPhase1() },
                onSilkBagClick = { navController.navigate("silk_bag/第一阶段/my") }
            )
        }

        composable(route = "phase2") {
            val currentEvent = uiState.currentEvent ?: return@composable
            val self = uiState.players.find { it.isSelf } ?: return@composable

            Phase2Screen(
                eventQueue = uiState.eventQueue,
                currentEventIndex = uiState.currentEventIndex,
                systemBroadcast = uiState.systemBroadcast,
                silkBagNotice = uiState.silkBagNotice,
                isHost = uiState.isHost,
                selfPlayer = self,
                players = uiState.players,
                dayNumber = uiState.dayNumber,
                isGameOver = uiState.isGameOver,
                canProceedToNextDay = uiState.canProceedToNextDay,
                onHostDecideIndex = { index -> viewModel.submitHostDecisionIndex(index) },
                onVote = { isConfirm -> viewModel.castVote(isConfirm) },
                onAllianceBetrayal = { eventId -> viewModel.declareAllianceBetrayal(eventId) },
                onAllianceBetrayalResult = { eventId, betrayerId, succeeded ->
                    viewModel.submitAllianceBetrayalResult(eventId, betrayerId, succeeded)
                },
                onGhostInterfere = { isBlessing -> viewModel.submitGhostInterference(isBlessing) },
                onProceedToNextDay = { viewModel.proceedToNextDay() },
                onRestartEvent = { viewModel.restartCurrentEvent() },
                onSilkBagClick = { navController.navigate("silk_bag/第二阶段/my") }
            )
        }

        composable(route = "silk_bag/{sourcePhaseName}/{mode}") { backStackEntry ->
            val sourcePhaseName = backStackEntry.arguments?.getString("sourcePhaseName") ?: "当前阶段"
            SilkBagScreen(
                players = uiState.players,
                dayNumber = uiState.dayNumber,
                sourcePhaseName = sourcePhaseName,
                showCodex = backStackEntry.arguments?.getString("mode") == "codex",
                onCodexClick = { navController.navigate("silk_bag/$sourcePhaseName/codex") },
                onUseSilkBag = { instanceId, targetPlayerId ->
                    viewModel.useSilkBag(instanceId, targetPlayerId)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * 开局前的房间等待页。
 */
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
                    } else if (player.isBot) {
                        Text(text = " (机器人)", color = Color.Gray)
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
