package com.example.demo.engine

import com.example.demo.model.*
import com.example.demo.network.MsgSubmitAction

/**
 * 游戏核心规则引擎 (Domain Layer)
 * 负责处理所有的业务判定、灵脉核算、状态变迁等纯逻辑操作。
 * 不涉及任何网络通信和 UI 状态流。
 */
object GameRuleEngine {

    /**
     * 阶段 1 -> 阶段 2：刷新玩家的天道庇佑状态
     * 规则：在所有存活玩家中，找出灵脉最少者，赋予天道庇佑 (+10% 收益)
     */
    fun assignHeavenProtection(players: List<Player>): List<Player> {
        val alivePlayers = players.filter { it.status != PlayerStatus.ELIMINATED }

        // 1. 找到全场的最低灵脉值
        val minVeins = alivePlayers.minOfOrNull { it.spiritVeins } ?: return players

        // 2. 统计处于这个最低谷的玩家数量
        val bottomPlayersCount = alivePlayers.count { it.spiritVeins == minVeins }

        // 3. 只有当“唯一”倒数第一时，才算作真正的弱势群体
        val isUniqueBottom = bottomPlayersCount == 1

        return players.map { player ->
            if (player.status != PlayerStatus.ELIMINATED) {
                // 必须是唯一最低分，且本人的分数就是最低分，才给予庇佑
                player.copy(hasHeavenProtection = (isUniqueBottom && player.spiritVeins == minVeins))
            } else {
                // 幽魂游离三界之外，强制剥夺庇佑
                player.copy(hasHeavenProtection = false)
            }
        }
    }

    /**
     * 阶段 1 -> 阶段 2：生成事件队列
     * 规则：气运鼎盛者（灵脉多）优先出手；并且系统会自动预判“奇袭撞护盾”的情况
     */
    fun buildEventQueue(
        players: List<Player>,
        submittedActions: Map<String, MsgSubmitAction>,
        dayNumber: Int
    ): List<GameEvent> {
        // 只取存活的玩家，并按灵脉从大到小排序（气运鼎盛者先动）
        val sortedAlivePlayers = players
            .filter { it.status != PlayerStatus.ELIMINATED }
            .sortedByDescending { it.spiritVeins }

        // ================= 第一步：生成有效事件，并剔除多余事件 =================
        val validEvents = sortedAlivePlayers.mapNotNull { attacker ->
            val myAction = submittedActions[attacker.id] ?: return@mapNotNull null
            val targetAction = submittedActions[myAction.targetId]
            val defender = players.find { it.id == myAction.targetId } ?: return@mapNotNull null

            var isSystemDetermined = false
            var systemIndex: Int? = null
            var memo = ""

            when (myAction.actionType) {
                ActionType.RAID -> {
                    // 如果目标刚好开启了护宗大阵
                    if (targetAction?.actionType == ActionType.DEFEND_ARRAY) {
                        isSystemDetermined = true
                        systemIndex = 2 // 对应 RAID 的 "被防御" 选项
                        memo = "【系统】目标已开启护宗大阵，奇袭受挫！"
                    }
                }
                ActionType.DEFEND_ARRAY -> {
                    // 检查是否有人（任何人）奇袭了自己
                    val isRaided = submittedActions.values.any { it.targetId == attacker.id && it.actionType == ActionType.RAID }

                    // 【核心修改】：如果被奇袭了（防御成功），因为反伤灵脉已在奇袭者的事件里结算完毕，
                    // 所以这里直接返回 null，不在公开结算庭里产生独立的冗余事件！
                    if (isRaided) return@mapNotNull null

                    isSystemDetermined = true
                    systemIndex = 0
                    memo = "【系统】四海太平，大阵空转。"
                }
                else -> {}
            }

            // 构建事件（暂时不填序号）
            GameEvent(
                id = "event_${System.currentTimeMillis()}_${attacker.id}",
                attacker = attacker,
                defender = defender,
                actionType = myAction.actionType,
                dayNumber = dayNumber,
                eventIndex = 0, // 稍后统一编号
                totalEvents = 0, // 稍后统一计算
                stake = myAction.stake,
                karmicInfluence = 0,
                totalPlayers = players.size,
                hostDecisionIndex = systemIndex,
                isSystemDetermined = isSystemDetermined,
                systemMemo = memo,
                confirmCount = 0
            )
        }

        // ================= 第二步：重新连续编号 =================
        // 防止因为移除了防御事件，导致事件序号出现 1/4, 3/4 这种断层
        return validEvents.mapIndexed { index, event ->
            event.copy(
                eventIndex = index + 1,
                totalEvents = validEvents.size
            )
        }
    }

    /**
     * 阶段 2 表决通过：核算灵脉吞吐，并判定道统是否断绝
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

        // ================= 四大法门因果核算 =================
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
                    0 -> applyTransfer(event.attacker.id, event.defender.id, 10)
                    1 -> {}
                    2 -> applyTransfer(event.defender.id, event.attacker.id, 10)
                }
            }
            ActionType.DEFEND_ARRAY -> {
                // 基础防御逻辑已在 RAID 联动中结算。
            }
            ActionType.EXPLORE -> {
                if (decision == 0) applyExploreReward(event.attacker.id, 5)
                if (decision == 1) applySilkBagReward(event.attacker.id)
            }
        }

        return currentPlayers.map { player ->
            val v = player.spiritVeins + (veinChanges[player.id] ?: 0)
            val bags = player.silkBag + (silkBagChanges[player.id] ?: 0)

            // ================= 强制规则兜底 =================
            val finalVeins = v.coerceAtLeast(0) // 防止灵脉出现负数
            val newStatus = if (finalVeins <= 0) PlayerStatus.ELIMINATED else player.status

            player.copy(
                spiritVeins = finalVeins,
                status = newStatus,
                silkBag = bags
            )
        }
    }

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
     * 辅助计算器：结算天道庇佑 (10% 加成) 与 幽魂业力的干预
     */
    private fun calculateGain(player: Player, base: Int, ghostPower: Int): Int {
        var gain = base.toFloat()
        if (player.hasHeavenProtection) {
            gain *= 1.1f // 弱者加成
        }
        return gain.toInt() + ghostPower // 叠加幽冥干预
    }
}
