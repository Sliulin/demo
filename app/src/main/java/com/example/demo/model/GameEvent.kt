package com.example.demo.model

import kotlinx.serialization.Serializable

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
    var hostDecisionIndex: Int? = null, // 【新增】0, 1, 2 分别对应不同的判定结果
    var isSystemDetermined: Boolean = false, // 【新增】标记是否由系统直接判定（如奇袭被防御）
    var systemMemo: String = ""
)

@Serializable
data class EventResult(
    val winnerId: String,
    val veinsChangeAttacker: Int = 0,
    val veinsChangeDefender: Int = 0,
    val hasDoubleBonus: Boolean = false,
    val triggeredHeavenProtection: Boolean = false
)
