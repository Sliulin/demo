package com.example.demo.engine

import com.example.demo.model.AllianceRequest
import com.example.demo.model.Player
import com.example.demo.model.PlayerStatus

object AllianceRuleEngine {
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

    fun clearAlliances(players: List<Player>): List<Player> {
        return players.map { player ->
            if (player.status == PlayerStatus.ALLIANCED) {
                player.copy(status = PlayerStatus.IDLE, alliancePartnerId = null)
            } else {
                player.copy(alliancePartnerId = null)
            }
        }
    }

    fun areAllied(players: List<Player>, firstPlayerId: String, secondPlayerId: String): Boolean {
        val first = players.find { it.id == firstPlayerId } ?: return false
        return first.alliancePartnerId == secondPlayerId
    }
}

data class AllianceCheckResult(
    val allowed: Boolean,
    val reason: String = ""
)
