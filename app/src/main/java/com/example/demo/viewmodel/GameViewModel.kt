package com.example.demo.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
 * 游戏 UI 状态载体
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
    val submittedActions: Map<String, MsgSubmitAction> = emptyMap(),
    val isGameOver: Boolean = false,
    val canProceedToNextDay: Boolean = false,
    val hasSubmittedAction: Boolean = false,
    val pendingAllianceRequests: List<AllianceRequest> = emptyList(),
    val incomingAllianceRequest: AllianceRequest? = null,
    val allianceNotice: String = ""
)

enum class Screen { HOME, PHASE1, PHASE2, WAITING_ROOM }

/**
 * 游戏业务视图模型
 * 职责：负责网络通信调度、界面路由切换及 UI 状态同步
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val sharedPreferences = application.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
    private val KEY_PLAYER_NAME = "saved_player_name"

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

    init {
        // 加载持久化的昵称
        val savedName = sharedPreferences.getString(KEY_PLAYER_NAME, "神秘玩家") ?: "神秘玩家"
        _uiState.update { it.copy(myPlayerName = savedName) }

        // 监听局域网房间扫描
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

    // ================= 基础配置与导航 =================

    fun updatePlayerName(newName: String) {
        _uiState.update { it.copy(myPlayerName = newName) }
        sharedPreferences.edit().putString(KEY_PLAYER_NAME, newName).apply()
    }

    fun refreshRooms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            nsdHelper.stopEverything()
            nsdHelper.startScanning()
            delay(1500)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    // ================= 核心联机逻辑 (房主 & 客机) =================

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

    fun createRoom(roomName: String) {
        myPlayerId = "host_${System.currentTimeMillis()}"
        val globalName = _uiState.value.myPlayerName
        val host = Player(id = myPlayerId, name = globalName, isHost = true, isSelf = true)

        joinedRoomIp = null
        reconnectJob?.cancel()
        _uiState.update { it.copy(isHost = true, players = listOf(host)) }

        gameServer = GameServer(onMessageReceived = { msg -> handleNetworkMessage(msg) })
        ensureHostServerRunning()

        nsdHelper.stopEverything()
        nsdHelper.startBroadcasting(roomName, globalName, 9999)
        _uiState.update { it.copy(currentScreen = Screen.WAITING_ROOM) }
    }

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

    fun startGame() {
        // 【新增】：第一天开局时，立刻计算初始的天道庇佑
        val updatedPlayers = GameRuleEngine.assignHeavenProtection(_uiState.value.players)
        _uiState.update { it.copy(players = updatedPlayers) }

        val msg = "—— 第 1 天 · 气机复苏 ——"
        val startMsg = MsgGameStart(GamePhase.PHASE_1, msg)

        handleNetworkMessage(startMsg)
        viewModelScope.launch {
            gameServer?.broadcast(MsgRoomStateSync(updatedPlayers, msg))
            gameServer?.broadcast(startMsg)
        }
    }

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

    // ================= 第一阶段：暗流涌动 (指令提交) =================

    fun submitAction(targetPlayer: Player, actionType: ActionType, stake: Int = 0) {
        if (AllianceRuleEngine.areAllied(_uiState.value.players, myPlayerId, targetPlayer.id) &&
            (actionType == ActionType.DUEL || actionType == ActionType.RAID)
        ) {
            _uiState.update { it.copy(allianceNotice = "盟友之间不能互相攻打") }
            return
        }

        val msg = MsgSubmitAction(
            attackerId = myPlayerId,
            targetId = targetPlayer.id,
            actionType = actionType,
            stake = stake
        )

        // 【修复 1】：点击瞬间，立刻让 UI 呈现 "法旨已结印"，不让玩家等
        _uiState.update { it.copy(hasSubmittedAction = true) }

        // 发送给服务器去慢慢处理
        if (_uiState.value.isHost) {
            handleNetworkMessage(msg)
        } else {
            sendToServer(msg) // 使用无阻塞发包
        }
    }

    fun requestAlliance(targetPlayer: Player) {
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

    // ================= 第二阶段：因果结算 (判定与投票) =================

    fun submitHostDecisionIndex(index: Int) {
        val state = _uiState.value
        val currentEvent = state.currentEvent ?: return
        val updatedEvent = currentEvent.copy(hostDecisionIndex = index, confirmCount = 1)

        // 【核心修复 2】：使用新助手同时更新单独事件和队列列表
        updateCurrentEventLocally(updatedEvent)

        viewModelScope.launch {
            gameServer?.broadcast(
                MsgEventSync(state.currentEventIndex, updatedEvent, state.systemBroadcast)
            )
        }
    }

    fun castVote(isConfirm: Boolean) {
        sendToServer(MsgVote(playerId = myPlayerId, isConfirm = isConfirm))
    }

    fun submitGhostInterference(isBlessing: Boolean) {
        viewModelScope.launch {
            val msg = MsgGhostInterference(ghostId = myPlayerId, isBlessing = isBlessing, power = 5)
            if (_uiState.value.isHost) handleNetworkMessage(msg) else sendToServer(msg)
            _uiState.update { it.copy(systemBroadcast = "【神念】你已降下${if (isBlessing) "福泽" else "业障"}...") }
        }
    }

    // ================= 手动跳转方法 =================
    fun proceedToNextDay() {
        val state = _uiState.value
        // 增加 !state.isGameOver 判断，确保死局无法强行开启第二天
        if (state.isHost && state.canProceedToNextDay && !state.isGameOver) {
            settlementJob?.cancel()
            startNextDay()
        }
    }

    // ================= 异常补救：时光回溯 =================

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
            chatMessages = it.chatMessages.filterNot { message -> message.isAllianceChat },
            systemBroadcast = msg
        ) }

        // ✨ 弃用新增协议，直接复用已有的“游戏开始”协议，100%保证客机能收到！
        broadcastToAll(
            MsgRoomStateSync(cleanPlayers, msg, emptyList()),
            MsgGameStart(GamePhase.PHASE_1, msg)
        )
    }

    fun restartCurrentEvent() {
        if (!_uiState.value.isHost) return
        val state = _uiState.value
        val currentEvent = state.currentEvent ?: return

        // 1. 安全重置：
        // 【关键逻辑】如果是系统强制判定的事件（如奇袭撞阵），绝对不能清空 DecisionIndex，否则会失去执行选项！
        val safeDecisionIndex = if (currentEvent.isSystemDetermined) currentEvent.hostDecisionIndex else null

        val resetEvent = currentEvent.copy(
            hostDecisionIndex = safeDecisionIndex,
            confirmCount = 0 // 核心：清空确认票数
        )

        val alertMsg = "【天机重塑】房主推翻了当前判定，重新审理！"

        // 2. 更新房主本地队列
        updateCurrentEventLocally(resetEvent, alertMsg)

        // 3. ✨ 弃用新增协议，直接复用最成熟的 MsgEventSync 下发重置后的事件，绝不丢包！
        broadcastToAll(MsgEventSync(state.currentEventIndex, resetEvent, alertMsg))
    }

    // ================= 网络消息中枢 =================

    private fun handleNetworkMessage(message: NetworkMessage) {
        when (message) {
            is MsgRoomStateSync -> {
                val localPlayers = message.players.map { it.copy(isSelf = (it.id == myPlayerId)) }
                _uiState.update { it.copy(
                    players = localPlayers,
                    systemBroadcast = message.systemBroadcast,
                    // 【核心修复 3】：客机接收房主的完整卷轴
                    eventQueue = if (message.eventQueue.isNotEmpty()) message.eventQueue else it.eventQueue
                ) }
            }

            is MsgEventSync -> {
                // 【核心修复 4】：客机收到单个事件进度时，也要更新自己的本地卷轴
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
                        eventQueue = newQueue, // 同步更新列表
                        systemBroadcast = message.systemBroadcast
                    )
                }
            }

            is MsgGameStart -> {
                _uiState.update { state ->
                    val cleanPlayers = AllianceRuleEngine.clearAlliances(state.players)
                    state.copy(
                        currentPhase = message.initialPhase,
                        currentScreen = Screen.PHASE1,
                        players = cleanPlayers,
                        selectedTargetPlayer = cleanPlayers.firstOrNull { it.id != myPlayerId },
                        hasSubmittedAction = false,
                        submittedActions = emptyMap(),
                        pendingAllianceRequests = emptyList(),
                        incomingAllianceRequest = null,
                        allianceNotice = "",
                        chatMessages = state.chatMessages.filterNot { it.isAllianceChat },
                        systemBroadcast = message.systemBroadcast
                    )
                }
            }

            is MsgSubmitAction -> {
                if (_uiState.value.isHost) {
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

                    // 1. 将提交记录和人数比对完全放入原子级的 update 中，防止并发漏判
                    _uiState.update { state ->
                        val newActions = state.submittedActions.toMutableMap()
                        newActions[message.attackerId] = message

                        val alivePlayers = state.players.filter { it.status != PlayerStatus.ELIMINATED }
                        val submittedAliveIds = newActions.keys.filter { id -> alivePlayers.any { it.id == id } }

                        if (submittedAliveIds.size >= alivePlayers.size && state.currentPhase == GamePhase.PHASE_1) {
                            // 全员提交完毕，立即生成队列
                            val newEventQueue = GameRuleEngine.buildEventQueue(state.players, newActions, state.dayNumber)
                            val broadcastMsg = "【天机观测】气运已定，按灵脉鼎盛排序开庭！"

                            msgsToBroadcast = listOf(
                                MsgRoomStateSync(state.players, broadcastMsg, newEventQueue),
                                MsgEventSync(0, newEventQueue.first(), broadcastMsg)
                            )

                            state.copy(
                                submittedActions = emptyMap(),
                                eventQueue = newEventQueue,
                                currentScreen = Screen.PHASE2,
                                currentPhase = GamePhase.PHASE_2,
                                currentEventIndex = 0,
                                currentEvent = newEventQueue.firstOrNull(),
                                systemBroadcast = broadcastMsg
                            )
                        } else {
                            state.copy(submittedActions = newActions)
                        }
                    }

                    // 2. 在安全域外执行广播，实现立即跳转
                    msgsToBroadcast?.let { msgs ->
                        broadcastToAll(*msgs.toTypedArray())
                    }
                }
            }

            is MsgChat -> {
                if (message.isAllianceChat && message.recipientPlayerId == null) return

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

                    appendChatMessageIfVisible(message)
                    sendChatToRecipients(message)
                } else {
                    appendChatMessageIfVisible(message)
                }
            }

            is MsgAllianceRequest -> {
                if (_uiState.value.isHost) {
                    val state = _uiState.value
                    val check = AllianceRuleEngine.canRequestAlliance(
                        players = state.players,
                        fromPlayerId = message.fromPlayerId,
                        toPlayerId = message.toPlayerId,
                        pendingRequests = state.pendingAllianceRequests
                    )

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

            // ================= 1. 修改 handleNetworkMessage 里的 is MsgVote 分支 =================
            is MsgVote -> {
                if (_uiState.value.isHost) {
                    var msgsToBroadcast: List<NetworkMessage>? = null
                    var triggerEndOfEra = false

                    _uiState.update { state ->
                        val currentEvent = state.currentEvent ?: return@update state

                        if (message.isConfirm) {
                            val newCount = currentEvent.confirmCount + 1
                            if (newCount >= state.players.size) {
                                // 票数已满：执行强结算
                                val fullyConfirmedEvent = currentEvent.copy(confirmCount = newCount)
                                val updatedPlayers = GameRuleEngine.resolveEventOutcome(fullyConfirmedEvent, state.players)

                                val newQueue = state.eventQueue.toMutableList()
                                if (state.currentEventIndex in newQueue.indices) {
                                    newQueue[state.currentEventIndex] = fullyConfirmedEvent
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
                                        MsgEventSync(state.currentEventIndex, fullyConfirmedEvent, finalMsg)
                                    )
                                    state.copy(players = updatedPlayers, systemBroadcast = finalMsg, currentEvent = fullyConfirmedEvent, eventQueue = newQueue, isGameOver = true, canProceedToNextDay = false)
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
                                        // 触发冷却
                                        triggerEndOfEra = true
                                        msgsToBroadcast = listOf(
                                            MsgRoomStateSync(updatedPlayers, resultMsg, newQueue),
                                            MsgEventSync(state.currentEventIndex, fullyConfirmedEvent, resultMsg)
                                        )
                                        state.copy(players = updatedPlayers, systemBroadcast = resultMsg, currentEvent = fullyConfirmedEvent, eventQueue = newQueue, canProceedToNextDay = true)
                                    }
                                }
                            } else {
                                // 票数未满：安全累加票数
                                val updatedEvent = currentEvent.copy(confirmCount = newCount)
                                val newQueue = state.eventQueue.toMutableList()
                                if (state.currentEventIndex in newQueue.indices) {
                                    newQueue[state.currentEventIndex] = updatedEvent
                                }
                                msgsToBroadcast = listOf(MsgEventSync(state.currentEventIndex, updatedEvent, state.systemBroadcast))
                                state.copy(currentEvent = updatedEvent, eventQueue = newQueue)
                            }
                        } else {
                            // 提出异议：打回重裁
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

                    // 触发相关的副作用与广播
                    msgsToBroadcast?.let { msgs ->
                        broadcastToAll(*msgs.toTypedArray())
                    }
                    if (triggerEndOfEra) {
                        settlementJob?.cancel()
                        _uiState.update { it.copy(canProceedToNextDay = false) }
                        settlementJob = viewModelScope.launch {
                            // 同步消息...
                            kotlinx.coroutines.delay(3000) // ⏳ 关键的 3 秒等待
                            val endMsg = "本纪元因果了结，准备进入下一天..."
                            _uiState.update { it.copy(
                                systemBroadcast = endMsg,
                                canProceedToNextDay = true // 3秒后解锁按钮
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

    private fun syncCurrentStateToClients(state: GameUiState) {
        val messages = mutableListOf<NetworkMessage>()
        messages.add(MsgRoomStateSync(state.players, state.systemBroadcast, state.eventQueue))

        when (state.currentPhase) {
            GamePhase.PHASE_1 -> {
                if (state.currentScreen != Screen.WAITING_ROOM) {
                    messages.add(MsgGameStart(GamePhase.PHASE_1, state.systemBroadcast))
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

    private fun appendChatMessageIfVisible(message: MsgChat) {
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
            isAllianceChat = message.isAllianceChat
        )
        _uiState.update { state ->
            state.copy(chatMessages = (state.chatMessages + chatMessage).takeLast(80))
        }
    }

    private fun sendChatToRecipients(message: MsgChat) {
        viewModelScope.launch(Dispatchers.IO) {
            if (message.isAllianceChat) {
                val recipientId = message.recipientPlayerId ?: return@launch
                gameServer?.sendToPlayers(setOf(message.senderId, recipientId), message)
            } else {
                gameServer?.broadcast(message)
            }
        }
    }

    /**
     * 【房主专用】开启下一个轮回
     */
    private fun startNextDay() {
        if (!_uiState.value.isHost) return
        val nextDay = _uiState.value.dayNumber + 1
        val welcomeMsg = "—— 第 $nextDay 天 · 气机复苏 ——"

        // 【核心修复】：在天亮时，立刻核算新一天的天道庇佑！
        val updatedPlayers = GameRuleEngine.assignHeavenProtection(
            AllianceRuleEngine.clearAlliances(_uiState.value.players)
        )

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
                chatMessages = state.chatMessages.filterNot { it.isAllianceChat },
                eventQueue = emptyList(),
                currentEvent = null,
                systemBroadcast = welcomeMsg,
                players = updatedPlayers // 【更新】：将带有最新星星状态的玩家列表存入
            )
        }

        viewModelScope.launch {
            gameServer?.broadcast(MsgGameStart(GamePhase.PHASE_1, welcomeMsg))
            gameServer?.broadcast(MsgRoomStateSync(updatedPlayers, welcomeMsg))
        }
    }
    /**
     * 【新增】：统一处理事件与队列的同步更新
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
     * 客机发送专用：切入 IO 线程，绝不卡顿点击动画
     */
    private fun ensureHostServerRunning() {
        val server = gameServer ?: return
        if (server.isRunning()) return

        viewModelScope.launch(Dispatchers.IO) {
            server.start(viewModelScope)
        }
    }

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

    private fun sendToServer(msg: NetworkMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!gameClient.sendMessage(msg) && connectToHostAndJoin()) {
                delay(50)
                gameClient.sendMessage(msg)
            }
        }
    }

    /**
     * 房主广播专用：切入 IO 线程，并在连续发包时加入 30ms 错位，彻底解决 TCP 粘包与延迟
     */
    private fun broadcastToAll(vararg msgs: NetworkMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            msgs.forEach { msg ->
                gameServer?.broadcast(msg)
                kotlinx.coroutines.delay(10) // 微小延迟，打断网络阻塞
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
