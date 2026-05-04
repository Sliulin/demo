package com.example.demo.model

import kotlinx.serialization.Serializable

/**
 * 由一阶段行动生成、在二阶段公开结算的事件。
 */
@Serializable
data class GameEvent(
    val id: String,
    val attacker: Player,
    val defender: Player,
    val actionType: ActionType,
    val dayNumber: Int,
    val eventIndex: Int,
    val totalEvents: Int,
    var result: EventResult? = null,
    var isConfirmed: Boolean = false,
    var confirmCount: Int = 0,
    var totalPlayers: Int = 0,
    val hostDecision: Boolean? = null,
    val stake: Int = 0,
    var karmicInfluence: Int = 0,
    var hostDecisionIndex: Int? = null,
    var isSystemDetermined: Boolean = false,
    var systemMemo: String = "",
    val isAllianceAction: Boolean = false,
    val alliancePartner: Player? = null,
    val allianceActionPlan: AllianceActionPlan? = null,
    val betrayerId: String? = null,
    val betrayalWinnerId: String? = null,
    val betrayalSucceeded: Boolean? = null
)

/**
 * 为兼容旧序列化数据保留的结算结果载荷。
 */
@Serializable
data class EventResult(
    val winnerId: String,
    val veinsChangeAttacker: Int = 0,
    val veinsChangeDefender: Int = 0,
    val hasDoubleBonus: Boolean = false,
    val triggeredHeavenProtection: Boolean = false
)
