package com.example.demo.model

import kotlinx.serialization.Serializable

/**
 * 房间、同盟和结算逻辑共享的玩家状态。
 */
@Serializable
enum class PlayerStatus {
    IDLE, READY, ALLIANCED, WAITING, ELIMINATED
}

/**
 * 房主和客机之间同步的可序列化玩家快照。
 */
@Serializable
data class Player(
    val id: String,
    val name: String,
    val avatarUrl: String = "",
    val isHost: Boolean = false,
    val isSelf: Boolean = false,
    var spiritVeins: Int = 100,
    val isBot: Boolean = false,
    var status: PlayerStatus = PlayerStatus.IDLE,
    var silkBag: Int = 0,
    var hasHeavenProtection: Boolean = false,
    val alliancePartnerId: String? = null
)

/**
 * 游戏主流程阶段。
 */
@Serializable
enum class GamePhase { PHASE_1, PHASE_2 }
