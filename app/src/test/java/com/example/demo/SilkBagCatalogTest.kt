package com.example.demo

import com.example.demo.engine.GameRuleEngine
import com.example.demo.model.ActionType
import com.example.demo.model.ActiveSilkBagEffect
import com.example.demo.model.AllianceActionPlan
import com.example.demo.model.GameEvent
import com.example.demo.model.GamePhase
import com.example.demo.model.Player
import com.example.demo.model.PlayerStatus
import com.example.demo.model.SilkBagCatalog
import com.example.demo.model.SilkBagId
import com.example.demo.model.SilkBagInstance
import com.example.demo.model.SilkBagUseRequest
import com.example.demo.network.MsgSubmitAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilkBagCatalogTest {
    @Test
    fun catalogContainsAllCardsWithUniqueNumbers() {
        val cards = SilkBagCatalog.definitions

        assertEquals(53, cards.size)
        assertEquals(53, cards.map { it.id }.toSet().size)
        assertEquals((1..53).toList(), cards.map { it.number })
        assertEquals("望气寻龙", cards.first().name)
        assertEquals("画地为牢", cards.last().name)
    }

    @Test
    fun oncePerGameAndCopyableFlagsAreDefined() {
        val paperDoll = SilkBagCatalog.get(SilkBagId.TI_JIE_ZHI_REN)
        val allArts = SilkBagCatalog.get(SilkBagId.WAN_FA_GUI_YI)

        assertTrue(paperDoll.isOncePerGame)
        assertFalse(paperDoll.isCopyable)
        assertFalse(allArts.isCopyable)
    }

    @Test
    fun exploreRewardDrawsConcreteCardAndSyncsCount() {
        val attacker = Player(id = "a", name = "甲")
        val defender = Player(id = "b", name = "乙")
        val event = GameEvent(
            id = "event_test_explore",
            attacker = attacker,
            defender = defender,
            actionType = ActionType.EXPLORE,
            dayNumber = 1,
            eventIndex = 1,
            totalEvents = 1,
            hostDecisionIndex = 1
        )

        val players = GameRuleEngine.resolveEventOutcome(event, listOf(attacker, defender))
        val updated = players.first { it.id == "a" }

        assertEquals(1, updated.silkBagCards.size)
        assertEquals(updated.silkBagCards.size, updated.silkBag)
    }

    @Test
    fun useSilkBagConsumesCardAndRecordsEffect() {
        val instance = SilkBagInstance("card_1", SilkBagId.PO_ZHEN_ZHEN)
        val owner = Player(id = "a", name = "甲", silkBagCards = listOf(instance), silkBag = 1)
        val target = Player(id = "b", name = "乙")

        val outcome = GameRuleEngine.useSilkBag(
            players = listOf(owner, target),
            request = SilkBagUseRequest(playerId = "a", instanceId = "card_1"),
            dayNumber = 1,
            currentPhase = GamePhase.PHASE_1,
            currentEvent = null,
            submittedActions = emptyMap()
        )

        val updated = outcome.players.first { it.id == "a" }
        assertTrue(outcome.success)
        assertEquals(0, updated.silkBagCards.size)
        assertEquals(0, updated.silkBag)
        assertTrue(updated.activeSilkBagEffects.any { it.cardId == SilkBagId.PO_ZHEN_ZHEN })
    }

    @Test
    fun huaDiWeiLaoRejectsSubmittedTarget() {
        val instance = SilkBagInstance("card_53", SilkBagId.HUA_DI_WEI_LAO)
        val owner = Player(id = "a", name = "甲", silkBagCards = listOf(instance), silkBag = 1)
        val target = Player(id = "b", name = "乙")

        val outcome = GameRuleEngine.useSilkBag(
            players = listOf(owner, target),
            request = SilkBagUseRequest(playerId = "a", instanceId = "card_53", targetPlayerId = "b"),
            dayNumber = 1,
            currentPhase = GamePhase.PHASE_1,
            currentEvent = null,
            submittedActions = mapOf("b" to MsgSubmitAction("b", "a", ActionType.DUEL, 10))
        )

        assertFalse(outcome.success)
        assertEquals("目标已经提交行动，不能被画地为牢", outcome.publicMessage)
    }

    @Test
    fun huXinLingFuReducesFirstTenLoss() {
        val attacker = Player(id = "a", name = "甲")
        val defender = Player(
            id = "b",
            name = "乙",
            activeSilkBagEffects = listOf(
                ActiveSilkBagEffect("effect_1", SilkBagId.HU_XIN_LING_FU, "b", dayNumber = 1)
            )
        )
        val event = GameEvent(
            id = "event_test_duel",
            attacker = attacker,
            defender = defender,
            actionType = ActionType.DUEL,
            dayNumber = 1,
            eventIndex = 1,
            totalEvents = 1,
            stake = 30,
            hostDecisionIndex = 0
        )

        val players = GameRuleEngine.resolveEventOutcome(event, listOf(attacker, defender))

        assertEquals(80, players.first { it.id == "b" }.spiritVeins)
    }

    @Test
    fun duanQiShuCancelsAllianceMultiplier() {
        val attacker = Player(
            id = "a",
            name = "甲",
            alliancePartnerId = "c",
            status = PlayerStatus.ALLIANCED,
            activeSilkBagEffects = listOf(
                ActiveSilkBagEffect("effect_16", SilkBagId.DUAN_QI_SHU, "a", dayNumber = 1)
            )
        )
        val partner = Player(id = "c", name = "丙", alliancePartnerId = "a", status = PlayerStatus.ALLIANCED)
        val defender = Player(id = "b", name = "乙")
        val plan = AllianceActionPlan(
            allianceId = "plan_1",
            firstPlayerId = "a",
            secondPlayerId = "c",
            actionType = ActionType.RAID,
            targetId = "b",
            targetName = "乙",
            rewardShareFirst = 100,
            rewardShareSecond = 0,
            penaltyShareFirst = 50,
            penaltyShareSecond = 50,
            confirmedPlayerIds = listOf("a", "c")
        )
        val event = GameEvent(
            id = "event_test_alliance",
            attacker = attacker,
            defender = defender,
            actionType = ActionType.RAID,
            dayNumber = 1,
            eventIndex = 1,
            totalEvents = 1,
            hostDecisionIndex = 0,
            isAllianceAction = true,
            alliancePartner = partner,
            allianceActionPlan = plan
        )

        val players = GameRuleEngine.resolveEventOutcome(event, listOf(attacker, partner, defender))

        assertEquals(115, players.first { it.id == "a" }.spiritVeins)
        assertEquals(85, players.first { it.id == "b" }.spiritVeins)
    }

    @Test
    fun tiJieZhiRenKeepsOneVeinAndDiscardsHand() {
        val attacker = Player(id = "a", name = "甲")
        val defender = Player(
            id = "b",
            name = "乙",
            spiritVeins = 10,
            silkBagCards = listOf(SilkBagInstance("paper", SilkBagId.TI_JIE_ZHI_REN)),
            silkBag = 1,
            activeSilkBagEffects = listOf(
                ActiveSilkBagEffect("effect_21", SilkBagId.TI_JIE_ZHI_REN, "b", dayNumber = 1)
            )
        )
        val event = GameEvent(
            id = "event_test_paper",
            attacker = attacker,
            defender = defender,
            actionType = ActionType.DUEL,
            dayNumber = 1,
            eventIndex = 1,
            totalEvents = 1,
            stake = 30,
            hostDecisionIndex = 0
        )

        val players = GameRuleEngine.resolveEventOutcome(event, listOf(attacker, defender))
        val updatedDefender = players.first { it.id == "b" }

        assertEquals(1, updatedDefender.spiritVeins)
        assertEquals(PlayerStatus.IDLE, updatedDefender.status)
        assertEquals(0, updatedDefender.silkBagCards.size)
    }
}
