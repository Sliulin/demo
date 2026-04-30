package com.example.demo.network

import com.example.demo.model.ActionType
import com.example.demo.model.GameEvent
import com.example.demo.model.GamePhase
import com.example.demo.model.Player
import kotlinx.serialization.Serializable

// ==========================================
// 核心信封：所有网络消息的基类
// (删除了所有手动定义的 type 字段，避免与系统底层冲突)
// ==========================================
@Serializable
sealed class NetworkMessage

// ==========================================
// 具体的消息实现 (Data Payloads)
// ==========================================

// 1. 玩家请求加入
@Serializable
data class MsgJoinRoom(
    val playerId: String,
    val playerName: String
) : NetworkMessage()

// 2. 房主同步房间内的玩家列表 (大厅等待时)
@Serializable
data class MsgRoomStateSync(
    val players: List<Player>,
    val systemBroadcast: String = "",
    val eventQueue: List<GameEvent> = emptyList()
) : NetworkMessage()

// 3. 房主宣布游戏开始
@Serializable
data class MsgGameStart(
    val initialPhase: GamePhase = GamePhase.PHASE_1,
    val systemBroadcast: String = ""
) : NetworkMessage()

// 4. 一阶段：玩家提交私密行动
@Serializable
data class MsgSubmitAction(
    val attackerId: String,
    val targetId: String,
    val actionType: ActionType,
    val stake: Int = 0
) : NetworkMessage()

// 5. 房主切换游戏阶段
@Serializable
data class MsgPhaseTransition(
    val newPhase: GamePhase
) : NetworkMessage()

// 6. 二阶段：房主同步当前裁定事件
@Serializable
data class MsgEventSync(
    val currentEventIndex: Int,
    val event: GameEvent,
    val systemBroadcast: String = ""
) : NetworkMessage()

// 7. 二阶段：玩家发送投票结果
@Serializable
data class MsgVote(
    val playerId: String,
    val isConfirm: Boolean // true = 确认, false = 驳回重裁
) : NetworkMessage()

// 二阶段：幽魂降下干预（仅淘汰玩家可用）
@Serializable
data class MsgGhostInterference(
    val ghostId: String,
    val isBlessing: Boolean, // true = 赐下福泽(+收益), false = 降下业障(-收益)
    val power: Int = 5       // 干预的灵脉数量
) : NetworkMessage()

// 8. 聊天消息
@Serializable
data class MsgChat(
    val senderId: String,
    val senderName: String,
    val content: String,
    val isAllianceChat: Boolean
) : NetworkMessage()

// 消息：重启第一阶段（全员重新提交法旨）
@Serializable
object MsgRestartPhase1 : NetworkMessage()

// 消息：重启当前二阶段事件（清除当前事件的投票和判定）
@Serializable
data class MsgRestartCurrentEvent(
    val eventIndex: Int
) : NetworkMessage()
