package com.rhodes.privatechat.viewmodel

import com.rhodes.privatechat.game.mahjong.AiChat
import com.rhodes.privatechat.game.mahjong.PlayerState
import com.rhodes.privatechat.game.mahjong.Tile
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.model.AnchorType
import com.rhodes.privatechat.shared.model.MahjongSave
import com.rhodes.privatechat.shared.model.MemoryAnchor
import com.rhodes.privatechat.shared.model.Moment
import com.rhodes.privatechat.shared.model.MomentComment
import com.rhodes.privatechat.shared.model.MomentLike
import com.rhodes.privatechat.shared.model.Relationship
import com.rhodes.privatechat.shared.model.RelationshipType
import com.rhodes.privatechat.shared.model.WorldEvent
import com.rhodes.privatechat.shared.model.WorldEventType
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.memory.AnchorSourcePolicy
import com.rhodes.privatechat.viewmodel.shared.MemoryPolicy
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

class MahjongViewModel(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val sharedUtils: SharedUtils,
    private val scope: CoroutineScope,
    private val operatorsProvider: () -> List<com.rhodes.privatechat.shared.model.Operator>
) {

    suspend fun refreshDailyLmb() {
        val today = sharedUtils.beijingSdf("yyyyMMdd").format(java.util.Date())
        val lastRefresh = settings.lmbRefreshDate
        if (lastRefresh == today) return
        settings.lmbRefreshDate = today
        for (op in operatorsProvider()) {
            if (op.lmb < 2000) {
                repository.updateOperator(op.copy(lmb = 2000))
            }
        }
    }

    fun saveMahjongGame(json: String, ruleType: String) {
        scope.launch { repository.saveMahjong(MahjongSave(saveJson = json, ruleType = ruleType)) }
    }

    fun loadMahjongSave(callback: (MahjongSave?) -> Unit) {
        scope.launch { callback(repository.getMahjongSave()) }
    }

    fun deleteMahjongSave() {
        scope.launch { repository.deleteMahjongSave() }
    }

    fun generateMahjongTableTalk(
        player: PlayerState,
        event: String,
        tile: Tile?,
        roundLabel: String,
        wallLeft: Int,
        shanten: Int,
        fallback: String,
        participants: List<String> = emptyList(),
        recentChat: List<String> = emptyList(),
        callback: (String) -> Unit
    ) {
        scope.launch {
            val text = generateMahjongText(buildTableTalkPrompt(player, event, tile, roundLabel, wallLeft, shanten, participants, recentChat), fallback, 36)
            withContext(Dispatchers.Main) { callback(text) }
        }
    }

    fun generateMahjongSettlementLine(
        player: PlayerState?,
        name: String,
        isWinner: Boolean,
        isDraw: Boolean,
        rank: Int,
        netGain: Int,
        summary: String,
        fallback: String,
        callback: (String) -> Unit
    ) {
        scope.launch {
            val text = generateMahjongText(buildSettlementPrompt(player, name, isWinner, isDraw, rank, netGain, summary), fallback, 48)
            withContext(Dispatchers.Main) { callback(text) }
        }
    }

    private suspend fun generateMahjongText(prompt: String, fallback: String, maxChars: Int): String {
        return try {
            if (settings.apiKey.isBlank()) return fallback
            val raw = withTimeout(12_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Mahjong", "mahjong") }
            val cleaned = cleanMahjongLine(raw, maxChars)
            if (isNarrationLike(cleaned) || isInvalidMahjongPresence(cleaned)) fallback else cleaned.ifBlank { fallback }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun buildTableTalkPrompt(player: PlayerState, event: String, tile: Tile?, roundLabel: String, wallLeft: Int, shanten: Int, participants: List<String>, recentChat: List<String>): String {
        val op = operatorsProvider().find { it.id == player.opId || it.name == player.name }
        val userName = settings.userName.ifBlank { "博士" }
        val eventName = when (event) {
            "chi" -> "吃牌"
            "pon" -> "碰牌"
            "kan" -> "杠牌"
            "ron" -> "点炮胡"
            "tsumo" -> "自摸"
            "tenpai" -> "疑似听牌"
            "middle" -> "中盘闲聊"
            "late" -> "终盘提醒"
            "opening" -> "开局闲聊"
            else -> event
        }
        val tileName = tile?.let { Tile.tileName(it) } ?: "无"
        val participantText = participants.ifEmpty { listOf(userName) }.distinct().joinToString("、")
        val recentText = recentChat.takeLast(8).joinToString("\n").ifBlank { "暂无" }
        return """
你正在扮演一个正在活动室打麻将的真人角色。你不是旁白，你就是牌桌上的本人。

【角色】
姓名：${player.name}
称号：${op?.title.orEmpty()}
简介：${op?.description.orEmpty().take(180).ifBlank { "无" }}
对${userName}的关系：${op?.userRelation.orEmpty().ifBlank { "普通熟人" }}
私有人设补充：${op?.privatePrompt.orEmpty().take(240).ifBlank { "无" }}
牌风参数：进攻${player.attack}，防守${player.defense}，鸣牌偏好${player.meldPref}

【牌桌成员】
当前同桌玩家：${participantText}
${userName}就是用户本人，正在牌桌上与你一起打麻将。你可以直接称呼${userName}，但禁止把${userName}说成缺席者。

【最近牌桌发言】
${recentText}

【牌局事件】
当前阶段：$roundLabel
事件：$eventName
相关牌：$tileName
剩余牌山：$wallLeft
        手牌状态：${if (shanten <= 0) "接近胡牌" else "整理牌型中"}

【输出要求】
只输出一句“你本人说出口的话”，不要JSON，不要解释，不要加姓名前缀。
必须是第一人称或直接对别人说话，例如“我杠这一口怎么样？”“你们猜我听哪张？”这种聊天感。
禁止第三人称旁白，禁止出现“${player.name}叹了口气”“${player.name}笑着”“打出9万”这类动作描述。
不要复述事件名称，不要教规则，不要透露隐藏手牌，不要说自己是AI。
禁止说“要是${userName}在这就好了”“如果${userName}在”“${userName}不在”“等${userName}回来”等把用户当作不在场的话。
要接得上最近牌桌发言；不知道接什么就围绕当前事件短句回应。
10到34个中文字符，可以调侃、试探、嘴硬、得意、装淡定、叫板。
如果事件是杠/碰/吃/和牌，要像真的在牌桌上对其他人说一句话。不要提“立直”“宝牌”等日麻规则。
""".trimIndent()
    }

    private fun buildSettlementPrompt(player: PlayerState?, name: String, isWinner: Boolean, isDraw: Boolean, rank: Int, netGain: Int, summary: String): String {
        val op = operatorsProvider().find { it.id == player?.opId || it.name == name }
        val outcome = when {
            isDraw -> "流局"
            isWinner || rank == 1 || netGain > 0 -> "赢了或排名靠前"
            else -> "输了或排名靠后"
        }
        return """
你正在扮演一个刚打完活动室麻将的真人角色。现在大家正在看结算，你要随口说一句感言。

【角色】
姓名：$name
称号：${op?.title.orEmpty()}
简介：${op?.description.orEmpty().take(180).ifBlank { "无" }}
对${settings.userName.ifBlank { "用户" }}的关系：${op?.userRelation.orEmpty().ifBlank { "普通熟人" }}
私有人设补充：${op?.privatePrompt.orEmpty().take(260).ifBlank { "无" }}
牌风参数：进攻${player?.attack ?: 0.5f}，防守${player?.defense ?: 0.5f}，鸣牌偏好${player?.meldPref ?: "medium"}

【结算】
结果：$outcome
名次：第${rank}名
龙门币净收益：${if (netGain >= 0) "+" else ""}$netGain
牌局摘要：${summary.ifBlank { "无" }}

【输出要求】
只输出${name}的一句结算感言，不要JSON，不要解释，不要加姓名前缀。
要像真实牌桌上的获奖感言或吐槽：赢了可以得意、装淡定、挑衅；输了可以嘴硬、心疼龙门币、复盘、怪牌山。
必须符合人设和与${settings.userName.ifBlank { "用户" }}的关系，不要通用鸡汤，不要像旁白，不要说自己是AI。
16到46个中文字符。
""".trimIndent()
    }

    private fun cleanMahjongLine(raw: String, maxChars: Int): String {
        return raw.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            .lines().firstOrNull { it.isNotBlank() }.orEmpty()
            .trim(' ', '"', '“', '”', '：', ':')
            .substringAfter("：")
            .substringAfter(":")
            .take(maxChars)
    }

    private fun isNarrationLike(text: String): Boolean {
        if (text.isBlank()) return true
        val narrationWords = listOf("打出", "摸了一张", "看了一眼", "叹了口气", "笑着", "盯着", "推倒", "摊牌", "牌桌", "空气", "气氛")
        return narrationWords.any { text.contains(it) }
    }

    private fun isInvalidMahjongPresence(text: String): Boolean {
        val userName = settings.userName.ifBlank { "博士" }
        val badPatterns = listOf(
            "要是${userName}在", "如果${userName}在", "${userName}不在", "等${userName}回来",
            "要是博士在", "如果博士在", "博士不在", "等博士回来", "博士在这就好了"
        )
        return badPatterns.any { text.contains(it) }
    }

    fun settleMahjongGame(
        participantNames: List<String>,
        winnerName: String,
        loserName: String,
        winType: String,
        summary: String,
        userNetGain: Int,
        assistantName: String
    ) {
        scope.launch {
            val now = System.currentTimeMillis()
            val ops = operatorsProvider()
            val participantIds = participantNames.mapNotNull { name -> ops.find { it.name == name }?.id }

            // 1. 写锚点：只给参与者和助手
            val anchorTargets = (participantIds + ops.find { it.name == assistantName }?.id.orEmpty()).filter { it.isNotBlank() }.distinct()
            val anchorContent = if (winnerName.isNotBlank()) {
                val gainText = if (userNetGain >= 0) "赢了${userNetGain}龙门币" else "输了${-userNetGain}龙门币"
                "在活动室打了一局麻将，${winnerName}${winType}，${gainText}。$summary"
            } else {
                "在活动室打了一局麻将，流局。$summary"
            }
            val importance = when {
                userNetGain >= 300 || userNetGain <= -300 -> AnchorSourcePolicy.STRONG
                userNetGain != 0 -> AnchorSourcePolicy.MEDIUM
                else -> AnchorSourcePolicy.WEAK
            }
            for (opId in anchorTargets) {
                repository.saveAnchor(AnchorSourcePolicy.buildAnchor(
                    source = AnchorSourcePolicy.MAHJONG,
                    sourceName = "活动室麻将",
                    sourceActor = "麻将对局",
                    sourceTarget = ops.find { it.id == opId }?.name ?: opId,
                    operatorId = opId,
                    type = AnchorType.EVENT,
                    content = anchorContent,
                    importance = importance,
                    sessionId = "mahjong_${now}",
                    createdAt = now,
                    expiresAt = MemoryPolicy.anchorExpiresAt(settings, AnchorType.EVENT)
                ))
            }

            // 2. 写世界事件
            repository.insertWorldEvent(WorldEvent(
                type = WorldEventType.MAHJONG_EVENT,
                actorId = "user",
                actorName = settings.userName.ifBlank { "用户" },
                targetId = participantIds.firstOrNull() ?: "",
                targetName = winnerName,
                source = "mahjong",
                sourceId = "mahjong_${now}",
                content = summary,
                createdAt = now,
                expiresAt = MemoryPolicy.memoryExpiresAt(settings)
            ))

            // 3. 更新关系：同桌打牌小幅增加熟悉度
            for (opId in participantIds) {
                try {
                    val existing = repository.getRelationship("user", opId)
                    if (existing != null) {
                        val newIntimacy = (existing.intimacy + 2).coerceAtMost(1000)
                        repository.insertRelationship(existing.copy(intimacy = newIntimacy))
                    } else {
                        repository.insertRelationship(Relationship(
                            operatorId = "user",
                            relatedOperatorId = opId,
                            relatedOperatorName = ops.find { it.id == opId }?.name ?: opId,
                            type = RelationshipType.FRIEND,
                            intimacy = 2
                        ))
                    }
                } catch (_: Exception) {}
            }

            // 4. 发动态：随机一名参与者发麻将动态
            val momentOp = ops.filter { it.id in participantIds && it.id != "user" }.randomOrNull()
            if (momentOp != null) {
                val momentContent = when {
                    winnerName == momentOp.name -> "今天在活动室赢了一局麻将，手气不错~"
                    loserName == momentOp.name -> "刚才打麻将放铳了…下次一定小心。"
                    winType == "流局" -> "活动室打了一局麻将，流局了，大家都太稳了。"
                    else -> "活动室刚打完一局麻将，${winnerName}赢了。"
                }
                val momentId = repository.insertMoment(Moment(
                    operatorId = momentOp.id, operatorName = momentOp.name,
                    content = momentContent, createdAt = now
                ))
                // 随机点赞
                val likers = ops.filter { it.id != momentOp.id && it.id != "user" }.shuffled().take((2..4).random())
                likers.forEach { l -> repository.insertLike(MomentLike(momentId = momentId, operatorId = l.id, operatorName = l.name, createdAt = now)) }
                repository.updateLikeCount(momentId, likers.size)
                // 随机评论
                val commenters = ops.filter { it.id != momentOp.id && it.id in participantIds }.shuffled().take((1..2).random())
                commenters.forEach { c ->
                    val comment = AiChat.settlementLine(c.name, c.name == winnerName, winType == "流局")
                        .substringAfter("：")
                    repository.insertComment(MomentComment(momentId = momentId, operatorId = c.id, operatorName = c.name, content = comment, createdAt = now))
                }
                repository.updateCommentCount(momentId, commenters.size)
            }
        }
    }

    fun createMahjongAnchor(content: String) {
        scope.launch {
            for (op in operatorsProvider().shuffled().take(4)) {
                repository.saveAnchor(MemoryAnchor(
                    sessionId = "anchor_${System.currentTimeMillis()}_${op.id}",
                    operatorId = op.id, type = AnchorType.EVENT,
                    content = content, isPrivate = false,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = MemoryPolicy.anchorExpiresAt(settings, AnchorType.EVENT)
                ))
            }
        }
    }

    fun postMahjongMoment(content: String) {
        scope.launch {
            val op = operatorsProvider().randomOrNull() ?: return@launch
            val momentId = repository.insertMoment(com.rhodes.privatechat.shared.model.Moment(operatorId = op.id, operatorName = op.name, content = content, createdAt = System.currentTimeMillis()))
            val allOps = operatorsProvider().filter { it.name != "系统" && it.id != op.id }
            val likers = allOps.shuffled().take((2..5).random())
            likers.forEach { l -> repository.insertLike(com.rhodes.privatechat.shared.model.MomentLike(momentId = momentId, operatorId = l.id, operatorName = l.name, createdAt = System.currentTimeMillis())) }
            repository.updateLikeCount(momentId, likers.size)
            val commenters = allOps.shuffled().take((1..2).random())
            commenters.forEach { c ->
                repository.insertComment(com.rhodes.privatechat.shared.model.MomentComment(momentId = momentId, operatorId = c.id, operatorName = c.name, content = "好局！", createdAt = System.currentTimeMillis()))
            }
            repository.updateCommentCount(momentId, commenters.size)
        }
    }
}
