package com.example.demo.engine

import com.example.demo.model.*
import com.example.demo.network.MsgSubmitAction

/**
 * 游戏规则纯逻辑引擎，负责阶段转换、事件生成和结算计算。
 */
object GameRuleEngine {

    /**
     * 为唯一灵脉最低的存活玩家刷新天道庇佑。
     */
    fun assignHeavenProtection(players: List<Player>): List<Player> {
        val alivePlayers = players.filter { it.status != PlayerStatus.ELIMINATED }

        val minVeins = alivePlayers.minOfOrNull { it.spiritVeins } ?: return players

        val bottomPlayersCount = alivePlayers.count { it.spiritVeins == minVeins }

        val isUniqueBottom = bottomPlayersCount == 1

        return players.map { player ->
            if (player.status != PlayerStatus.ELIMINATED) {
                player.copy(hasHeavenProtection = (isUniqueBottom && player.spiritVeins == minVeins))
            } else {
                player.copy(hasHeavenProtection = false)
            }
        }
    }

    /**
     * 根据当天提交的行动生成按灵脉排序的公开事件队列。
     */
    fun buildEventQueue(
        players: List<Player>,
        submittedActions: Map<String, MsgSubmitAction>,
        dayNumber: Int,
        alliancePlans: List<AllianceActionPlan> = emptyList()
    ): List<GameEvent> {
        val sortedAlivePlayers = players
            .filter { it.status != PlayerStatus.ELIMINATED }
            .sortedByDescending { it.spiritVeins }
        val confirmedAlliancePlans = alliancePlans.filter { it.isFullyConfirmed() }
        val coveredAlliancePlayers = confirmedAlliancePlans
            .flatMap { listOf(it.firstPlayerId, it.secondPlayerId) }
            .toSet()

        val validEvents = sortedAlivePlayers.mapNotNull { attacker ->
            val alliancePlan = confirmedAlliancePlans.find { it.firstPlayerId == attacker.id }
            if (alliancePlan != null) {
                val partner = players.find { it.id == alliancePlan.secondPlayerId } ?: return@mapNotNull null
                val defender = players.find { it.id == alliancePlan.targetId } ?: return@mapNotNull null
                return@mapNotNull GameEvent(
                    id = "event_${System.currentTimeMillis()}_${attacker.id}_${partner.id}",
                    attacker = attacker,
                    defender = defender,
                    actionType = alliancePlan.actionType,
                    dayNumber = dayNumber,
                    eventIndex = 0,
                    totalEvents = 0,
                    stake = alliancePlan.stake,
                    karmicInfluence = 0,
                    totalPlayers = players.size,
                    confirmCount = 0,
                    isAllianceAction = true,
                    alliancePartner = partner,
                    allianceActionPlan = alliancePlan,
                    systemMemo = "【同盟】${attacker.name} 与 ${partner.name} 发动同盟行动，总收益/惩罚 x3。"
                )
            }
            if (coveredAlliancePlayers.contains(attacker.id)) return@mapNotNull null

            val myAction = submittedActions[attacker.id] ?: return@mapNotNull null
            val targetAction = submittedActions[myAction.targetId]
            val defender = players.find { it.id == myAction.targetId } ?: return@mapNotNull null

            var isSystemDetermined = false
            var systemIndex: Int? = null
            var memo = ""

            when (myAction.actionType) {
                ActionType.RAID -> {
                    if (targetAction?.actionType == ActionType.DEFEND_ARRAY) {
                        isSystemDetermined = true
                        systemIndex = 2
                        memo = "【系统】目标已开启护宗大阵，奇袭受挫！"
                    }
                }
                ActionType.DEFEND_ARRAY -> {
                    val isRaided = submittedActions.values.any { it.targetId == attacker.id && it.actionType == ActionType.RAID }

                    if (isRaided) return@mapNotNull null

                    isSystemDetermined = true
                    systemIndex = 0
                    memo = "【系统】四海太平，大阵空转。"
                }
                else -> {}
            }

            GameEvent(
                id = "event_${System.currentTimeMillis()}_${attacker.id}",
                attacker = attacker,
                defender = defender,
                actionType = myAction.actionType,
                dayNumber = dayNumber,
                eventIndex = 0,
                totalEvents = 0,
                stake = myAction.stake,
                karmicInfluence = 0,
                totalPlayers = players.size,
                hostDecisionIndex = systemIndex,
                isSystemDetermined = isSystemDetermined,
                systemMemo = memo,
                confirmCount = 0
            )
        }

        return validEvents.mapIndexed { index, event ->
            event.copy(
                eventIndex = index + 1,
                totalEvents = validEvents.size
            )
        }
    }

