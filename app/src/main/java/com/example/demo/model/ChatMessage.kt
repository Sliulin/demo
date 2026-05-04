package com.example.demo.model

import kotlinx.serialization.Serializable

/**
 * 展示在公共消息、同盟私聊或密谋私聊中的聊天记录。
 */
@Serializable
data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderAvatarUrl: String,
    val content: String,
    val recipientPlayerId: String? = null,
    val conspiracySessionId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isSystemMessage: Boolean = false,
    val isAllianceChat: Boolean = false,
    val isConspiracyChat: Boolean = false
)

/**
 * 等待回应的结盟邀请。
 */
@Serializable
data class AllianceRequest(
    val requestId: String,
    val fromPlayerId: String,
    val fromPlayerName: String,
    val toPlayerId: String,
    val expireTime: Long
)

/**
 * 两名存活玩家之间等待回应的密谋邀请。
 */
@Serializable
data class ConspiracyRequest(
    val requestId: String,
    val fromPlayerId: String,
    val fromPlayerName: String,
    val toPlayerId: String,
    val expireTime: Long
)

/**
 * 两名玩家在当天开启的密谋私聊会话。
 */
@Serializable
data class ConspiracySession(
    val sessionId: String,
    val firstPlayerId: String,
    val firstPlayerName: String,
    val secondPlayerId: String,
    val secondPlayerName: String,
    val dayNumber: Int
) {
    /**
     * 判断玩家是否属于当前密谋会话。
     */
    fun includes(playerId: String): Boolean {
        return firstPlayerId == playerId || secondPlayerId == playerId
    }

    /**
     * 返回会话中的另一名参与者；非参与者返回 null。
     */
    fun partnerIdFor(playerId: String): String? {
        return when (playerId) {
            firstPlayerId -> secondPlayerId
            secondPlayerId -> firstPlayerId
            else -> null
        }
    }
}

/**
 * 同盟双方在一阶段锁定前协定的联合行动方案。
 */
@Serializable
data class AllianceActionPlan(
    val allianceId: String,
    val firstPlayerId: String,
    val secondPlayerId: String,
    val actionType: ActionType = ActionType.DUEL,
    val targetId: String,
    val targetName: String = "",
    val stake: Int = 0,
    val rewardShareFirst: Int = 50,
    val rewardShareSecond: Int = 50,
    val penaltyShareFirst: Int = 50,
    val penaltyShareSecond: Int = 50,
    val confirmedPlayerIds: List<String> = emptyList()
) {
    /**
     * 判断玩家是否属于当前同盟行动。
     */
    fun includes(playerId: String): Boolean {
        return firstPlayerId == playerId || secondPlayerId == playerId
    }

    /**
     * 判断同盟双方是否都已确认行动方案。
     */
    fun isFullyConfirmed(): Boolean {
        return confirmedPlayerIds.contains(firstPlayerId) && confirmedPlayerIds.contains(secondPlayerId)
    }

    /**
     * 返回同盟成功时双方的收益分配。
     */
    fun rewardShares(): Map<String, Int> {
        return mapOf(firstPlayerId to rewardShareFirst, secondPlayerId to rewardShareSecond)
    }

    /**
     * 返回同盟失败时双方的惩罚分配。
     */
    fun penaltyShares(): Map<String, Int> {
        return mapOf(firstPlayerId to penaltyShareFirst, secondPlayerId to penaltyShareSecond)
    }
}
