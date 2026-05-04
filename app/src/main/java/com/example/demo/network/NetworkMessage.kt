package com.example.demo.network

import com.example.demo.model.ActionType
import com.example.demo.model.AllianceActionPlan
import com.example.demo.model.AllianceRequest
import com.example.demo.model.ConspiracyRequest
import com.example.demo.model.ConspiracySession
import com.example.demo.model.GameEvent
import com.example.demo.model.GamePhase
import com.example.demo.model.Player
import kotlinx.serialization.Serializable

/**
 * 本地 Socket 上传输的所有房主/客机消息基类。
 */
@Serializable
sealed class NetworkMessage

/**
 * 客机请求房主将玩家加入房间。
 */
@Serializable
data class MsgJoinRoom(
    val playerId: String,
    val playerName: String
) : NetworkMessage()

/**
 * 房主广播权威房间、玩家和事件队列快照。
 */
@Serializable
data class MsgRoomStateSync(
    val players: List<Player>,
    val systemBroadcast: String = "",
    val eventQueue: List<GameEvent> = emptyList()
) : NetworkMessage()

/**
 * 房主开始或重启指定天数的一阶段。
 */
@Serializable
data class MsgGameStart(
    val initialPhase: GamePhase = GamePhase.PHASE_1,
    val systemBroadcast: String = "",
    val dayNumber: Int = 1
) : NetworkMessage()

/**
 * 玩家向房主提交一阶段私密行动。
 */
@Serializable
data class MsgSubmitAction(
    val attackerId: String,
    val targetId: String,
    val actionType: ActionType,
    val stake: Int = 0
) : NetworkMessage()

/**
 * 房主宣布阶段切换。
 */
@Serializable
data class MsgPhaseTransition(
    val newPhase: GamePhase
) : NetworkMessage()

/**
 * 房主同步二阶段当前正在审理的事件。
 */
@Serializable
data class MsgEventSync(
    val currentEventIndex: Int,
    val event: GameEvent,
    val systemBroadcast: String = ""
) : NetworkMessage()

/**
 * 玩家确认或驳回房主对当前事件的判定。
 */
@Serializable
data class MsgVote(
    val playerId: String,
    val isConfirm: Boolean
) : NetworkMessage()

/**
 * 淘汰玩家修改当前事件的因果干预值。
 */
@Serializable
data class MsgGhostInterference(
    val ghostId: String,
    val isBlessing: Boolean,
    val power: Int = 5
) : NetworkMessage()

/**
 * 公共聊天、同盟私聊或密谋私聊的消息载荷。
 */
@Serializable
data class MsgChat(
    val senderId: String,
    val senderName: String,
    val content: String,
    val isAllianceChat: Boolean,
    val recipientPlayerId: String? = null,
    val isConspiracyChat: Boolean = false,
    val conspiracySessionId: String? = null
) : NetworkMessage()

/**
 * 玩家请求与另一名玩家开启密谋会话。
 */
@Serializable
data class MsgConspiracyRequest(
    val requestId: String,
    val fromPlayerId: String,
    val fromPlayerName: String,
    val toPlayerId: String
) : NetworkMessage()

/**
 * 目标玩家接受或拒绝密谋请求。
 */
@Serializable
data class MsgConspiracyResponse(
    val requestId: String,
    val fromPlayerId: String,
    val toPlayerId: String,
    val accepted: Boolean
) : NetworkMessage()

/**
 * 房主返回密谋请求对指定玩家可见的结果。
 */
@Serializable
data class MsgConspiracyResult(
    val pendingRequest: ConspiracyRequest? = null,
    val session: ConspiracySession? = null,
    val recipientPlayerId: String? = null,
    val notice: String = ""
) : NetworkMessage()

/**
 * 玩家请求与另一名玩家建立临时同盟。
 */
@Serializable
data class MsgAllianceRequest(
    val requestId: String,
    val fromPlayerId: String,
    val fromPlayerName: String,
    val toPlayerId: String
) : NetworkMessage()

/**
 * 目标玩家接受或拒绝结盟请求。
 */
@Serializable
data class MsgAllianceResponse(
    val requestId: String,
    val fromPlayerId: String,
    val toPlayerId: String,
    val accepted: Boolean
) : NetworkMessage()

/**
 * 房主返回同盟操作对指定玩家可见的结果。
 */
@Serializable
data class MsgAllianceResult(
    val players: List<Player>,
    val pendingRequest: AllianceRequest? = null,
    val recipientPlayerId: String? = null,
    val alliancePartnerId: String? = null,
    val notice: String = ""
) : NetworkMessage()

/**
 * 同盟双方提出或确认联合行动方案。
 */
@Serializable
data class MsgAllianceActionPlanUpdate(
    val plan: AllianceActionPlan,
    val notice: String = ""
) : NetworkMessage()

/**
 * 同盟成员在公开结算中声明反水。
 */
@Serializable
data class MsgAllianceBetrayal(
    val eventId: String,
    val playerId: String
) : NetworkMessage()

/**
 * 房主记录同盟反水的判定结果。
 */
@Serializable
data class MsgAllianceBetrayalResult(
    val eventId: String,
    val betrayerId: String,
    val winnerId: String,
    val succeeded: Boolean,
    val notice: String = ""
) : NetworkMessage()

/**
 * 房主拒绝行动提交，并解锁发送者的一阶段界面。
 */
@Serializable
data class MsgActionRejected(
    val playerId: String,
    val notice: String
) : NetworkMessage()

/**
 * 房主在不推进天数的情况下重启一阶段。
 */
@Serializable
object MsgRestartPhase1 : NetworkMessage()

/**
 * 房主重置当前二阶段事件以重新判定。
 */
@Serializable
data class MsgRestartCurrentEvent(
    val eventIndex: Int
) : NetworkMessage()
