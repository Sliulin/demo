package com.example.demo.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.engine.AllianceCheckResult
import com.example.demo.engine.AllianceRuleEngine
import com.example.demo.engine.GameRuleEngine
import com.example.demo.model.*
import com.example.demo.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Compose 页面消费的不可变 UI 状态快照。
 */
data class GameUiState(
    val currentScreen: Screen = Screen.HOME,
    val isWifiConnected: Boolean = true,
    val isScanning: Boolean = true,
    val rooms: List<GameRoom> = emptyList(),
    val players: List<Player> = emptyList(),
    val currentPhase: GamePhase = GamePhase.PHASE_1,
    val countdownSeconds: Int = 10,
    val selectedTargetPlayer: Player? = null,
    val currentEvent: GameEvent? = null,
    val systemBroadcast: String = "",
    val chatMessages: List<ChatMessage> = emptyList(),
    val dayNumber: Int = 1,
    val eventQueue: List<GameEvent> = emptyList(),
    val currentEventIndex: Int = 0,
    val isHost: Boolean = false,
    val isRefreshing: Boolean = false,
    val myPlayerName: String = "神秘玩家",
    val isTestModeEnabled: Boolean = false,
    val submittedActions: Map<String, MsgSubmitAction> = emptyMap(),
    val isGameOver: Boolean = false,
    val canProceedToNextDay: Boolean = false,
    val hasSubmittedAction: Boolean = false,
    val pendingAllianceRequests: List<AllianceRequest> = emptyList(),
    val incomingAllianceRequest: AllianceRequest? = null,
    val allianceNotice: String = "",
    val pendingConspiracyRequests: List<ConspiracyRequest> = emptyList(),
    val incomingConspiracyRequest: ConspiracyRequest? = null,
    val conspiracySessions: List<ConspiracySession> = emptyList(),
    val conspiracyNotice: String = "",
    val allianceActionPlans: List<AllianceActionPlan> = emptyList(),
    val silkBagNotice: String = "",
    val debugNextDayNumber: Int? = null,
    val debugNextDaySpiritVeins: Map<String, Int> = emptyMap()
)

/**
 * 由 GameUiState 驱动的导航目的地。
 */
enum class Screen { HOME, PHASE1, PHASE2, WAITING_ROOM }

