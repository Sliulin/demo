package com.example.demo.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderAvatarUrl: String,
    val content: String,
    val recipientPlayerId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isSystemMessage: Boolean = false,
    val isAllianceChat: Boolean = false
)

@Serializable
data class AllianceRequest(
    val requestId: String,
    val fromPlayerId: String,
    val fromPlayerName: String,
    val toPlayerId: String,
    val expireTime: Long
)
