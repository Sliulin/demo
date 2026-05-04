package com.example.demo.model

import kotlinx.serialization.Serializable

/**
 * 通过局域网服务发现得到的房间摘要。
 */
@Serializable
data class GameRoom(
    val id: String,
    val name: String,
    val host: Player,
    val currentPlayers: Int,
    val maxPlayers: Int,
    val ip: String = "",
    val port: Int = 9999,
    val isFull: Boolean = currentPlayers >= maxPlayers
)
