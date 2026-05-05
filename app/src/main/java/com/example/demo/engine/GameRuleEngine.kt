package com.example.demo.engine

import com.example.demo.model.*
import com.example.demo.network.MsgSubmitAction
import kotlin.math.absoluteValue

/**
 * 游戏规则纯逻辑引擎，负责阶段转换、事件生成和结算计算。
 */
object GameRuleEngine {

    data class SilkBagUseOutcome(
        val players: List<Player>,
        val event: GameEvent?,
        val log: SilkBagUseLog?,
        val publicMessage: String,
        val privateMessage: String = "",
        val success: Boolean = true
    )

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
            val attackerEffects = attacker.activeSilkBagEffects.filter { !it.isConsumed }
            val defenderEffects = defender.activeSilkBagEffects.filter { !it.isConsumed }

            var isSystemDetermined = false
            var systemIndex: Int? = null
            var memo = ""

            when (myAction.actionType) {
                ActionType.RAID -> {
                    val bypassArray = attackerEffects.any {
                        it.cardId == SilkBagId.PO_ZHEN_ZHEN || it.cardId == SilkBagId.MAN_TIAN_GUO_HAI
                    }
                    val defenderHasArray = targetAction?.actionType == ActionType.DEFEND_ARRAY ||
                        defenderEffects.any {
                            it.cardId == SilkBagId.GU_RUO_JIN_TANG || it.cardId == SilkBagId.JIN_ZHONG_HU_TI
                        }
                    if (defenderHasArray && !bypassArray) {
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
                val extra = if (winner.hasActiveSilkBag(SilkBagId.LING_QUAN_XIAO_QI)) 5 else 0
                val multiplier = if (winner.hasActiveSilkBag(SilkBagId.YI_YI_DAI_LAO)) 3 else 1
                val gain = calculateGain(winner, (base + extra) * multiplier, ghostPower)
                veinChanges[winner.id] = (veinChanges[winner.id] ?: 0) + gain
            }
        }

        fun applySilkBagReward(playerId: String) {
            allianceSide(currentPlayers, playerId).forEach { winner ->
                val extra = if (winner.hasActiveSilkBag(SilkBagId.LING_QUAN_XIAO_QI)) 1 else 0
                val multiplier = if (winner.hasActiveSilkBag(SilkBagId.YI_YI_DAI_LAO)) 3 else 1
                silkBagChanges[winner.id] = (silkBagChanges[winner.id] ?: 0) + (1 + extra) * multiplier
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
            val multiplier = if (currentPlayers.any { it.hasActiveSilkBag(SilkBagId.DUAN_QI_SHU) }) 1 else 3
            val total = base * multiplier
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
            val multiplier = if (currentPlayers.any { it.hasActiveSilkBag(SilkBagId.DUAN_QI_SHU) }) 1 else 3
            val total = base * multiplier
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
                            val multiplier = if (currentPlayers.any { it.hasActiveSilkBag(SilkBagId.DUAN_QI_SHU) }) 1 else 3
                            veinChanges[targetId] = (veinChanges[targetId] ?: 0) - baseStake * multiplier
                        }
                        1 -> applyAlliancePenalty(plan, baseStake)
                        2 -> {
                            applyAllianceReward(plan, baseStake / 2)
                            val multiplier = if (currentPlayers.any { it.hasActiveSilkBag(SilkBagId.DUAN_QI_SHU) }) 1 else 3
                            veinChanges[targetId] = (veinChanges[targetId] ?: 0) - (baseStake / 2) * multiplier
                        }
                    }
                }
                ActionType.RAID -> {
                    when (decision) {
                        0 -> {
                            applyAllianceReward(plan, 15)
                            val multiplier = if (currentPlayers.any { it.hasActiveSilkBag(SilkBagId.DUAN_QI_SHU) }) 1 else 3
                            veinChanges[targetId] = (veinChanges[targetId] ?: 0) - 15 * multiplier
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

            return finalizeOutcomePlayers(currentPlayers, veinChanges, silkBagChanges, event)
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

        return finalizeOutcomePlayers(currentPlayers, veinChanges, silkBagChanges, event)
    }

    fun updateEventOutcomeRecord(event: GameEvent, beforePlayers: List<Player>, afterPlayers: List<Player>): GameEvent {
        val veinChanges = afterPlayers.associate { player ->
            val before = beforePlayers.find { it.id == player.id }
            player.id to (player.spiritVeins - (before?.spiritVeins ?: player.spiritVeins))
        }.filterValues { it != 0 }
        val silkBagChanges = afterPlayers.associate { player ->
            val before = beforePlayers.find { it.id == player.id }
            player.id to (player.silkBagCards.size - (before?.silkBagCards?.size ?: before?.silkBag ?: player.silkBagCards.size))
        }.filterValues { it != 0 }
        return event.copy(
            veinChangesByPlayerId = veinChanges,
            silkBagChangesByPlayerId = silkBagChanges
        )
    }

    fun useSilkBag(
        players: List<Player>,
        request: SilkBagUseRequest,
        dayNumber: Int,
        currentPhase: GamePhase,
        currentEvent: GameEvent?,
        submittedActions: Map<String, MsgSubmitAction>
    ): SilkBagUseOutcome {
        val owner = players.find { it.id == request.playerId }
            ?: return failedSilkBagUse(players, currentEvent, "没有找到使用锦囊的玩家")
        val instance = owner.silkBagCards.find { it.instanceId == request.instanceId }
            ?: return failedSilkBagUse(players, currentEvent, "你没有这张锦囊")
        val definition = SilkBagCatalog.get(instance.cardId)
        if (definition.isOncePerGame && owner.usedOnceSilkBagIds.contains(definition.id)) {
            return failedSilkBagUse(players, currentEvent, "这张锦囊整局只能使用一次")
        }
        if (!isTimingAllowed(definition, currentPhase, currentEvent)) {
            return failedSilkBagUse(players, currentEvent, "当前时机不能使用【${definition.name}】")
        }
        if (definition.needsTarget && request.targetPlayerId == null) {
            return failedSilkBagUse(players, currentEvent, "请选择锦囊目标")
        }
        val sameTypeUsed = owner.activeSilkBagEffects.any {
            it.dayNumber == dayNumber &&
                it.cardId == definition.id &&
                !it.isConsumed
        }
        if (sameTypeUsed && definition.timing != SilkBagEffectTiming.PASSIVE) {
            return failedSilkBagUse(players, currentEvent, "本回合已经声明过这类锦囊效果")
        }
        val usedSameCategory = owner.activeSilkBagEffects.any { effect ->
            effect.dayNumber == dayNumber &&
                !effect.isConsumed &&
                SilkBagCatalog.get(effect.cardId).isPassive == definition.isPassive
        }
        if (usedSameCategory) {
            return failedSilkBagUse(
                players,
                currentEvent,
                if (definition.isPassive) "本回合已经触发过被动锦囊" else "本回合已经主动使用过锦囊"
            )
        }

        val target = request.targetPlayerId?.let { id -> players.find { it.id == id } }
        val baseLog = SilkBagUseLog(
            logId = "silk_log_${System.currentTimeMillis()}_${owner.id}",
            cardId = definition.id,
            cardName = definition.name,
            ownerPlayerId = owner.id,
            targetPlayerId = target?.id,
            eventId = request.eventId,
            dayNumber = dayNumber,
            publicMessage = "",
            requiresHostDecision = definition.needsHostDecision
        )
        val withoutCard = players.replacePlayer(owner.consumeSilkBag(instance, definition))
        val activeEffect = ActiveSilkBagEffect(
            effectId = "silk_effect_${System.currentTimeMillis()}_${owner.id}",
            cardId = definition.id,
            ownerPlayerId = owner.id,
            targetPlayerId = target?.id,
            dayNumber = dayNumber,
            remainingRounds = when (definition.id) {
                SilkBagId.XIU_YANG_SHENG_XI -> 3
                SilkBagId.JIN_ZHONG_HU_TI -> 2
                else -> 1
            }
        )

        fun withEffect(message: String, privateMessage: String = ""): SilkBagUseOutcome {
            val playersWithEffect = withoutCard.map { player ->
                if (player.id == owner.id) player.copy(activeSilkBagEffects = player.activeSilkBagEffects + activeEffect) else player
            }.syncSilkBagCounts()
            val log = baseLog.copy(publicMessage = message, privateMessage = privateMessage)
            return SilkBagUseOutcome(
                players = playersWithEffect,
                event = appendSilkBagLog(currentEvent, log),
                log = log,
                publicMessage = message,
                privateMessage = privateMessage
            )
        }

        return when (definition.id) {
            SilkBagId.PAO_ZHUAN_YIN_YU -> {
                val extraCard = owner.silkBagCards.firstOrNull { it.instanceId != instance.instanceId }
                    ?: return failedSilkBagUse(players, currentEvent, "需要至少再持有一张锦囊才能抛砖引玉")
                val updated = players.map { player ->
                    if (player.id == owner.id) {
                        val cards = player.silkBagCards.filterNot {
                            it.instanceId == instance.instanceId || it.instanceId == extraCard.instanceId
                        }
                        player.copy(spiritVeins = player.spiritVeins + 30, silkBagCards = cards).withSyncedSilkBagCount()
                    } else {
                        player
                    }
                }
                val msg = "【锦囊】${owner.name} 使用【${definition.name}】，弃置一张锦囊并获得30点灵脉。"
                val log = baseLog.copy(publicMessage = msg)
                SilkBagUseOutcome(updated, appendSilkBagLog(currentEvent, log), log, msg)
            }
            SilkBagId.WANG_QI_XUN_LONG -> {
                val count = target?.silkBagCards?.size ?: 0
                val sampledName = target?.silkBagCards
                    ?.minByOrNull { "${owner.id}_${it.instanceId}".hashCode().absoluteValue }
                    ?.let { SilkBagCatalog.get(it.cardId).name }
                val privateMessage = if (count == 0) {
                    "【望气寻龙】${target?.name ?: "目标"} 当前没有锦囊。"
                } else {
                    "【望气寻龙】${target?.name ?: "目标"} 持有 $count 张锦囊，随机窥见【$sampledName】。"
                }
                val msg = "【锦囊】${owner.name} 使用【${definition.name}】，窥探一线天机。"
                val log = baseLog.copy(publicMessage = msg, privateMessage = privateMessage)
                SilkBagUseOutcome(withoutCard.syncSilkBagCounts(), appendSilkBagLog(currentEvent, log), log, msg, privateMessage)
            }
            SilkBagId.TING_FENG_BIAN_WEI -> {
                val targetAction = request.targetPlayerId?.let { submittedActions[it] }
                val privateMessage = if (targetAction == null) {
                    "【听风辨位】${target?.name ?: "目标"} 尚未提交行动。"
                } else {
                    val actionTargetName = players.find { it.id == targetAction.targetId }?.name ?: "未知目标"
                    "【听风辨位】${target?.name ?: "目标"} 已提交${targetAction.actionType.displayName()}，目标为 $actionTargetName。"
                }
                val msg = "【锦囊】${owner.name} 使用【${definition.name}】，辨得风中动静。"
                val log = baseLog.copy(publicMessage = msg, privateMessage = privateMessage)
                SilkBagUseOutcome(withoutCard.syncSilkBagCounts(), appendSilkBagLog(currentEvent, log), log, msg, privateMessage)
            }
            SilkBagId.HUA_DI_WEI_LAO -> {
                if (target != null && submittedActions.containsKey(target.id)) {
                    return failedSilkBagUse(players, currentEvent, "目标已经提交行动，不能被画地为牢")
                }
                withEffect("【锦囊】${owner.name} 使用【${definition.name}】，${target?.name ?: "目标"} 本回合被禁锢。")
            }
            SilkBagId.PO_ZHEN_ZHEN,
            SilkBagId.JIN_ZHONG_HU_TI,
            SilkBagId.LING_QUAN_XIAO_QI,
            SilkBagId.DUAN_QI_SHU,
            SilkBagId.YIN_GUO_SUO_LIAN,
            SilkBagId.GU_RUO_JIN_TANG,
            SilkBagId.MAN_TIAN_GUO_HAI,
            SilkBagId.TI_JIE_ZHI_REN,
            SilkBagId.MOU_DING_ER_DONG -> {
                withEffect("【锦囊】${owner.name} 声明【${definition.name}】，效果已记录，结算时生效。")
            }
            else -> {
                val hostNote = if (definition.needsHostDecision) "，等待房主裁决时录入" else ""
                withEffect("【锦囊】${owner.name} 使用【${definition.name}】$hostNote。")
            }
        }
    }

    fun resetDailySilkBagEffects(players: List<Player>): List<Player> {
        return players.map { player ->
            val nextEffects = player.activeSilkBagEffects.mapNotNull { effect ->
                when {
                    effect.remainingRounds > 1 -> effect.copy(remainingRounds = effect.remainingRounds - 1)
                    else -> null
                }
            }
            player.copy(activeSilkBagEffects = nextEffects)
        }.syncSilkBagCounts()
    }

    fun List<Player>.syncSilkBagCounts(): List<Player> = map { player ->
        if (player.silkBagCards.isEmpty() && player.silkBag > 0) {
            val migratedCards = (0 until player.silkBag).map { index ->
                SilkBagCatalog.drawInstance("migrate_${player.id}_$index")
            }
            player.copy(silkBagCards = migratedCards).withSyncedSilkBagCount()
        } else {
            player.withSyncedSilkBagCount()
        }
    }

    private fun finalizeOutcomePlayers(
        currentPlayers: List<Player>,
        veinChanges: Map<String, Int>,
        silkBagChanges: Map<String, Int>,
        event: GameEvent
    ): List<Player> {
        return currentPlayers.map { player ->
            val rawChange = veinChanges[player.id] ?: 0
            val protectedChange = when {
                rawChange < 0 && player.hasActiveSilkBag(SilkBagId.JIN_CHAN_TUO_KE) -> 0
                rawChange < 0 && player.hasActiveSilkBag(SilkBagId.HU_XIN_LING_FU) -> (rawChange + 10).coerceAtMost(0)
                else -> rawChange
            }
            val v = player.spiritVeins + protectedChange
            val newCards = drawSilkBagsForPlayer(
                player,
                silkBagChanges[player.id] ?: 0,
                "${event.id}_${player.id}_${event.dayNumber}"
            )
            val paperDollTriggered = v <= 0 && player.hasActiveSilkBag(SilkBagId.TI_JIE_ZHI_REN)
            val finalVeins = if (paperDollTriggered) 1 else v.coerceAtLeast(0)
            val newStatus = if (finalVeins <= 0) PlayerStatus.ELIMINATED else player.status
            player.copy(
                spiritVeins = finalVeins,
                status = newStatus,
                silkBagCards = if (paperDollTriggered) emptyList() else newCards
            ).withSyncedSilkBagCount()
        }
    }

    private fun drawSilkBagsForPlayer(player: Player, count: Int, seedPrefix: String): List<SilkBagInstance> {
        if (count <= 0) return player.silkBagCards
        return player.silkBagCards + (0 until count).map { index ->
            SilkBagCatalog.drawInstance("${seedPrefix}_${player.silkBagCards.size}_$index")
        }
    }

    private fun appendSilkBagLog(event: GameEvent?, log: SilkBagUseLog): GameEvent? {
        return event?.copy(silkBagUseLogs = event.silkBagUseLogs + log)
    }

    private fun Player.consumeSilkBag(instance: SilkBagInstance, definition: SilkBagDefinition): Player {
        val onceIds = if (definition.isOncePerGame) usedOnceSilkBagIds + definition.id else usedOnceSilkBagIds
        return copy(
            silkBagCards = silkBagCards.filterNot { it.instanceId == instance.instanceId },
            usedOnceSilkBagIds = onceIds
        ).withSyncedSilkBagCount()
    }

    private fun Player.withSyncedSilkBagCount(): Player {
        return copy(silkBag = silkBagCards.size)
    }

    private fun Player.hasActiveSilkBag(cardId: SilkBagId): Boolean {
        return activeSilkBagEffects.any { it.cardId == cardId && !it.isConsumed }
    }

    private fun List<Player>.replacePlayer(updated: Player): List<Player> {
        return map { if (it.id == updated.id) updated else it }
    }

    private fun failedSilkBagUse(players: List<Player>, event: GameEvent?, message: String): SilkBagUseOutcome {
        return SilkBagUseOutcome(players, event, null, message, success = false)
    }

    private fun isTimingAllowed(definition: SilkBagDefinition, phase: GamePhase, currentEvent: GameEvent?): Boolean {
        return when (definition.timing) {
            SilkBagEffectTiming.PHASE1_BEFORE_ACTION,
            SilkBagEffectTiming.PHASE1_ACTION_CHOSEN,
            SilkBagEffectTiming.CONSPIRACY,
            SilkBagEffectTiming.ALLIANCE -> phase == GamePhase.PHASE_1
            SilkBagEffectTiming.PHASE2_BEFORE_DECISION -> phase == GamePhase.PHASE_2 && currentEvent?.hostDecisionIndex == null
            SilkBagEffectTiming.PHASE2_AFTER_OUTCOME -> phase == GamePhase.PHASE_2 && currentEvent?.hostDecisionIndex != null
            SilkBagEffectTiming.END_OF_DAY,
            SilkBagEffectTiming.PASSIVE,
            SilkBagEffectTiming.ANYTIME -> true
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

    private fun ActionType.displayName(): String {
        return when (this) {
            ActionType.DUEL -> "斗法"
            ActionType.RAID -> "奇袭"
            ActionType.DEFEND_ARRAY -> "护宗大阵"
            ActionType.EXPLORE -> "探索"
        }
    }
}
