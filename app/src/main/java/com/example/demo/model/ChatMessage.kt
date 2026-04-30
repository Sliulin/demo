package com.example.demo.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderAvatarUrl: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSystemMessage: Boolean = false,
    val isAllianceChat: Boolean = false
)

@Serializable
data class AllianceRequest(
    val fromPlayer: Player,
    val toPlayer: Player,
    val expireTime: Long
)