    /**
     * 将房主确认后的事件结果结算到当前玩家快照。
     */
    fun resolveEventOutcome(event: GameEvent, currentPlayers: List<Player>): List<Player> {
        val decision = event.hostDecisionIndex ?: 0
        val baseStake = event.stake
        val ghostPower = event.karmicInfluence
        val veinChanges = mutableMapOf<String, Int>()
        val silkBagChanges = mutableMapOf<String, Int>()

        fun applyTransfer(winnerId: String, loserId: String, base: Int) {
            val winners = allianceSide(currentPlayers, winnerId)
            val losers = allianceSide(currentPlayers, loserId)
            if (winners.isEmpty() || losers.isEmpty() || base <= 0) return

            val totalBase = base * maxOf(winners.size, losers.size)
            val basePerWinner = totalBase / winners.size
            val remainder = totalBase % winners.size

            winners.forEachIndexed { index, winner ->
                val gainBase = basePerWinner + if (index < remainder) 1 else 0
                val gain = calculateGain(winner, gainBase, ghostPower)
                veinChanges[winner.id] = (veinChanges[winner.id] ?: 0) + gain
            }

            losers.forEach { loser ->
                veinChanges[loser.id] = (veinChanges[loser.id] ?: 0) - base
            }
        }

        fun applyExploreReward(playerId: String, base: Int) {
            val winners = allianceSide(currentPlayers, playerId)
            if (winners.isEmpty() || base <= 0) return

            winners.forEach { winner ->
                val gain = calculateGain(winner, base, ghostPower)
                veinChanges[winner.id] = (veinChanges[winner.id] ?: 0) + gain
            }
        }

        fun applySilkBagReward(playerId: String) {
            allianceSide(currentPlayers, playerId).forEach { winner ->
                silkBagChanges[winner.id] = (silkBagChanges[winner.id] ?: 0) + 1
            }
        }

        fun splitAmount(total: Int, shares: Map<String, Int>): Map<String, Int> {
            val cleaned = shares.mapValues { it.value.coerceAtLeast(0) }
            val shareTotal = cleaned.values.sum().takeIf { it > 0 } ?: return cleaned.mapValues { 0 }
            var assigned = 0
            val entries = cleaned.entries.toList()
            return entries.mapIndexed { index, entry ->
                val value = if (index == entries.lastIndex) {
                    total - assigned
                } else {
                    (total * entry.value) / shareTotal
                }
                assigned += value
                entry.key to value
            }.toMap()
        }

        fun applyAllianceReward(plan: AllianceActionPlan, base: Int) {
            if (base <= 0) return
            val total = base * 3
            val shares = event.betrayalWinnerId?.takeIf { event.betrayalSucceeded == true }?.let { winnerId ->
                plan.rewardShares().keys.associateWith { playerId -> if (playerId == winnerId) 100 else 0 }
            } ?: plan.rewardShares()
            splitAmount(total, shares).forEach { (playerId, amount) ->
                val player = currentPlayers.find { it.id == playerId } ?: return@forEach
                val gain = calculateGain(player, amount, ghostPower)
                veinChanges[playerId] = (veinChanges[playerId] ?: 0) + gain
            }
        }

        fun applyAlliancePenalty(plan: AllianceActionPlan, base: Int) {
            if (base <= 0) return
            val total = base * 3
            val shares = event.betrayalWinnerId?.takeIf { event.betrayalSucceeded == true }?.let { winnerId ->
                plan.penaltyShares().keys.associateWith { playerId -> if (playerId == winnerId) 0 else 100 }
            } ?: plan.penaltyShares()
            splitAmount(total, shares).forEach { (playerId, amount) ->
                veinChanges[playerId] = (veinChanges[playerId] ?: 0) - amount
            }
        }

        fun applyAllianceSilkBagReward(plan: AllianceActionPlan) {
            plan.rewardShares().forEach { (playerId, share) ->
                if (share > 0) silkBagChanges[playerId] = (silkBagChanges[playerId] ?: 0) + 1
            }
        }

        event.allianceActionPlan?.let { plan ->
            val targetId = event.defender.id
            when (event.actionType) {
                ActionType.DUEL -> {
                    when (decision) {
                        0 -> {
                            applyAllianceReward(plan, baseStake)
                            veinChanges[targetId] = (veinChanges[targetId] ?: 0) - baseStake * 3
                        }
                        1 -> applyAlliancePenalty(plan, baseStake)
                        2 -> {
                            applyAllianceReward(plan, baseStake / 2)
                            veinChanges[targetId] = (veinChanges[targetId] ?: 0) - (baseStake / 2) * 3
                        }
                    }
                }
                ActionType.RAID -> {
                    when (decision) {
                        0 -> {
                            applyAllianceReward(plan, 15)
                            veinChanges[targetId] = (veinChanges[targetId] ?: 0) - 45
                        }
                        1 -> {}
                        2 -> applyAlliancePenalty(plan, 15)
                    }
                }
                ActionType.DEFEND_ARRAY -> {}
                ActionType.EXPLORE -> {
                    if (decision == 0) applyAllianceReward(plan, 5)
                    if (decision == 1) applyAllianceSilkBagReward(plan)
                }
            }

            return currentPlayers.map { player ->
                val v = player.spiritVeins + (veinChanges[player.id] ?: 0)
                val bags = player.silkBag + (silkBagChanges[player.id] ?: 0)
                val finalVeins = v.coerceAtLeast(0)
                val newStatus = if (finalVeins <= 0) PlayerStatus.ELIMINATED else player.status
                player.copy(
                    spiritVeins = finalVeins,
                    status = newStatus,
                    silkBag = bags
                )
            }
        }

        when (event.actionType) {
            ActionType.DUEL -> {
                when (decision) {
                    0 -> applyTransfer(event.attacker.id, event.defender.id, baseStake)
                    1 -> applyTransfer(event.defender.id, event.attacker.id, baseStake)
                    2 -> applyTransfer(event.attacker.id, event.defender.id, baseStake / 2)
                }
            }
            ActionType.RAID -> {
                when (decision) {
                    0 -> applyTransfer(event.attacker.id, event.defender.id, 15)
                    1 -> {}
                    2 -> applyTransfer(event.defender.id, event.attacker.id, 15)
                }
            }
            ActionType.DEFEND_ARRAY -> {

            }
            ActionType.EXPLORE -> {
                if (decision == 0) applyExploreReward(event.attacker.id, 5)
                if (decision == 1) applySilkBagReward(event.attacker.id)
            }
        }

        return currentPlayers.map { player ->
            val v = player.spiritVeins + (veinChanges[player.id] ?: 0)
            val bags = player.silkBag + (silkBagChanges[player.id] ?: 0)

            val finalVeins = v.coerceAtLeast(0)
            val newStatus = if (finalVeins <= 0) PlayerStatus.ELIMINATED else player.status

            player.copy(
                spiritVeins = finalVeins,
                status = newStatus,
                silkBag = bags
            )
        }
    }

    /**
     * 返回玩家的结算阵营，包含仍存活且互相绑定的盟友。
     */
    private fun allianceSide(players: List<Player>, playerId: String): List<Player> {
        val player = players.find { it.id == playerId } ?: return emptyList()
        if (player.status == PlayerStatus.ELIMINATED) return emptyList()

        val partner = player.alliancePartnerId
            ?.let { partnerId -> players.find { it.id == partnerId } }
            ?.takeIf { it.status != PlayerStatus.ELIMINATED && it.alliancePartnerId == player.id }

        return if (partner != null) {
            listOf(player, partner)
        } else {
            listOf(player)
        }
    }

    /**
     * 对正向灵脉收益应用天道庇佑和幽魂干预。
     */
    private fun calculateGain(player: Player, base: Int, ghostPower: Int): Int {
        var gain = base.toFloat()
        if (player.hasHeavenProtection) {
            gain *= 1.1f
        }
        return gain.toInt() + ghostPower
    }
}
