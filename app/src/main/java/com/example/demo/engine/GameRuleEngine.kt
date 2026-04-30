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

        return currentPlayers.map { player ->
            var v = player.spiritVeins
            var bags = player.silkBag // 需要在 Player.kt 中预留 val silkBag: Int = 0

            val isAttacker = (player.id == event.attacker.id)
            val isDefender = (player.id == event.defender.id && !isAttacker)

            // ================= 四大法门因果核算 =================
            when (event.actionType) {
                ActionType.DUEL -> {
                    when (decision) {
                        0 -> { // 斗法成功：败方给胜方全部赌注
                            if (isAttacker) v += calculateGain(player, baseStake, ghostPower)
                            if (isDefender) v -= baseStake
                        }
                        1 -> { // 斗法失败：攻方反噬，给守方赌注
                            if (isAttacker) v -= baseStake
                            if (isDefender) v += baseStake
                        }
                        2 -> { // 对方投降：给一半灵脉
                            val half = baseStake / 2
                            if (isAttacker) v += calculateGain(player, half, ghostPower)
                            if (isDefender) v -= half
                        }
                    }
                }
                ActionType.RAID -> {
                    when (decision) {
                        0 -> { // 奇袭成功
                            if (isAttacker) v += calculateGain(player, 10, ghostPower)
                            if (isDefender) v -= 10
                        }
                        1 -> { // 奇袭失败：无事发生
                        }
                        2 -> { // 被防御：奇袭者反赔给被奇袭者10条
                            if (isAttacker) v -= 10
                            if (isDefender) v += 10
                        }
                    }
                }
                ActionType.DEFEND_ARRAY -> {
                    // 基础防御逻辑已在上述 RAID 联动中扣除。此处可做额外扩展（如：成功防御奖励造化）
                    if (isAttacker && decision == 1) {
                        // 预留位置
                    }
                }
                ActionType.EXPLORE -> {
                    if (isAttacker) {
                        if (decision == 0) v += calculateGain(player, 5, ghostPower) // 获得灵脉
                        if (decision == 1) bags += 1 // 获得锦囊
                    }
                }
            }

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