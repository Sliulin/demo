package com.example.demo.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlayerStatus {
    IDLE, READY, ALLIANCED, WAITING, ELIMINATED
}

@Serializable
data class Player(
    val id: String,
    val name: String,
    val avatarUrl: String = "",
    val isHost: Boolean = false,
    val isSelf: Boolean = false, // 【恢复】：把这个加回来！
    var spiritVeins: Int = 100,
    var status: PlayerStatus = PlayerStatus.IDLE,
    var silkBag: Int = 0,
    var hasHeavenProtection: Boolean = false
)

@Serializable
enum class GamePhase { PHASE_1, PHASE_2 }