/**
 * 统筹房间发现、Socket 消息、游戏流程和 UI 状态更新。
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val sharedPreferences = application.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
    private val KEY_PLAYER_NAME = "saved_player_name"
    private val KEY_TEST_MODE = "test_mode_enabled"

    private val nsdHelper = GameNsdHelper(application)
    private var gameServer: GameServer? = null
    private val gameClient = GameClient(
        onMessageReceived = { msg -> handleNetworkMessage(msg) },
        onDisconnected = { scheduleClientReconnect() }
    )

    private var myPlayerId: String = ""
    private var joinedRoomIp: String? = null
    private var joinedRoomPort: Int = 9999
    private var settlementJob: Job? = null
    private var reconnectJob: Job? = null
    private val isConspiracyOpen: Boolean
        get() = _uiState.value.dayNumber > 3
    private val isAllianceOpen: Boolean
        get() = _uiState.value.dayNumber > 5

    init {

        val savedName = sharedPreferences.getString(KEY_PLAYER_NAME, "神秘玩家") ?: "神秘玩家"
        val savedTestMode = sharedPreferences.getBoolean(KEY_TEST_MODE, false)
        _uiState.update { it.copy(myPlayerName = savedName, isTestModeEnabled = savedTestMode) }

        viewModelScope.launch {
            nsdHelper.discoveredRooms.collect { discoveredMap ->
                val realRooms = discoveredMap.values.map { nsdRoom ->
                    GameRoom(
                        id = "real_${nsdRoom.ip}_${nsdRoom.port}",
                        name = nsdRoom.roomName,
                        host = Player("host", nsdRoom.hostName, isHost = true),
                        currentPlayers = nsdRoom.currentPlayers,
                        maxPlayers = nsdRoom.maxPlayers,
                        ip = nsdRoom.ip,
                        port = nsdRoom.port
                    )
                }
                _uiState.update { it.copy(rooms = realRooms) }
            }
        }
        nsdHelper.startScanning()
    }

    /**
     * 持久化本机显示名称，并刷新 UI 状态。
     */
    fun updatePlayerName(newName: String) {
        _uiState.update { it.copy(myPlayerName = newName) }
        sharedPreferences.edit().putString(KEY_PLAYER_NAME, newName).apply()
    }

    /**
     * 持久化测试模式开关，并刷新 UI 状态。
     */
    fun updateTestModeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isTestModeEnabled = enabled) }
        sharedPreferences.edit().putBoolean(KEY_TEST_MODE, enabled).apply()
    }

    /**
     * 重启 NSD 扫描，并短暂展示刷新状态。
     */
    fun refreshRooms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            nsdHelper.stopEverything()
            nsdHelper.startScanning()
            delay(1500)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * App 回到前台时恢复房主或客机的网络工作。
     */
    fun onAppForegrounded() {
        val state = _uiState.value
        if (state.isHost && state.currentScreen != Screen.HOME) {
            ensureHostServerRunning()
            return
        }

        if (!state.isHost && state.currentScreen != Screen.HOME) {
            scheduleClientReconnect(immediate = true)
        }
    }

    /**
     * 创建房主房间，启动 TCP 服务端，并通过 NSD 广播。
     */
    fun createRoom(roomName: String) {
        myPlayerId = "host_${System.currentTimeMillis()}"
        val globalName = _uiState.value.myPlayerName
        val host = Player(id = myPlayerId, name = globalName, isHost = true, isSelf = true)
        val testBots = if (_uiState.value.isTestModeEnabled) createTestBots() else emptyList()

        joinedRoomIp = null
        reconnectJob?.cancel()
        _uiState.update { it.copy(isHost = true, players = listOf(host) + testBots) }

        gameServer = GameServer(onMessageReceived = { msg -> handleNetworkMessage(msg) })
        ensureHostServerRunning()

        nsdHelper.stopEverything()
        nsdHelper.startBroadcasting(roomName, globalName, 9999)
        _uiState.update { it.copy(currentScreen = Screen.WAITING_ROOM) }
    }

    private fun createTestBots(): List<Player> {
        return listOf(
            Player(id = "bot_training_duel", name = "测试傀儡甲", isBot = true, spiritVeins = 120),
            Player(id = "bot_training_raid", name = "测试傀儡乙", isBot = true, spiritVeins = 90)
        )
    }

    /**
     * 连接已发现的房主房间，并发送加入请求。
     */
    fun joinRoom(room: GameRoom) {
        myPlayerId = "player_${System.currentTimeMillis()}"
        joinedRoomIp = room.ip
        joinedRoomPort = room.port
        _uiState.update { it.copy(isHost = false) }
        nsdHelper.stopEverything()

        viewModelScope.launch {
            if (room.ip.isNotEmpty()) {
                val connected = connectToHostAndJoin()
                if (connected) {
                    _uiState.update { it.copy(currentScreen = Screen.WAITING_ROOM) }
                } else {
                    _uiState.update { it.copy(systemBroadcast = "错误：无法连接到房主") }
                }
            }
        }
    }

    /**
     * 从等待房间开始第 1 天，并广播初始权威状态。
     */
    fun startGame() {

        val updatedPlayers = GameRuleEngine.assignHeavenProtection(_uiState.value.players)
        _uiState.update { it.copy(players = updatedPlayers) }

        val msg = "—— 第 1 天 · 气机复苏 ——"
        val startMsg = MsgGameStart(GamePhase.PHASE_1, msg, 1)

        handleNetworkMessage(startMsg)
        viewModelScope.launch {
            gameServer?.broadcast(MsgRoomStateSync(updatedPlayers, msg))
            gameServer?.broadcast(startMsg)
        }
    }

    /**
     * 断开当前房间连接，并回到房间发现。
     */
    fun leaveRoom() {
        reconnectJob?.cancel()
        reconnectJob = null
        joinedRoomIp = null
        gameClient.disconnect()
        gameServer?.stop()
        gameServer = null
        nsdHelper.startScanning()
        _uiState.update { it.copy(currentScreen = Screen.HOME, isHost = false, players = emptyList()) }
    }

    /**
     * 向房主提交当前玩家的一阶段行动。
     */
    fun submitAction(targetPlayer: Player, actionType: ActionType, stake: Int = 0) {
        if (AllianceRuleEngine.areAllied(_uiState.value.players, myPlayerId, targetPlayer.id) &&
            (actionType == ActionType.DUEL || actionType == ActionType.RAID)
        ) {
            _uiState.update { it.copy(allianceNotice = "盟友之间不能互相攻打") }
            return
        }

        val actionTargetId = if (actionType == ActionType.DEFEND_ARRAY) myPlayerId else targetPlayer.id
        val msg = MsgSubmitAction(
            attackerId = myPlayerId,
            targetId = actionTargetId,
            actionType = actionType,
            stake = stake
        )

        _uiState.update { it.copy(hasSubmittedAction = true) }

        if (_uiState.value.isHost) {
            handleNetworkMessage(msg)
        } else {
            sendToServer(msg)
        }
    }

    /**
     * 请求与另一名玩家建立密谋私聊。
     */
    fun requestConspiracy(targetPlayer: Player) {
        val self = _uiState.value.players.find { it.id == myPlayerId } ?: return
        val msg = MsgConspiracyRequest(
            requestId = "conspiracy_${System.currentTimeMillis()}_$myPlayerId",
            fromPlayerId = myPlayerId,
            fromPlayerName = self.name,
            toPlayerId = targetPlayer.id
        )

        _uiState.update { it.copy(conspiracyNotice = "密谋邀请已送出") }
        if (_uiState.value.isHost) {
            handleNetworkMessage(msg)
        } else {
            sendToServer(msg)
        }
    }

    /**
     * 回应收到的密谋邀请。
     */
    fun respondConspiracy(request: ConspiracyRequest, accepted: Boolean) {
        val msg = MsgConspiracyResponse(
            requestId = request.requestId,
            fromPlayerId = request.fromPlayerId,
            toPlayerId = request.toPlayerId,
            accepted = accepted
        )

        _uiState.update { it.copy(incomingConspiracyRequest = null) }
        if (_uiState.value.isHost) {
            handleNetworkMessage(msg)
        } else {
            sendToServer(msg)
        }
    }

    /**
     * 在已有密谋会话中发送消息。
     */
    fun sendConspiracyChat(sessionId: String, content: String) {
        val trimmedContent = content.trim()
        if (trimmedContent.isEmpty()) return

        val state = _uiState.value
        val session = state.conspiracySessions.find { it.sessionId == sessionId && it.includes(myPlayerId) }
        if (session == null) {
            _uiState.update { it.copy(conspiracyNotice = "密谋会话不存在") }
            return
        }
        val self = state.players.find { it.id == myPlayerId } ?: return
        val recipientId = session.partnerIdFor(myPlayerId) ?: return

        val msg = MsgChat(
            senderId = myPlayerId,
            senderName = self.name,
            content = trimmedContent,
            isAllianceChat = false,
            recipientPlayerId = recipientId,
            isConspiracyChat = true,
            conspiracySessionId = session.sessionId
        )

        if (state.isHost) {
            handleNetworkMessage(msg)
        } else {
            sendToServer(msg)
        }
    }

    /**
     * 请求与另一名存活玩家建立临时同盟。
     */
    fun requestAlliance(targetPlayer: Player) {
        if (!isAllianceOpen) {
            _uiState.update { it.copy(allianceNotice = "完成第 5 回合后开放同盟") }
            return
        }
        val self = _uiState.value.players.find { it.id == myPlayerId } ?: return
        val msg = MsgAllianceRequest(
            requestId = "alliance_${System.currentTimeMillis()}_$myPlayerId",
            fromPlayerId = myPlayerId,
            fromPlayerName = self.name,
            toPlayerId = targetPlayer.id
        )

        _uiState.update { it.copy(allianceNotice = "结盟请求已送出，等待回应") }
        if (_uiState.value.isHost) {
            handleNetworkMessage(msg)
        } else {
            sendToServer(msg)
        }
    }

    /**
     * 为当前同盟双方提出共享的一阶段行动方案。
     */
    fun proposeAllianceActionPlan(
        targetPlayer: Player,
        actionType: ActionType,
        stake: Int,
        rewardShareSelf: Int,
        penaltyShareSelf: Int
    ) {
        val state = _uiState.value
        val self = state.players.find { it.id == myPlayerId } ?: return
        val partnerId = self.alliancePartnerId
        val partner = state.players.find { it.id == partnerId }
        if (partnerId == null) {
            _uiState.update { it.copy(allianceNotice = "结盟后才能协定同盟行动") }
            return
        }
        if (targetPlayer.id == partnerId || targetPlayer.id == myPlayerId) {
            _uiState.update { it.copy(allianceNotice = "同盟行动需要选择第三方目标") }
            return
        }

        if (actionType !in setOf(ActionType.RAID, ActionType.DEFEND_ARRAY, ActionType.EXPLORE)) {
            _uiState.update { it.copy(allianceNotice = "同盟后只能选择奇袭、防御、探索") }
            return
        }

        val firstId = self.id
        val secondId = partnerId
        val plan = AllianceActionPlan(
            allianceId = "alliance_action_${state.dayNumber}_${listOf(firstId, secondId).sorted().joinToString("_")}",
            firstPlayerId = firstId,
            secondPlayerId = secondId,
            actionType = actionType,
            targetId = targetPlayer.id,
            targetName = targetPlayer.name,
            stake = stake,
            rewardShareFirst = rewardShareSelf.coerceIn(0, 100),
            rewardShareSecond = 100 - rewardShareSelf.coerceIn(0, 100),
            penaltyShareFirst = penaltyShareSelf.coerceIn(0, 100),
            penaltyShareSecond = 100 - penaltyShareSelf.coerceIn(0, 100),
            confirmedPlayerIds = listOf(myPlayerId)
        )
        val planForDispatch = if (partner?.isBot == true) {
            plan.copy(confirmedPlayerIds = listOf(myPlayerId, partnerId))
        } else {
            plan
        }
        val msg = MsgAllianceActionPlanUpdate(planForDispatch, "同盟行动方案已提交，等待盟友确认")

        _uiState.update { it.copy(allianceNotice = msg.notice) }
        if (state.isHost) {
            handleNetworkMessage(msg)
        } else {
            sendToServer(msg)
        }
    }

    /**
     * 确认当前同盟行动方案。
     */
    fun confirmAllianceActionPlan(plan: AllianceActionPlan) {
        val confirmed = if (plan.confirmedPlayerIds.contains(myPlayerId)) {
            plan
        } else {
            plan.copy(confirmedPlayerIds = plan.confirmedPlayerIds + myPlayerId)
        }
        val msg = MsgAllianceActionPlanUpdate(confirmed, "同盟行动已确认")

        if (_uiState.value.isHost) {
            handleNetworkMessage(msg)
        } else {
            sendToServer(msg)
        }
    }

    /**
     * 回应收到的结盟邀请。
     */
    fun respondAlliance(request: AllianceRequest, accepted: Boolean) {
        val msg = MsgAllianceResponse(
            requestId = request.requestId,
            fromPlayerId = request.fromPlayerId,
            toPlayerId = request.toPlayerId,
            accepted = accepted
        )

        _uiState.update { it.copy(incomingAllianceRequest = null) }
        if (_uiState.value.isHost) {
            handleNetworkMessage(msg)
        } else {
            sendToServer(msg)
        }
    }

    /**
     * 向当前盟友发送同盟私聊消息。
     */
    fun sendAllianceChat(content: String) {
        val trimmedContent = content.trim()
        if (trimmedContent.isEmpty()) return

        val self = _uiState.value.players.find { it.id == myPlayerId } ?: return
        val partnerId = self.alliancePartnerId
        if (partnerId == null) {
            _uiState.update { it.copy(allianceNotice = "结盟后才能发送盟友私聊") }
            return
        }

        val msg = MsgChat(
            senderId = myPlayerId,
            senderName = self.name,
            content = trimmedContent,
            isAllianceChat = true,
            recipientPlayerId = partnerId
        )

        if (_uiState.value.isHost) {
            handleNetworkMessage(msg)
        } else {
            sendToServer(msg)
        }
    }

    /**
     * 锁定房主对当前二阶段事件的判定。
     */
    fun submitHostDecisionIndex(index: Int) {
        val state = _uiState.value
        val currentEvent = state.currentEvent ?: return
        val requiredConfirmCount = state.players.count { it.status != PlayerStatus.ELIMINATED && !it.isBot }
        val shouldAutoConfirm = requiredConfirmCount <= 1
        val updatedEvent = currentEvent.copy(
            hostDecisionIndex = index,
            confirmCount = if (shouldAutoConfirm) 0 else 1
        )

        updateCurrentEventLocally(updatedEvent)

        viewModelScope.launch {
            gameServer?.broadcast(
                MsgEventSync(state.currentEventIndex, updatedEvent, state.systemBroadcast)
            )
        }

        if (shouldAutoConfirm) {
            handleNetworkMessage(MsgVote(playerId = myPlayerId, isConfirm = true))
        }
    }

    /**
     * 声明当前同盟结算事件中的反水意图。
     */
    fun declareAllianceBetrayal(eventId: String) {
        val msg = MsgAllianceBetrayal(eventId, myPlayerId)
        if (_uiState.value.isHost) {
            handleNetworkMessage(msg)
        } else {
            sendToServer(msg)
        }
    }

    /**
     * 由房主提交同盟反水的线下判定结果。
     */
    fun submitAllianceBetrayalResult(eventId: String, betrayerId: String, betrayalSucceeded: Boolean) {
        val state = _uiState.value
        val event = state.eventQueue.find { it.id == eventId } ?: return
        val partnerId = event.allianceActionPlan
            ?.let { plan -> if (plan.firstPlayerId == betrayerId) plan.secondPlayerId else plan.firstPlayerId }
            ?: return
        val winnerId = if (betrayalSucceeded) betrayerId else partnerId
        val msg = MsgAllianceBetrayalResult(
            eventId = eventId,
            betrayerId = betrayerId,
            winnerId = winnerId,
            succeeded = betrayalSucceeded,
            notice = if (betrayalSucceeded) "反水成功，结算归属已改写" else "反水失败，维持原同盟结算"
        )

        if (state.isHost) {
            handleNetworkMessage(msg)
        } else {
            sendToServer(msg)
        }
    }

    fun castVote(isConfirm: Boolean) {
        sendToServer(MsgVote(playerId = myPlayerId, isConfirm = isConfirm))
    }

    /**
     * 请求使用一张具体锦囊。
     */
    fun useSilkBag(instanceId: String, targetPlayerId: String? = null) {
        val eventId = _uiState.value.currentEvent?.id
        val msg = MsgUseSilkBag(
            SilkBagUseRequest(
                playerId = myPlayerId,
                instanceId = instanceId,
                targetPlayerId = targetPlayerId,
                eventId = eventId
            )
        )
        if (_uiState.value.isHost) {
            handleNetworkMessage(msg)
        } else {
            sendToServer(msg)
        }
    }

    /**
     * 淘汰玩家对当前事件提交幽魂干预。
     */
    fun submitGhostInterference(isBlessing: Boolean) {
        viewModelScope.launch {
            val msg = MsgGhostInterference(ghostId = myPlayerId, isBlessing = isBlessing, power = 5)
            if (_uiState.value.isHost) handleNetworkMessage(msg) else sendToServer(msg)
            _uiState.update { it.copy(systemBroadcast = "【神念】你已降下${if (isBlessing) "福泽" else "业障"}...") }
        }
    }

    /**
     * 在房主结算冷却结束后推进到下一天。
     */
    fun proceedToNextDay() {
        val state = _uiState.value

        if (state.isHost && state.canProceedToNextDay && !state.isGameOver) {
            settlementJob?.cancel()
            startNextDay()
        }
    }

    /**
     * 设置测试模式下下一天的天数。
     */
    fun setDebugNextDayNumber(dayNumber: Int?) {
        if (!_uiState.value.isHost || _uiState.value.currentPhase != GamePhase.PHASE_1) return
        _uiState.update { state ->
            state.copy(debugNextDayNumber = dayNumber?.coerceAtLeast(state.dayNumber + 1))
        }
    }

    /**
     * 设置测试模式下指定玩家下一天的灵脉。
     */
    fun setDebugNextDaySpiritVeins(playerId: String, spiritVeins: Int?) {
        if (!_uiState.value.isHost || _uiState.value.currentPhase != GamePhase.PHASE_1) return
        _uiState.update { state ->
            val nextValues = state.debugNextDaySpiritVeins.toMutableMap()
            if (spiritVeins == null) {
                nextValues.remove(playerId)
            } else {
                nextValues[playerId] = spiritVeins.coerceAtLeast(0)
            }
            state.copy(debugNextDaySpiritVeins = nextValues)
        }
    }

    /**
     * 清空测试模式下的下一天调试配置。
     */
    fun clearDebugNextDaySettings() {
        if (!_uiState.value.isHost) return
        _uiState.update {
            it.copy(
                debugNextDayNumber = null,
                debugNextDaySpiritVeins = emptyMap()
            )
        }
    }

    /**
     * 重新开启当天一阶段，并清空临时社交和行动状态。
     */
    fun restartPhase1() {
        if (!_uiState.value.isHost) return
        val msg = "【天机重塑】房主开启了时光回溯，请重新下达法旨！"
        val currentState = _uiState.value
        val cleanPlayers = AllianceRuleEngine.clearAlliances(currentState.players)
        _uiState.update { it.copy(
            players = cleanPlayers,
            submittedActions = emptyMap(),
            hasSubmittedAction = false,
            pendingAllianceRequests = emptyList(),
            incomingAllianceRequest = null,
            allianceNotice = "",
            pendingConspiracyRequests = emptyList(),
            incomingConspiracyRequest = null,
            conspiracySessions = emptyList(),
            conspiracyNotice = "",
            allianceActionPlans = emptyList(),
            chatMessages = it.chatMessages.filterNot { message -> message.isAllianceChat || message.isConspiracyChat },
            systemBroadcast = msg
        ) }

        broadcastToAll(
            MsgRoomStateSync(cleanPlayers, msg, emptyList()),
            MsgGameStart(GamePhase.PHASE_1, msg, currentState.dayNumber)
        )
    }

    /**
     * 重置当前二阶段事件，但保留完整事件队列。
     */
    fun restartCurrentEvent() {
        if (!_uiState.value.isHost) return
        val state = _uiState.value
        val currentEvent = state.currentEvent ?: return

        val safeDecisionIndex = if (currentEvent.isSystemDetermined) currentEvent.hostDecisionIndex else null

        val resetEvent = currentEvent.copy(
            hostDecisionIndex = safeDecisionIndex,
            confirmCount = 0,
            betrayerId = null,
            betrayalWinnerId = null,
            betrayalSucceeded = null
        )

        val alertMsg = "【天机重塑】房主推翻了当前判定，重新审理！"

        updateCurrentEventLocally(resetEvent, alertMsg)

        broadcastToAll(MsgEventSync(state.currentEventIndex, resetEvent, alertMsg))
    }

    /**
     * 本地与远端协议消息的统一分发入口。
     */
    private fun handleNetworkMessage(message: NetworkMessage) {
        when (message) {
            is MsgRoomStateSync -> {
                val localPlayers = GameRuleEngine.run { message.players.syncSilkBagCounts() }
                    .map { it.copy(isSelf = (it.id == myPlayerId)) }
                _uiState.update { it.copy(
                    players = localPlayers,
                    systemBroadcast = message.systemBroadcast,

                    eventQueue = if (message.eventQueue.isNotEmpty()) message.eventQueue else it.eventQueue
                ) }
            }

            is MsgEventSync -> {

                _uiState.update { state ->
                    val newQueue = state.eventQueue.toMutableList()
                    if (message.currentEventIndex in newQueue.indices) {
                        newQueue[message.currentEventIndex] = message.event
                    }
                    state.copy(
                        currentScreen = Screen.PHASE2,
                        currentPhase = GamePhase.PHASE_2,
                        currentEventIndex = message.currentEventIndex,
                        currentEvent = message.event,
                        eventQueue = newQueue,
                        systemBroadcast = message.systemBroadcast
                    )
                }
            }

            is MsgPrivateSilkBagResult -> {
                if (message.recipientPlayerId == myPlayerId) {
                    _uiState.update { it.copy(silkBagNotice = message.notice) }
                }
            }

            is MsgSilkBagResult -> {
                if (message.recipientPlayerId == null || message.recipientPlayerId == myPlayerId) {
                    val localPlayers = GameRuleEngine.run { message.players.syncSilkBagCounts() }
                        .map { it.copy(isSelf = (it.id == myPlayerId)) }
                    _uiState.update { state ->
                        val newQueue = if (message.eventQueue.isNotEmpty()) {
                            message.eventQueue
                        } else {
                            state.eventQueue
                        }.toMutableList()
                        message.currentEvent?.let { event ->
                            if (message.currentEventIndex in newQueue.indices) {
                                newQueue[message.currentEventIndex] = event
                            }
                        }
                        state.copy(
                            players = localPlayers,
                            eventQueue = newQueue,
                            currentEvent = message.currentEvent ?: state.currentEvent,
                            systemBroadcast = message.systemBroadcast,
                            silkBagNotice = message.systemBroadcast
                        )
                    }
                }
            }

            is MsgGameStart -> {
                _uiState.update { state ->
                    val cleanPlayers = GameRuleEngine.run {
                        resetDailySilkBagEffects(AllianceRuleEngine.clearAlliances(state.players)).syncSilkBagCounts()
                    }
                    state.copy(
                        currentPhase = message.initialPhase,
                        currentScreen = Screen.PHASE1,
                        dayNumber = message.dayNumber,
                        players = cleanPlayers,
                        selectedTargetPlayer = cleanPlayers.firstOrNull { it.id != myPlayerId },
                        hasSubmittedAction = false,
                        submittedActions = emptyMap(),
                        pendingAllianceRequests = emptyList(),
                        incomingAllianceRequest = null,
                        allianceNotice = "",
                        pendingConspiracyRequests = emptyList(),
                        incomingConspiracyRequest = null,
                        conspiracySessions = emptyList(),
                        conspiracyNotice = "",
                        allianceActionPlans = emptyList(),
                        silkBagNotice = "",
                        chatMessages = state.chatMessages.filterNot { it.isAllianceChat || it.isConspiracyChat },
                        systemBroadcast = message.systemBroadcast
                    )
                }
            }

            is MsgSubmitAction -> {
                if (_uiState.value.isHost) {
                    val jailedTarget = _uiState.value.players.firstOrNull { player ->
                        player.activeSilkBagEffects.any {
                            it.cardId == SilkBagId.HUA_DI_WEI_LAO &&
                                it.targetPlayerId == message.attackerId &&
                                !it.isConsumed
                        }
                    }
                    if (jailedTarget != null) {
                        broadcastToAll(MsgActionRejected(message.attackerId, "你本回合被画地为牢，不能提交行动"))
                        if (message.attackerId == myPlayerId) {
                            _uiState.update {
                                it.copy(
                                    hasSubmittedAction = false,
                                    silkBagNotice = "你本回合被画地为牢，不能提交行动"
                                )
                            }
                        }
                        return
                    }
                    val actor = _uiState.value.players.find { it.id == message.attackerId }
                    if (actor?.activeSilkBagEffects?.any {
                            it.cardId == SilkBagId.XIU_YANG_SHENG_XI &&
                                message.actionType != ActionType.EXPLORE &&
                                !it.isConsumed
                        } == true
                    ) {
                        broadcastToAll(MsgActionRejected(message.attackerId, "休养生息期间只能选择探索"))
                        if (message.attackerId == myPlayerId) {
                            _uiState.update {
                                it.copy(
                                    hasSubmittedAction = false,
                                    silkBagNotice = "休养生息期间只能选择探索"
                                )
                            }
                        }
                        return
                    }
                    if (actor?.activeSilkBagEffects?.any {
                            it.cardId == SilkBagId.MOU_DING_ER_DONG &&
                                it.dayNumber == _uiState.value.dayNumber &&
                                !it.isConsumed
                        } == true
                    ) {
                        broadcastToAll(MsgActionRejected(message.attackerId, "谋定而动生效，本回合不可提交行动"))
                        if (message.attackerId == myPlayerId) {
                            _uiState.update {
                                it.copy(
                                    hasSubmittedAction = false,
                                    silkBagNotice = "谋定而动生效，本回合不可提交行动"
                                )
                            }
                        }
                        return
                    }
                    if (AllianceRuleEngine.areAllied(_uiState.value.players, message.attackerId, message.targetId) &&
                        (message.actionType == ActionType.DUEL || message.actionType == ActionType.RAID)
                    ) {
                        broadcastToAll(MsgActionRejected(message.attackerId, "盟友之间不能互相攻打"))
                        if (message.attackerId == myPlayerId) {
                            _uiState.update {
                                it.copy(
                                    hasSubmittedAction = false,
                                    allianceNotice = "盟友之间不能互相攻打"
                                )
                            }
                        }
                        return
                    }

                    var msgsToBroadcast: List<NetworkMessage>? = null

                    _uiState.update { state ->
                        val newActions = state.submittedActions.toMutableMap()
                        newActions[message.attackerId] = message

                        val alivePlayers = state.players.filter { it.status != PlayerStatus.ELIMINATED && !it.isBot }
                        val submittedAliveIds = newActions.keys.filter { id -> alivePlayers.any { it.id == id } }

                        if (submittedAliveIds.size >= alivePlayers.size && state.currentPhase == GamePhase.PHASE_1) {

                            val newEventQueue = GameRuleEngine.buildEventQueue(
                                state.players,
                                newActions,
                                state.dayNumber,
                                state.allianceActionPlans
                            )
                            val broadcastMsg = "【天机观测】气运已定，按灵脉鼎盛排序开庭！"
                            val firstEvent = newEventQueue.firstOrNull()
                            if (firstEvent == null) {
                                state.copy(submittedActions = emptyMap())
                            } else {
                                msgsToBroadcast = listOf(
                                    MsgRoomStateSync(state.players, broadcastMsg, newEventQueue),
                                    MsgEventSync(0, firstEvent, broadcastMsg)
                                )

                                state.copy(
                                    submittedActions = emptyMap(),
                                    eventQueue = newEventQueue,
                                    currentScreen = Screen.PHASE2,
                                    currentPhase = GamePhase.PHASE_2,
                                    currentEventIndex = 0,
                                    currentEvent = firstEvent,
                                    systemBroadcast = broadcastMsg
                                )
                            }
                        } else {
                            state.copy(submittedActions = newActions)
                        }
                    }

                    msgsToBroadcast?.let { msgs ->
                        broadcastToAll(*msgs.toTypedArray())
                    }
                }
            }

            is MsgChat -> {
                if ((message.isAllianceChat || message.isConspiracyChat) && message.recipientPlayerId == null) return

                if (_uiState.value.isHost) {
                    if (message.isAllianceChat) {
                        val recipientId = message.recipientPlayerId ?: return
                        if (!AllianceRuleEngine.areAllied(_uiState.value.players, message.senderId, recipientId)) {
                            if (message.senderId == myPlayerId) {
                                _uiState.update { it.copy(allianceNotice = "只能向当前盟友发送私聊") }
                            } else {
                                broadcastToAll(
                                    MsgAllianceResult(
                                        players = _uiState.value.players,
                                        recipientPlayerId = message.senderId,
                                        notice = "只能向当前盟友发送私聊"
                                    )
                                )
                            }
                            return
                        }
                    }
                    if (message.isConspiracyChat) {
                        val sessionId = message.conspiracySessionId ?: return
                        val recipientId = message.recipientPlayerId ?: return
                        val canSend = _uiState.value.conspiracySessions.any { session ->
                            session.sessionId == sessionId &&
                                session.includes(message.senderId) &&
                                session.includes(recipientId)
                        }
                        if (!canSend) return
                    }

                    appendChatMessageIfVisible(message)
                    sendChatToRecipients(message)
                } else {
                    appendChatMessageIfVisible(message)
                }
            }

            is MsgUseSilkBag -> {
                if (_uiState.value.isHost) {
                    handleSilkBagUse(message.request)
                }
            }

            is MsgConspiracyRequest -> {
                if (_uiState.value.isHost) {
                    val state = _uiState.value
                    val fromPlayer = state.players.find { it.id == message.fromPlayerId }
                    val toPlayer = state.players.find { it.id == message.toPlayerId }
                    val reason = when {
                        !isConspiracyOpen -> "完成第 3 回合后开放密谋"
                        fromPlayer == null || toPlayer == null -> "密谋对象不存在"
                        fromPlayer.status == PlayerStatus.ELIMINATED || toPlayer.status == PlayerStatus.ELIMINATED -> "阵亡玩家无法密谋"
                        message.fromPlayerId == message.toPlayerId -> "不能与自己密谋"
                        else -> ""
                    }
                    if (reason.isNotBlank()) {
                        broadcastToAll(MsgConspiracyResult(recipientPlayerId = message.fromPlayerId, notice = reason))
                        if (message.fromPlayerId == myPlayerId) {
                            _uiState.update { it.copy(conspiracyNotice = reason) }
                        }
                    } else {
                        val request = ConspiracyRequest(
                            requestId = message.requestId,
                            fromPlayerId = message.fromPlayerId,
                            fromPlayerName = message.fromPlayerName,
                            toPlayerId = message.toPlayerId,
                            expireTime = System.currentTimeMillis() + 10000
                        )
                        _uiState.update {
                            it.copy(
                                pendingConspiracyRequests = it.pendingConspiracyRequests + request,
                                incomingConspiracyRequest = if (request.toPlayerId == myPlayerId) request else it.incomingConspiracyRequest
                            )
                        }
                        broadcastToAll(
                            MsgConspiracyResult(
                                pendingRequest = request,
                                recipientPlayerId = request.toPlayerId,
                                notice = "${request.fromPlayerName} 向你发起密谋"
                            )
                        )
                    }
                }
            }

            is MsgConspiracyResponse -> {
                if (_uiState.value.isHost) {
                    val state = _uiState.value
                    val request = state.pendingConspiracyRequests.find { it.requestId == message.requestId }
                    if (request == null) {
                        broadcastToAll(MsgConspiracyResult(recipientPlayerId = message.toPlayerId, notice = "密谋邀请已失效"))
                        return
                    }

                    val remainingRequests = state.pendingConspiracyRequests.filterNot { it.requestId == message.requestId }
                    if (!message.accepted) {
                        _uiState.update {
                            it.copy(
                                pendingConspiracyRequests = remainingRequests,
                                incomingConspiracyRequest = if (it.incomingConspiracyRequest?.requestId == message.requestId) null else it.incomingConspiracyRequest,
                                conspiracyNotice = if (myPlayerId == message.toPlayerId) "已拒绝密谋邀请" else it.conspiracyNotice
                            )
                        }
                        broadcastToAll(
                            MsgConspiracyResult(recipientPlayerId = message.fromPlayerId, notice = "对方拒绝了密谋邀请"),
                            MsgConspiracyResult(recipientPlayerId = message.toPlayerId, notice = "已拒绝密谋邀请")
                        )
                    } else {
                        val fromPlayer = state.players.find { it.id == message.fromPlayerId } ?: return
                        val toPlayer = state.players.find { it.id == message.toPlayerId } ?: return
                        val session = ConspiracySession(
                            sessionId = "conspiracy_session_${state.dayNumber}_${message.fromPlayerId}_${message.toPlayerId}_${System.currentTimeMillis()}",
                            firstPlayerId = message.fromPlayerId,
                            firstPlayerName = fromPlayer.name,
                            secondPlayerId = message.toPlayerId,
                            secondPlayerName = toPlayer.name,
                            dayNumber = state.dayNumber
                        )
                        _uiState.update {
                            it.copy(
                                pendingConspiracyRequests = remainingRequests,
                                incomingConspiracyRequest = null,
                                conspiracySessions = it.conspiracySessions + session,
                                conspiracyNotice = if (session.includes(myPlayerId)) "密谋已建立" else it.conspiracyNotice
                            )
                        }
                        broadcastToAll(
                            MsgConspiracyResult(
                                session = session,
                                recipientPlayerId = message.fromPlayerId,
                                notice = "你已与 ${toPlayer.name} 开启密谋"
                            ),
                            MsgConspiracyResult(
                                session = session,
                                recipientPlayerId = message.toPlayerId,
                                notice = "你已与 ${fromPlayer.name} 开启密谋"
                            )
                        )
                    }
                }
            }

            is MsgConspiracyResult -> {
                if (message.recipientPlayerId == null || message.recipientPlayerId == myPlayerId) {
                    _uiState.update { state ->
                        val sessions = if (message.session != null && state.conspiracySessions.none { it.sessionId == message.session.sessionId }) {
                            state.conspiracySessions + message.session
                        } else {
                            state.conspiracySessions
                        }
                        state.copy(
                            incomingConspiracyRequest = message.pendingRequest ?: state.incomingConspiracyRequest,
                            conspiracySessions = sessions,
                            conspiracyNotice = message.notice.ifBlank { state.conspiracyNotice }
                        )
                    }
                }
            }

            is MsgAllianceRequest -> {
                if (_uiState.value.isHost) {
                    val state = _uiState.value
                    val check = if (!isAllianceOpen) {
                        AllianceCheckResult(false, "完成第 5 回合后开放同盟")
                    } else {
                        AllianceRuleEngine.canRequestAlliance(
                            players = state.players,
                            fromPlayerId = message.fromPlayerId,
                            toPlayerId = message.toPlayerId,
                            pendingRequests = state.pendingAllianceRequests
                        )
                    }

                    if (!check.allowed) {
                        broadcastToAll(
                            MsgAllianceResult(
                                players = state.players,
                                recipientPlayerId = message.fromPlayerId,
                                notice = check.reason
                            )
                        )
                        if (message.fromPlayerId == myPlayerId) {
                            _uiState.update { it.copy(allianceNotice = check.reason) }
                        }
                    } else {
                        val targetPlayer = state.players.find { it.id == message.toPlayerId }
                        if (targetPlayer?.isBot == true) {
                            handleNetworkMessage(
                                MsgAllianceResponse(
                                    requestId = message.requestId,
                                    fromPlayerId = message.fromPlayerId,
                                    toPlayerId = message.toPlayerId,
                                    accepted = true
                                )
                            )
                            return
                        }
                        val request = AllianceRequest(
                            requestId = message.requestId,
                            fromPlayerId = message.fromPlayerId,
                            fromPlayerName = message.fromPlayerName,
                            toPlayerId = message.toPlayerId,
                            expireTime = System.currentTimeMillis() + 10000
                        )
                        _uiState.update {
                            it.copy(
                                pendingAllianceRequests = it.pendingAllianceRequests + request,
                                incomingAllianceRequest = if (request.toPlayerId == myPlayerId) request else it.incomingAllianceRequest
                            )
                        }
                        broadcastToAll(
                            MsgAllianceResult(
                                players = state.players,
                                pendingRequest = request,
                                recipientPlayerId = request.toPlayerId,
                                notice = "${request.fromPlayerName} 向你发起结盟"
                            )
                        )
                    }
                }
            }

            is MsgAllianceResponse -> {
                if (_uiState.value.isHost) {
                    val state = _uiState.value
                    val request = state.pendingAllianceRequests.find { it.requestId == message.requestId }
                    if (request == null) {
                        broadcastToAll(
                            MsgAllianceResult(
                                players = state.players,
                                recipientPlayerId = message.toPlayerId,
                                notice = "结盟请求已失效"
                            )
                        )
                        return
                    }

                    val remainingRequests = state.pendingAllianceRequests.filterNot { it.requestId == message.requestId }

                    if (!message.accepted) {
                        val notice = "对方拒绝了结盟请求"
                        _uiState.update {
                            it.copy(
                                pendingAllianceRequests = remainingRequests,
                                incomingAllianceRequest = if (it.incomingAllianceRequest?.requestId == message.requestId) null else it.incomingAllianceRequest,
                                allianceNotice = when (myPlayerId) {
                                    message.fromPlayerId -> notice
                                    message.toPlayerId -> "已拒绝结盟请求"
                                    else -> it.allianceNotice
                                }
                            )
                        }
                        broadcastToAll(
                            MsgAllianceResult(
                                players = state.players,
                                recipientPlayerId = message.fromPlayerId,
                                notice = notice
                            ),
                            MsgAllianceResult(
                                players = state.players,
                                recipientPlayerId = message.toPlayerId,
                                notice = "已拒绝结盟请求"
                            )
                        )
                    } else {
                        val recheck = AllianceRuleEngine.canRequestAlliance(
                            players = state.players,
                            fromPlayerId = message.fromPlayerId,
                            toPlayerId = message.toPlayerId,
                            pendingRequests = remainingRequests
                        )
                        if (!recheck.allowed) {
                            _uiState.update { it.copy(pendingAllianceRequests = remainingRequests) }
                            broadcastToAll(
                                MsgAllianceResult(
                                    players = state.players,
                                    recipientPlayerId = message.fromPlayerId,
                                    notice = recheck.reason
                                ),
                                MsgAllianceResult(
                                    players = state.players,
                                    recipientPlayerId = message.toPlayerId,
                                    notice = "结盟已无法成立"
                                )
                            )
                        } else {
                            val updatedPlayers = AllianceRuleEngine.applyAlliance(
                                state.players,
                                message.fromPlayerId,
                                message.toPlayerId
                            )
                            val fromName = updatedPlayers.find { it.id == message.fromPlayerId }?.name ?: "对方"
                            val toName = updatedPlayers.find { it.id == message.toPlayerId }?.name ?: "对方"
                            _uiState.update {
                                it.copy(
                                    players = updatedPlayers,
                                    pendingAllianceRequests = remainingRequests,
                                    incomingAllianceRequest = null,
                                    allianceNotice = if (message.fromPlayerId == myPlayerId || message.toPlayerId == myPlayerId) {
                                        "结盟成功，本回合不可互相攻打"
                                    } else {
                                        it.allianceNotice
                                    }
                                )
                            }
                            broadcastToAll(
                                MsgAllianceResult(
                                    players = updatedPlayers,
                                    recipientPlayerId = message.fromPlayerId,
                                    alliancePartnerId = message.toPlayerId,
                                    notice = "你已与 $toName 结盟"
                                ),
                                MsgAllianceResult(
                                    players = updatedPlayers,
                                    recipientPlayerId = message.toPlayerId,
                                    alliancePartnerId = message.fromPlayerId,
                                    notice = "你已与 $fromName 结盟"
                                )
                            )
                        }
                    }
                }
            }

            is MsgAllianceResult -> {
                if (message.recipientPlayerId == null || message.recipientPlayerId == myPlayerId) {
                    _uiState.update { state ->
                        val localPlayers = if (message.alliancePartnerId != null) {
                            state.players.map { player ->
                                if (player.id == myPlayerId) {
                                    player.copy(
                                        isSelf = true,
                                        status = PlayerStatus.ALLIANCED,
                                        alliancePartnerId = message.alliancePartnerId
                                    )
                                } else {
                                    player.copy(isSelf = false)
                                }
                            }
                        } else {
                            state.players
                        }
                        state.copy(
                            players = localPlayers,
                            incomingAllianceRequest = message.pendingRequest ?: state.incomingAllianceRequest,
                            allianceNotice = message.notice.ifBlank { state.allianceNotice }
                        )
                    }
                }
            }

            is MsgAllianceActionPlanUpdate -> {
                if (_uiState.value.isHost) {
                    val state = _uiState.value
                    val plan = message.plan
                    val first = state.players.find { it.id == plan.firstPlayerId }
                    val second = state.players.find { it.id == plan.secondPlayerId }
                    val allowed = first?.alliancePartnerId == second?.id && second?.alliancePartnerId == first?.id
                    if (!allowed) {
                        broadcastToAll(MsgAllianceResult(state.players, recipientPlayerId = plan.firstPlayerId, notice = "同盟已失效"))
                        return
                    }
                    val updatedPlans = (state.allianceActionPlans.filterNot { it.allianceId == plan.allianceId } + plan)
                    _uiState.update {
                        it.copy(
                            allianceActionPlans = updatedPlans,
                            hasSubmittedAction = if (plan.isFullyConfirmed() && plan.includes(myPlayerId)) true else it.hasSubmittedAction,
                            allianceNotice = if (plan.includes(myPlayerId)) message.notice else it.allianceNotice
                        )
                    }
                    val notice = if (plan.isFullyConfirmed()) {
                        "同盟行动已协定，等待全员行动提交"
                    } else {
                        message.notice
                    }
                    if (plan.isFullyConfirmed()) {
                        handleNetworkMessage(
                            MsgSubmitAction(
                                attackerId = plan.firstPlayerId,
                                targetId = plan.targetId,
                                actionType = plan.actionType,
                                stake = plan.stake
                            )
                        )
                        handleNetworkMessage(
                            MsgSubmitAction(
                                attackerId = plan.secondPlayerId,
                                targetId = plan.targetId,
                                actionType = plan.actionType,
                                stake = plan.stake
                            )
                        )
                    }
                    viewModelScope.launch(Dispatchers.IO) {
                        gameServer?.sendToPlayers(setOf(plan.firstPlayerId, plan.secondPlayerId), MsgAllianceActionPlanUpdate(plan, notice))
                    }
                    broadcastToAll(
                        MsgAllianceResult(state.players, recipientPlayerId = plan.firstPlayerId, notice = notice),
                        MsgAllianceResult(state.players, recipientPlayerId = plan.secondPlayerId, notice = notice)
                    )
                } else {
                    _uiState.update { state ->
                        val updatedPlans = (state.allianceActionPlans.filterNot { it.allianceId == message.plan.allianceId } + message.plan)
                        state.copy(
                            allianceActionPlans = updatedPlans,
                            hasSubmittedAction = if (message.plan.isFullyConfirmed() && message.plan.includes(myPlayerId)) true else state.hasSubmittedAction,
                            allianceNotice = message.notice.ifBlank { state.allianceNotice }
                        )
                    }
                }
            }

            is MsgAllianceBetrayal -> {
                if (_uiState.value.isHost) {
                    val state = _uiState.value
                    val event = state.eventQueue.find { it.id == message.eventId } ?: return
                    if (!event.isAllianceAction || event.allianceActionPlan?.includes(message.playerId) != true) return
                    val updatedEvent = event.copy(betrayerId = message.playerId)
                    val newQueue = state.eventQueue.map { if (it.id == message.eventId) updatedEvent else it }
                    val broadcast = "【反水】${state.players.find { it.id == message.playerId }?.name ?: "盟友"} 选择反水，等待房主录入判定"
                    _uiState.update {
                        it.copy(
                            eventQueue = newQueue,
                            currentEvent = if (it.currentEvent?.id == message.eventId) updatedEvent else it.currentEvent,
                            systemBroadcast = broadcast
                        )
                    }
                    broadcastToAll(MsgEventSync(state.currentEventIndex, updatedEvent, broadcast))
                }
            }

            is MsgAllianceBetrayalResult -> {
                if (_uiState.value.isHost) {
                    val state = _uiState.value
                    val event = state.eventQueue.find { it.id == message.eventId } ?: return
                    val updatedEvent = event.copy(
                        betrayerId = message.betrayerId,
                        betrayalWinnerId = message.winnerId,
                        betrayalSucceeded = message.succeeded
                    )
                    val newQueue = state.eventQueue.map { if (it.id == message.eventId) updatedEvent else it }
                    _uiState.update {
                        it.copy(
                            eventQueue = newQueue,
                            currentEvent = if (it.currentEvent?.id == message.eventId) updatedEvent else it.currentEvent,
                            systemBroadcast = message.notice
                        )
                    }
                    broadcastToAll(MsgEventSync(state.currentEventIndex, updatedEvent, message.notice))
                }
            }

            is MsgActionRejected -> {
                if (message.playerId == myPlayerId) {
                    _uiState.update {
                        it.copy(
                            hasSubmittedAction = false,
                            allianceNotice = message.notice
                        )
                    }
                }
            }

            is MsgGhostInterference -> {
                if (_uiState.value.isHost) {
                    val state = _uiState.value
                    val currentEvent = state.currentEvent ?: return
                    val influenceChange = if (message.isBlessing) message.power else -message.power
                    val updatedEvent = currentEvent.copy(karmicInfluence = currentEvent.karmicInfluence + influenceChange)

                    _uiState.update { it.copy(currentEvent = updatedEvent) }
                    viewModelScope.launch { gameServer?.broadcast(MsgEventSync(state.currentEventIndex, updatedEvent)) }
                }
            }

            is MsgVote -> {
                if (_uiState.value.isHost) {
                    var msgsToBroadcast: List<NetworkMessage>? = null
                    var triggerEndOfEra = false

                    _uiState.update { state ->
                        val currentEvent = state.currentEvent ?: return@update state

                        if (message.isConfirm) {
                            if (currentEvent.isAllianceAction &&
                                currentEvent.betrayerId != null &&
                                currentEvent.betrayalSucceeded == null
                            ) {
                                return@update state.copy(systemBroadcast = "【反水】等待房主录入反水判定")
                            }
                            val newCount = currentEvent.confirmCount + 1
                            val requiredConfirmCount = state.players.count { it.status != PlayerStatus.ELIMINATED && !it.isBot }
                            if (newCount >= requiredConfirmCount) {

                                val fullyConfirmedEvent = currentEvent.copy(confirmCount = newCount)
                                val playersAfterOutcome = GameRuleEngine.resolveEventOutcome(fullyConfirmedEvent, state.players)
                                val recordedEvent = GameRuleEngine.updateEventOutcomeRecord(
                                    fullyConfirmedEvent,
                                    state.players,
                                    playersAfterOutcome
                                )
                                val updatedPlayers = playersAfterOutcome

                                val newQueue = state.eventQueue.toMutableList()
                                if (state.currentEventIndex in newQueue.indices) {
                                    newQueue[state.currentEventIndex] = recordedEvent
                                }

                                val alivePlayers = updatedPlayers.filter { it.status != PlayerStatus.ELIMINATED }
                                val isNowGameOver = alivePlayers.size <= 1

                                val deadNow = updatedPlayers.filter { p ->
                                    val old = state.players.find { it.id == p.id }
                                    old?.status != PlayerStatus.ELIMINATED && p.status == PlayerStatus.ELIMINATED
                                }
                                val resultMsg = if (deadNow.isNotEmpty()) "【天谴】${deadNow.joinToString { it.name }} 道统断绝，神魂坠入幽冥..." else "【天机】第 ${fullyConfirmedEvent.eventIndex} 号因果结算完毕。"

                                if (isNowGameOver) {
                                    val winner = alivePlayers.firstOrNull()?.name ?: "无人"
                                    val finalMsg = "【终局】诸天寂灭，唯有 $winner 存续道统！"
                                    msgsToBroadcast = listOf(
                                        MsgRoomStateSync(updatedPlayers, finalMsg, newQueue),
                                        MsgEventSync(state.currentEventIndex, recordedEvent, finalMsg)
                                    )
                                    state.copy(players = updatedPlayers, systemBroadcast = finalMsg, currentEvent = recordedEvent, eventQueue = newQueue, isGameOver = true, canProceedToNextDay = false)
                                } else {
                                    val nextIndex = state.currentEventIndex + 1
                                    if (nextIndex < newQueue.size) {
                                        val nextEvent = newQueue[nextIndex]
                                        msgsToBroadcast = listOf(
                                            MsgRoomStateSync(updatedPlayers, resultMsg, newQueue),
                                            MsgEventSync(nextIndex, nextEvent, resultMsg)
                                        )
                                        state.copy(players = updatedPlayers, systemBroadcast = resultMsg, currentEventIndex = nextIndex, currentEvent = nextEvent, eventQueue = newQueue)
                                    } else {

                                        triggerEndOfEra = true
                                        msgsToBroadcast = listOf(
                                            MsgRoomStateSync(updatedPlayers, resultMsg, newQueue),
                                            MsgEventSync(state.currentEventIndex, recordedEvent, resultMsg)
                                        )
                                        state.copy(players = updatedPlayers, systemBroadcast = resultMsg, currentEvent = recordedEvent, eventQueue = newQueue, canProceedToNextDay = true)
                                    }
                                }
                            } else {

                                val updatedEvent = currentEvent.copy(confirmCount = newCount)
                                val newQueue = state.eventQueue.toMutableList()
                                if (state.currentEventIndex in newQueue.indices) {
                                    newQueue[state.currentEventIndex] = updatedEvent
                                }
                                msgsToBroadcast = listOf(MsgEventSync(state.currentEventIndex, updatedEvent, state.systemBroadcast))
                                state.copy(currentEvent = updatedEvent, eventQueue = newQueue)
                            }
                        } else {

                            val resetEvent = currentEvent.copy(hostDecisionIndex = null, confirmCount = 0)
                            val alertMsg = "【警报】有玩家对裁定提出异议！"
                            val newQueue = state.eventQueue.toMutableList()
                            if (state.currentEventIndex in newQueue.indices) {
                                newQueue[state.currentEventIndex] = resetEvent
                            }
                            msgsToBroadcast = listOf(MsgEventSync(state.currentEventIndex, resetEvent, alertMsg))
                            state.copy(currentEvent = resetEvent, eventQueue = newQueue, systemBroadcast = alertMsg)
                        }
                    }

                    msgsToBroadcast?.let { msgs ->
                        broadcastToAll(*msgs.toTypedArray())
                    }
                    if (triggerEndOfEra) {
                        settlementJob?.cancel()
                        _uiState.update { it.copy(canProceedToNextDay = false) }
                        settlementJob = viewModelScope.launch {

                            kotlinx.coroutines.delay(3000)
                            val endMsg = "本纪元因果了结，准备进入下一天..."
                            _uiState.update { it.copy(
                                systemBroadcast = endMsg,
                                canProceedToNextDay = true
                            ) }
                            val s = _uiState.value
                            broadcastToAll(
                                MsgRoomStateSync(s.players, endMsg, s.eventQueue),
                                MsgEventSync(s.currentEventIndex, s.currentEvent!!, endMsg)
                            )
                        }
                    }
                }
            }

            is MsgJoinRoom -> {
                if (_uiState.value.isHost) handlePlayerJoin(message)
            }

            else -> {}
        }
    }

    /**
     * 将新连接客机加入房主状态，并重新同步所有客户端。
     */
    private fun handlePlayerJoin(msg: MsgJoinRoom) {
        val currentState = _uiState.value
        if (currentState.players.any { it.id == msg.playerId }) {
            syncCurrentStateToClients(currentState)
            return
        }

        val newPlayer = Player(id = msg.playerId, name = msg.playerName, isSelf = false)
        val updatedPlayers = currentState.players + newPlayer
        _uiState.update { it.copy(players = updatedPlayers) }
        syncCurrentStateToClients(_uiState.value)
    }

    /**
     * 将房主当前权威状态发送给所有已连接客机。
     */
    private fun syncCurrentStateToClients(state: GameUiState) {
        val messages = mutableListOf<NetworkMessage>()
        messages.add(MsgRoomStateSync(state.players, state.systemBroadcast, state.eventQueue))

        when (state.currentPhase) {
            GamePhase.PHASE_1 -> {
                if (state.currentScreen != Screen.WAITING_ROOM) {
                    messages.add(MsgGameStart(GamePhase.PHASE_1, state.systemBroadcast, state.dayNumber))
                }
            }
            GamePhase.PHASE_2 -> {
                val currentEvent = state.currentEvent
                if (currentEvent != null) {
                    messages.add(MsgEventSync(state.currentEventIndex, currentEvent, state.systemBroadcast))
                }
            }
        }

        broadcastToAll(*messages.toTypedArray())
    }

    /**
     * 仅在消息属于当前客户端可见会话时追加聊天记录。
     */
    private fun appendChatMessageIfVisible(message: MsgChat) {
        if (message.isConspiracyChat) {
            val recipientId = message.recipientPlayerId ?: return
            val sessionId = message.conspiracySessionId ?: return
            val visible = _uiState.value.conspiracySessions.any { session ->
                session.sessionId == sessionId &&
                    session.includes(myPlayerId) &&
                    (message.senderId == myPlayerId || recipientId == myPlayerId)
            }
            if (!visible) return
        }
        if (message.isAllianceChat) {
            val recipientId = message.recipientPlayerId ?: return
            if (message.senderId != myPlayerId && recipientId != myPlayerId) return
        }

        val chatMessage = ChatMessage(
            id = "chat_${message.senderId}_${System.currentTimeMillis()}",
            senderId = message.senderId,
            senderName = message.senderName,
            senderAvatarUrl = "",
            content = message.content,
            recipientPlayerId = message.recipientPlayerId,
            conspiracySessionId = message.conspiracySessionId,
            isAllianceChat = message.isAllianceChat,
            isConspiracyChat = message.isConspiracyChat
        )
        _uiState.update { state ->
            state.copy(chatMessages = (state.chatMessages + chatMessage).takeLast(80))
        }
    }

    private fun sendChatToRecipients(message: MsgChat) {
        viewModelScope.launch(Dispatchers.IO) {
            if (message.isAllianceChat || message.isConspiracyChat) {
                val recipientId = message.recipientPlayerId ?: return@launch
                gameServer?.sendToPlayers(setOf(message.senderId, recipientId), message)
            } else {
                gameServer?.broadcast(message)
            }
        }
    }

    private fun handleSilkBagUse(request: SilkBagUseRequest) {
        val state = _uiState.value
        val outcome = GameRuleEngine.useSilkBag(
            players = state.players,
            request = request,
            dayNumber = state.dayNumber,
            currentPhase = state.currentPhase,
            currentEvent = state.currentEvent,
            submittedActions = state.submittedActions
        )

        if (!outcome.success) {
            val rejection = MsgPrivateSilkBagResult(request.playerId, outcome.publicMessage)
            if (request.playerId == myPlayerId) {
                handleNetworkMessage(rejection)
            }
            viewModelScope.launch(Dispatchers.IO) {
                gameServer?.sendToPlayers(setOf(request.playerId), rejection)
            }
            return
        }

        val updatedEvent = outcome.event
        val updatedQueue = state.eventQueue.map { event ->
            if (updatedEvent != null && event.id == updatedEvent.id) updatedEvent else event
        }
        val syncedPlayers = GameRuleEngine.run { outcome.players.syncSilkBagCounts() }
        _uiState.update {
            it.copy(
                players = syncedPlayers,
                currentEvent = updatedEvent ?: it.currentEvent,
                eventQueue = updatedQueue,
                systemBroadcast = outcome.publicMessage,
                silkBagNotice = outcome.privateMessage.ifBlank { outcome.publicMessage }
            )
        }

        val result = MsgSilkBagResult(
            players = syncedPlayers,
            systemBroadcast = outcome.publicMessage,
            eventQueue = updatedQueue,
            currentEvent = updatedEvent,
            currentEventIndex = state.currentEventIndex,
            log = outcome.log
        )
        val privateResult = outcome.privateMessage.takeIf { it.isNotBlank() }?.let {
            MsgPrivateSilkBagResult(request.playerId, it)
        }
        viewModelScope.launch(Dispatchers.IO) {
            gameServer?.broadcast(result)
            privateResult?.let { gameServer?.sendToPlayers(setOf(request.playerId), it) }
        }
    }

    /**
     * 基于房主状态开启下一天的一阶段。
     */
    private fun startNextDay() {
        if (!_uiState.value.isHost) return
        val currentState = _uiState.value
        val nextDay = currentState.debugNextDayNumber ?: currentState.dayNumber + 1
        val welcomeMsg = "—— 第 $nextDay 天 · 气机复苏 ——"

        val updatedPlayers = GameRuleEngine.assignHeavenProtection(
            GameRuleEngine.resetDailySilkBagEffects(AllianceRuleEngine.clearAlliances(currentState.players)).map { player ->
                val debugVeins = currentState.debugNextDaySpiritVeins[player.id]
                if (debugVeins == null) player else player.copy(spiritVeins = debugVeins)
            }
        ).let { GameRuleEngine.run { it.syncSilkBagCounts() } }

        _uiState.update { state ->
            state.copy(
                dayNumber = nextDay,
                currentPhase = GamePhase.PHASE_1,
                currentScreen = Screen.PHASE1,
                hasSubmittedAction = false,
                submittedActions = emptyMap(),
                pendingAllianceRequests = emptyList(),
                incomingAllianceRequest = null,
                allianceNotice = "",
                pendingConspiracyRequests = emptyList(),
                incomingConspiracyRequest = null,
                conspiracySessions = emptyList(),
                conspiracyNotice = "",
                allianceActionPlans = emptyList(),
                chatMessages = state.chatMessages.filterNot { it.isAllianceChat || it.isConspiracyChat },
                eventQueue = emptyList(),
                currentEvent = null,
                systemBroadcast = welcomeMsg,
                debugNextDayNumber = null,
                debugNextDaySpiritVeins = emptyMap(),
                players = updatedPlayers
            )
        }

        viewModelScope.launch {
            gameServer?.broadcast(MsgGameStart(GamePhase.PHASE_1, welcomeMsg, nextDay))
            gameServer?.broadcast(MsgRoomStateSync(updatedPlayers, welcomeMsg))
        }
    }

    /**
     * 同步更新 currentEvent 以及 eventQueue 中对应的事件项。
     */
    private fun updateCurrentEventLocally(updatedEvent: GameEvent, newBroadcast: String? = null) {
        _uiState.update { state ->
            val newQueue = state.eventQueue.toMutableList()
            if (state.currentEventIndex in newQueue.indices) {
                newQueue[state.currentEventIndex] = updatedEvent
            }
            state.copy(
                currentEvent = updatedEvent,
                eventQueue = newQueue,
                systemBroadcast = newBroadcast ?: state.systemBroadcast
            )
        }
    }

    /**
     * 当 Android 生命周期导致监听循环停止时重启房主服务端。
     */
    private fun ensureHostServerRunning() {
        val server = gameServer ?: return
        if (server.isRunning()) return

        viewModelScope.launch(Dispatchers.IO) {
            server.start(viewModelScope)
        }
    }

    /**
     * 在没有重连任务运行时安排一次客机重连。
     */
    private fun scheduleClientReconnect(immediate: Boolean = false) {
        if (_uiState.value.isHost || _uiState.value.currentScreen == Screen.HOME) return
        if (joinedRoomIp.isNullOrEmpty()) return
        if (reconnectJob?.isActive == true) return

        reconnectJob = viewModelScope.launch(Dispatchers.IO) {
            if (!immediate) {
                delay(500)
            }
            connectToHostAndJoin()
        }
    }

    private suspend fun connectToHostAndJoin(): Boolean {
        val ip = joinedRoomIp ?: return false
        if (ip.isEmpty()) return false

        val connected = if (gameClient.isConnected()) {
            true
        } else {
            gameClient.connect(viewModelScope, ip, joinedRoomPort)
        }

        if (!connected) return false

        val joinMessage = MsgJoinRoom(myPlayerId, _uiState.value.myPlayerName)
        val joined = gameClient.sendMessage(joinMessage)
        if (!joined) {
            val reconnected = gameClient.connect(viewModelScope, ip, joinedRoomPort)
            return reconnected && gameClient.sendMessage(joinMessage)
        }

        return true
    }

    /**
     * 发送客机消息；失败时重连后再重试一次。
     */
    private fun sendToServer(msg: NetworkMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!gameClient.sendMessage(msg) && connectToHostAndJoin()) {
                delay(50)
                gameClient.sendMessage(msg)
            }
        }
    }

    /**
     * 按顺序广播多条房主消息，并在消息之间保留极短间隔。
     */
    private fun broadcastToAll(vararg msgs: NetworkMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            msgs.forEach { msg ->
                gameServer?.broadcast(msg)
                kotlinx.coroutines.delay(10)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        reconnectJob?.cancel()
        nsdHelper.stopEverything()
        gameClient.disconnect()
        gameServer?.stop()
    }
}
