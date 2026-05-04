package com.example.demo.ui.silkbag

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.demo.model.Player

/**
 * 锦囊卡牌页，第一阶段和第二阶段共用。
 */
@Composable
fun SilkBagScreen(
    players: List<Player>,
    dayNumber: Int,
    sourcePhaseName: String,
    showCodex: Boolean,
    onCodexClick: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val selfPlayer = remember(players) { players.firstOrNull { it.isSelf } }
    val selfSilkBagCount = selfPlayer?.silkBag ?: 0
    val visibleCards = remember(showCodex, selfSilkBagCount) {
        if (showCodex) {
            silkBagCards
        } else {
            silkBagCards.take(selfSilkBagCount.coerceIn(0, silkBagCards.size))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFF2E2BE),
                        Color(0xFFE5C891),
                        Color(0xFFD7B174)
                    )
                )
            )
            .statusBarsPadding()
    ) {
        SilkBagHeader(
            selfPlayer = selfPlayer,
            dayNumber = dayNumber,
            sourcePhaseName = sourcePhaseName,
            showCodex = showCodex,
            onCodexClick = onCodexClick,
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (visibleCards.isEmpty()) {
                item {
                    EmptySilkBagCard()
                }
            } else {
                items(visibleCards.chunked(2), key = { row -> row.joinToString("-") { it.id.toString() } }) { rowCards ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        rowCards.forEach { card ->
                            SilkBagCardView(
                                card = card,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowCards.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SilkBagHeader(
    selfPlayer: Player?,
    dayNumber: Int,
    sourcePhaseName: String,
    showCodex: Boolean,
    onCodexClick: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color(0xFF5C4033))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (showCodex) "锦囊图鉴" else "锦囊",
                    color = Color(0xFF5C4033),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$sourcePhaseName · 第 $dayNumber 天",
                    color = Color(0xFF6E5435),
                    fontSize = 12.sp
                )
            }
            if (showCodex) {
                SilkBagCountPill(count = selfPlayer?.silkBag ?: 0)
            } else {
                SilkBagCodexButton(onClick = onCodexClick)
            }
        }
    }
}

@Composable
private fun SilkBagCodexButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFF8E8).copy(alpha = 0.86f))
            .border(1.dp, Color(0xFFA8733F), RoundedCornerShape(14.dp))
    ) {
        Icon(
            Icons.Default.MenuBook,
            contentDescription = null,
            tint = Color(0xFFA8733F),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text("锦囊图鉴", color = Color(0xFF5C4033), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SilkBagCountPill(count: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFF8E8).copy(alpha = 0.86f))
            .border(1.dp, Color(0xFFA8733F), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Inventory2,
            contentDescription = null,
            tint = Color(0xFFA8733F),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text("我的锦囊 $count", color = Color(0xFF5C4033), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptySilkBagCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFC38A4C))
            .border(2.dp, Color(0xFF9B6735), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFFFFAEA))
                .border(1.dp, Color(0xFFB98A58), RoundedCornerShape(4.dp))
                .padding(horizontal = 20.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Inventory2,
                contentDescription = null,
                tint = Color(0xFFB98A58).copy(alpha = 0.58f),
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "当前还没有锦囊",
                color = Color(0xFF7A4F2C),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "通过探索获得锦囊后，会在这里显示。",
                color = Color(0xFF6E5435),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SilkBagCardView(
    card: SilkBagCard,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(360.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFC38A4C))
            .border(2.dp, Color(0xFF9B6735), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFFFFAEA))
                .border(1.dp, Color(0xFFB98A58), RoundedCornerShape(4.dp))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = card.name,
                color = Color(0xFF7A4F2C),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFF4E8D5))
                    .border(1.dp, Color(0xFFB98A58), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    tint = Color(0xFFB98A58).copy(alpha = 0.38f),
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "${card.timing}：",
                color = Color(0xFF7A4F2C),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Text(
                text = card.description,
                color = Color(0xFF5C4033),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 5,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (card.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "（${card.note}）",
                    color = Color(0xFFA64B3F),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

private data class SilkBagCard(
    val id: Int,
    val name: String,
    val timing: String,
    val description: String,
    val note: String
)

private val silkBagCards = listOf(
    SilkBagCard(1, "望气寻龙", "一阶段，提交行动前", "查看任意一名存活玩家的锦囊数量，并从其持有锦囊中随机显示1张名称。", "仅使用者可见"),
    SilkBagCard(2, "抛砖引玉", "一阶段，提交行动前", "可丢弃其他一张锦囊，获得30点灵脉。", "不公告丢弃的锦囊"),
    SilkBagCard(3, "借势引雷", "二阶段，自己斗法或奇袭获胜后", "你可以选择使用。若使用，本次收益额外+10点灵脉。", "同盟行动中只对自己的收益份额生效"),
    SilkBagCard(4, "护心灵符", "二阶段，自己即将损失灵脉时", "抵消本次灵脉损失的前10点。", "可抵消斗法、奇袭、同盟惩罚和锦囊伤害"),
    SilkBagCard(5, "反噬咒印", "二阶段，受到斗法或奇袭损失后", "对造成你损失的一方返还10点灵脉伤害。", "若来源是同盟行动，按同盟惩罚协定分担"),
    SilkBagCard(6, "移星换斗", "二阶段，当前事件公开后、裁决前", "选择当前事件中的一名参与者，令其本次正向收益和负面惩罚互换归属。", "仅改变当前事件的收益与惩罚归属"),
    SilkBagCard(7, "破阵针", "一阶段，选择奇袭时", "本回合你的奇袭若遇到护宗大阵，仍进入房主裁决，不再被系统直接判定为奇袭受挫。", "房主仍可裁定奇袭失败或被反制"),
    SilkBagCard(8, "金钟护体", "一阶段，选择护宗大阵时", "你的护宗大阵效果延续到下一回合。下一回合你仍可选择其他行动，并同时享有护宗大阵防御效果。", "延续防御只持续1回合"),
    SilkBagCard(9, "灵泉小憩", "一阶段，选择探索时", "本回合探索若获得灵脉，额外获得5点；若获得锦囊，则锦囊额外+1。", "同盟探索时，只对自己的探索收益生效"),
    SilkBagCard(10, "天机残页", "探索结算后", "若本次探索获得锦囊，你可以立刻再抽1张锦囊，并弃置其中1张。", "当前系统可暂按锦囊数量+1处理"),
    SilkBagCard(11, "密语传音", "密谋期间", "选择一名与你正在密谋的玩家。你可以要求其在本回合公开结算前确认一次行动；若其最终行动与你约定不同，其损失10点灵脉给你。", "约定行动只对密谋双方可见"),
    SilkBagCard(12, "听风辨位", "一阶段，对任意存活玩家使用", "查看目标本回合已提交的行动类型及目标。", "若目标尚未提交行动，只显示未提交"),
    SilkBagCard(13, "口蜜剑心", "密谋成功后", "选择与你密谋的一名玩家。本回合若其主动攻击你，该事件直接判定其失败。", "只对主动攻击你的斗法或奇袭生效"),
    SilkBagCard(14, "心魔誓", "密谋期间", "你与密谋对象立下心魔誓。本回合双方若没有互相攻击，各获得5点灵脉；若任意一方攻击另一方，攻击方额外损失10点灵脉。", "只能用于密谋对象"),
    SilkBagCard(15, "合修契印", "同盟成立后，提交同盟行动前", "本回合同盟行动成功时，双方各获得5点灵脉，并额外获得1张锦囊；失败时，双方各额外损失5点灵脉。", "额外锦囊给奖励份额更高的一方"),
    SilkBagCard(16, "断契术", "二阶段，同盟事件结算前", "若当前事件为同盟行动，你可以取消该同盟的x3倍率，改为按普通行动倍率结算。", "不取消同盟关系"),
    SilkBagCard(17, "背水魔念", "二阶段，发起反水前", "你本次反水判定若成功，额外独吞10点灵脉；若失败，额外损失15点灵脉。", "只作用于当前同盟事件"),
    SilkBagCard(18, "同尘共劫", "二阶段，同盟事件结算前", "若本次同盟行动失败，你可以令本次惩罚总量减少10点灵脉，再按惩罚协定分担。", "最低减少到0"),
    SilkBagCard(19, "斩缘飞剑", "一阶段，提交行动前", "选择一名玩家。本回合你对其斗法或奇袭成功时，收益+5；若未对其行动，本锦囊失效。", "额外收益不参与同盟x3倍率"),
    SilkBagCard(20, "乾坤挪移", "二阶段，自己成为奇袭目标时", "将本次奇袭目标转移给另一名存活玩家，但不能转移给奇袭发起者。", "房主重新确认事件双方后继续裁决"),
    SilkBagCard(21, "替劫纸人", "二阶段，自己即将被淘汰时", "保留1点灵脉不被淘汰，并立即弃置全部锦囊。", "每名玩家整局最多触发一次"),
    SilkBagCard(22, "天道回响", "一阶段，自己拥有天道庇护时", "本回合你的正向灵脉收益额外+10；若本回合没有获得灵脉，则回合结束时获得1张锦囊。", "与天道庇护加成同时生效"),
    SilkBagCard(23, "截运符", "一阶段，提交行动前", "选择一名灵脉高于你的玩家。本回合其第一次正向灵脉收益减少10点。", "不影响锦囊奖励"),
    SilkBagCard(24, "聚灵阵盘", "一阶段，公开声明后", "本回合你不能斗法或奇袭；若回合结束时仍存活，获得10点灵脉。", "可以探索、护宗大阵、密谋或同盟防御型行动"),
    SilkBagCard(25, "血煞丹", "斗法或奇袭裁决前", "本次行动你的收益上限提高10点；若本次行动失败，你额外损失10点灵脉。", "房主裁决时作为额外修正项录入"),
    SilkBagCard(26, "幻身遁影", "一阶段，成为他人行动目标后", "本回合第一次针对你的斗法或奇袭，攻击方需在线下额外判定一次；判定失败则该事件视为无效。", "判定方式由房主或实体规则决定"),
    SilkBagCard(27, "因果锁链", "二阶段，任意事件结算前", "指定当前事件中的一名收益方。本事件该玩家最终灵脉收益减半，向下取整。", "不影响锦囊获得和负面惩罚"),
    SilkBagCard(28, "焚香问卜", "一阶段，选择探索前", "你可以预言本次探索将获得灵脉或锦囊。若二阶段结果命中，额外获得对应奖励。", "命中结果可公开播报"),
    SilkBagCard(29, "万法归一", "随时，其他玩家使用锦囊后", "你获得1张刚刚被使用的同名锦囊。", "不能获得万法归一、替劫纸人和整局限一次锦囊"),
    SilkBagCard(30, "飞升一线", "回合结束结算时", "若你本回合至少获得过15点灵脉，额外获得1张锦囊；若你当前灵脉全场最高，再额外获得5点灵脉。", "并列最高不触发额外5点灵脉"),
    SilkBagCard(31, "调虎离山", "一阶段，同盟成立后", "选择你的本回合同盟对象。本轮结算结束后，你从该盟友处掠夺10点灵脉。", "不影响同盟行动本身"),
    SilkBagCard(32, "欲擒故纵", "一阶段，提交行动前", "本回合若其他玩家主动对你发起斗法，你在该斗法中受到的灵脉损失不生效，并改为获得等量灵脉。", "仅对斗法生效"),
    SilkBagCard(33, "釜底抽薪", "被动触发，回合结算后", "若你本回合累计损失灵脉达到20点或以上，立刻获得30点灵脉。", "每回合最多触发1次"),
    SilkBagCard(34, "浑水摸鱼", "二阶段，其他两方事件成功时", "当另外两方的斗法、奇袭或护宗大阵反制成功并产生正向收益时，你可以获得该收益的一半，向下取整。", "不减少原收益方收益"),
    SilkBagCard(35, "瓮中捉鳖", "二阶段，护宗大阵成功时", "你本次护宗大阵若成功防住奇袭或完成反制，额外从奇袭者处掠夺10点灵脉。", "若奇袭来自同盟行动，按惩罚协定分担"),
    SilkBagCard(36, "休养生息", "一阶段，提交行动前", "接下来3回合你只能选择探索，不能斗法、奇袭或主动同盟。3回合结束后，你获得2张锦囊。", "仍可密谋和使用防御类锦囊"),
    SilkBagCard(37, "众生平等", "一阶段，提交行动前", "若本回合存在其他玩家组成同盟，回合结算时你从每名其他存活玩家处各获得10点灵脉。", "只检查其他玩家的同盟"),
    SilkBagCard(38, "瞒天过海", "一阶段，选择奇袭时", "你本次奇袭无法被护宗大阵防守。", "目标若已选择护宗大阵，仍按普通奇袭进入房主裁决"),
    SilkBagCard(39, "隔岸观火", "一阶段，提交行动前", "本回合你若成为斗法或奇袭目标，自动将该行动转移给另一名存活玩家。", "不能转移给行动发起者"),
    SilkBagCard(40, "借刀杀人", "一阶段，提交行动前", "选择一名存活玩家。本回合你借其名义行动，你的行动无论成功或失败，全部收益和惩罚都由该玩家承担。", "同盟状态下无法使用"),
    SilkBagCard(41, "以逸待劳", "一阶段，选择探索前", "本轮你的探索效果x3。", "探索获得灵脉或锦囊时均按x3处理"),
    SilkBagCard(42, "趁火打劫", "一阶段，提交行动前", "本回合结算后，累计损失灵脉最多的玩家给你10点灵脉。", "若你损失最多则不触发"),
    SilkBagCard(43, "金蝉脱壳", "一阶段，提交行动前", "免除你本回合产生的所有灵脉损失，包括行动、同盟、反水和锦囊造成的损失。", "不免除非灵脉效果"),
    SilkBagCard(44, "再接再厉", "一阶段，选择探索前", "本回合探索若获得灵脉，你可以立刻再进行一次探索判定。", "额外探索最多触发1次"),
    SilkBagCard(45, "固若金汤", "一阶段，提交行动前", "本回合若你遭受奇袭，自动视为你已开启护宗大阵。", "自动防御只对第一次奇袭生效"),
    SilkBagCard(46, "谋定而动", "一阶段，提交行动前", "本回合你不可提交行动；下一回合你可以提交2次行动。", "两次行动分别生成结算"),
    SilkBagCard(47, "不战而胜", "一阶段，提交行动前", "本回合若有玩家主动对你发起斗法，该斗法直接判定为你获胜。", "只对第一次针对你的斗法生效"),
    SilkBagCard(48, "反间计", "一阶段，对任意存活玩家使用", "选择一名玩家。若其本回合与其他玩家同盟，则取消该同盟效果，并改为同盟二人互相斗法。", "若目标没有同盟，本锦囊失效"),
    SilkBagCard(49, "狗急跳墙", "一阶段，自己灵脉20点及以下时", "本回合你可以提交3次行动。", "同盟状态下无法使用"),
    SilkBagCard(50, "乘胜追击", "二阶段，斗法或奇袭获胜后", "你可以立刻追加一次斗法或奇袭。", "追加行动由房主即时插入并裁决"),
    SilkBagCard(51, "过河拆桥", "二阶段，反水时", "你的本次反水必定成功。", "只作用于当前同盟事件"),
    SilkBagCard(52, "亡羊补牢", "一阶段，提交行动前", "记录你上一次受到灵脉损失的行动类型。下一次同类型行动不会对你造成灵脉损失。", "触发一次后失效"),
    SilkBagCard(53, "画地为牢", "一阶段，对任意存活玩家使用", "选择一名玩家。该玩家本回合不可提交行动，也不会损失灵脉；可对自己使用。", "已提交行动的玩家不能被选择")
)
