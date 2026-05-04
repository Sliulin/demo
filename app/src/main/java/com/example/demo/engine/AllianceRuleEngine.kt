package com.example.demo.engine

import com.example.demo.model.AllianceRequest
import com.example.demo.model.Player
import com.example.demo.model.PlayerStatus

/**
 * 同盟规则的纯逻辑入口，负责校验结盟资格和转换同盟状态。
 */
object AllianceRuleEngine {
    /**
     * 校验两个玩家是否可以创建新的结盟请求。
     */
    fun canRequestAlliance(
        players: List<Player>,
        fromPlayerId: String,
        toPlayerId: String,
        pendingRequests: List<AllianceRequest>
    ): AllianceCheckResult {
        if (fromPlayerId == toPlayerId) {
            return AllianceCheckResult(false, "不能与自己结盟")
        }

        val fromPlayer = players.find { it.id == fromPlayerId }
            ?: return AllianceCheckResult(false, "发起者不存在")
        val toPlayer = players.find { it.id == toPlayerId }
            ?: return AllianceCheckResult(false, "目标玩家不存在")

        if (fromPlayer.status == PlayerStatus.ELIMINATED || toPlayer.status == PlayerStatus.ELIMINATED) {
            return AllianceCheckResult(false, "阵亡玩家无法结盟")
        }

        if (fromPlayer.alliancePartnerId != null) {
            return AllianceCheckResult(false, "你本回合已结盟")
        }

        if (toPlayer.alliancePartnerId != null) {
            return AllianceCheckResult(false, "对方本回合已无法结盟")
        }

        val isEitherPending = pendingRequests.any { request ->
            request.fromPlayerId == fromPlayerId ||
                request.toPlayerId == fromPlayerId ||
                request.fromPlayerId == toPlayerId ||
                request.toPlayerId == toPlayerId
        }

        if (isEitherPending) {
            return AllianceCheckResult(false, "已有结盟请求正在等待回应")
        }

        return AllianceCheckResult(true)
    }

    /**
     * 将两个玩家标记为互相结盟。
     */
    fun applyAlliance(players: List<Player>, firstPlayerId: String, secondPlayerId: String): List<Player> {
        return players.map { player ->
            when (player.id) {
                firstPlayerId -> player.copy(
                    status = PlayerStatus.ALLIANCED,
                    alliancePartnerId = secondPlayerId
                )
                secondPlayerId -> player.copy(
                    status = PlayerStatus.ALLIANCED,
                    alliancePartnerId = firstPlayerId
                )
                else -> player
            }
        }
    }

    /**
     * 在新的一阶段开始时清空所有临时同盟状态。
     */
    fun clearAlliances(players: List<Player>): List<Player> {
        return players.map { player ->
            if (player.status == PlayerStatus.ALLIANCED) {
                player.copy(status = PlayerStatus.IDLE, alliancePartnerId = null)
            } else {
                player.copy(alliancePartnerId = null)
            }
        }
    }

    /**
     * 根据当前玩家快照判断两名玩家是否互为盟友。
     */
    fun areAllied(players: List<Player>, firstPlayerId: String, secondPlayerId: String): Boolean {
        val first = players.find { it.id == firstPlayerId } ?: return false
        return first.alliancePartnerId == secondPlayerId
    }
}

/**
 * 结盟校验结果。
 */
data class AllianceCheckResult(
    val allowed: Boolean,
    val reason: String = ""
)
