package com.rhodes.privatechat.game.mahjong

import kotlin.random.Random

object AiChat {
    data class TableTalkContext(
        val event: String,
        val player: PlayerState,
        val tile: Tile? = null,
        val roundLabel: String = "",
        val shanten: Int = 99,
        val wallLeft: Int = 0,
        val rank: Int = 0,
        val netGain: Int = 0,
        val winnerName: String = ""
    )

    private val eventLines = mapOf(
        "discard" to listOf(
            "这张先不要了。","先打这个，你们别太紧张。","我换个方向做牌。","这张应该没人要吧？"
        ),
        "pon" to listOf("碰！","这个我要了","等的就是这张","终于来了！"),
        "chi" to listOf("吃！","借用了~","这个组合不错","刚好能用"),
        "kan" to listOf("杠！","又凑齐了","杠上开花！","意外收获"),
        "ron" to listOf("和了！","赢了！","就等这一刻","不好意思了~"),
        "tsumo" to listOf("自摸！","没想到自摸了","好运气来了","这张等好久了"),
        "exhaustive" to listOf("流局了…","又流局了","大家都太稳了","白忙一场")
    )

    private val settlementWinLines = listOf(
        "运气不错，下次继续！","小赢一把，开心~","今天手感好","承让承让！","嘿嘿，赢了！"
    )
    private val settlementLoseLines = listOf(
        "今天手气不好…","下次一定赢回来！","呜，输了","只是运气问题","再来一局！"
    )
    private val settlementDrawLines = listOf(
        "流局了，大家都很稳","下次再分胜负","打了个寂寞","至少没输钱"
    )

    private val phaseLines = mapOf(
        "opening" to listOf(
            "开局开局，今天谁先交学费？",
            "先说好，输赢都算龙门币，别赖账。",
            "我今天手感不错，你们小心点。"
        ),
        "middle" to listOf(
            "你们这个牌河，看着都不像小牌啊。",
            "中盘了，谁还在偷偷做大牌？",
            "别都这么安静，我有点慌了。"
        ),
        "late" to listOf(
            "最后几巡了，谁放铳谁请宵夜。",
            "终盘别乱来，我可不想接炮。",
            "现在每一张都像陷阱，真难受。"
        )
    )

    private val tableTalkLines = mapOf(
        "chi" to listOf(
            "吃，这张刚好顺手。",
            "这张我拿走了，别后悔啊。",
            "不好意思，给我续上了。",
            "这个形状舒服多了。"
        ),
        "pon" to listOf(
            "碰！等的就是这张。",
            "这个我要了，你们继续。",
            "嘿，终于让我碰到了。",
            "别看我，我只是刚好需要。"
        ),
        "kan" to listOf(
            "杠！补张给点面子。",
            "我杠这一口怎么样，怕不怕？",
            "杠了啊，我说我要和了你们信吗？",
            "补张好牌，牌山别演我。"
        ),
        "ron" to listOf(
            "和了，不好意思啊。",
            "就等这张，谢谢送上门。",
            "这张我可等很久了。",
            "别怪我，是你自己打出来的。"
        ),
        "tsumo" to listOf(
            "自摸，今天牌山站我这边。",
            "这张终于来了，舒服。",
            "摸到了，龙门币我收下了。",
            "哎呀，自摸了，承让。"
        ),
        "tenpai" to listOf(
            "你们打牌突然变慢了，是不是怕我？",
            "别乱打哦，我可快了。",
            "我这手牌，已经有点意思了。",
            "再给我一张，应该就差不多。"
        ),
        "near_win" to listOf(
            "差一点，就差那么一点。",
            "再让我进一张，你们就难办了。",
            "这牌有戏，先不告诉你们。"
        )
    )

    private val styleSettlementLines = mapOf(
        "win_aggressive" to listOf("今天冲得值，龙门币我收下了。", "牌山给机会就要压上去，这就是回报。", "下次也别让我这么舒服进攻。"),
        "win_defensive" to listOf("稳住就会有机会，今天运气也不错。", "没有乱冒险，结果还算理想。", "赢得不多，但每一步都很安心。"),
        "win_random" to listOf("我也不知道怎么赢的，但赢了就是赢了。", "这局牌山好像偷偷帮我。", "随便打打居然第一，真奇妙。"),
        "lose_aggressive" to listOf("可惜，就差一点冲过去。", "这把是我太贪，下次继续压。", "输是输了，但气势不能输。"),
        "lose_defensive" to listOf("明明已经很小心了，还是差一点。", "这把先记下，下次不放这种危险张。", "至少没有崩盘，慢慢追回来。"),
        "lose_random" to listOf("欸？怎么龙门币少了？", "我刚才是不是打错了三次？", "没关系，下一局乱拳打回来。")
    )

    private val helpReplies = mapOf(
        "严谨" to listOf(
            "打{1}，牌型更顺","{1}安全，牌河已见2张","拆{1}，留{2}好型","先稳一点，打{1}"
        ),
        "激进" to listOf(
            "冲{1}，相信你的运气","打{1}，听牌就在下一张","别怂，打{1}","富贵险中求，打{1}"
        ),
        "随机" to listOf(
            "打{1}……大概吧","我觉得{1}不错，也可能不对","要不试试{1}？","我选{1}，错了别怪我",
            "嗯…打{1}？我也不知道","你确定问我？好吧打{1}"
        ),
        "稳健" to listOf(
            "安全第一，打{1}","{1}最安全，没人要","别冒险，打{1}","打{1}至少不会放铳"
        )
    )

    fun line(event: String, player: String, tile: String = ""): String {
        val lines = eventLines[event] ?: eventLines["discard"]!!
        return lines.random().replace("{t}", tile)
    }

    fun tableTalk(context: TableTalkContext): String {
        val lines = tableTalkLines[context.event] ?: return line(context.event, context.player.name, context.tile?.let { Tile.tileName(it) }.orEmpty())
        return fill(lines.random(), context)
    }

    fun phaseTalk(phase: String, game: GameState): String {
        val speaker = game.assistantPlayer() ?: game.players.filter { !it.isHuman }.randomOrNull() ?: game.players.first()
        val line = phaseLines[phase]?.random() ?: phaseLines.values.random().random()
        return fill(line, TableTalkContext(event = phase, player = speaker, roundLabel = game.roundLabel(), wallLeft = game.wall.size))
    }

    fun personalityLine(player: PlayerState): String {
        val style = when {
            player.specialTraits.any { it.contains("严谨") || it.contains("认真") } -> "严谨"
            player.specialTraits.any { it.contains("激进") || it.contains("冲") } -> "激进"
            player.specialTraits.any { it.contains("随机") || it.contains("疯") || it.contains("捣乱") } -> "随机"
            player.attack > 0.6f -> "激进"
            player.defense > 0.7f -> "稳健"
            else -> listOf("严谨","激进","随机","稳健")[Random.nextInt(4)]
        }
        val lines = helpReplies[style] ?: helpReplies["随机"]!!
        val tpl = lines.random()
        val t1 = "这张牌"
        return tpl.replace("{1}", t1).replace("{2}", t1)
    }

    fun settlementLine(name: String, isWinner: Boolean, isDraw: Boolean): String = when {
        isDraw -> "${name}：${settlementDrawLines.random()}"
        isWinner -> "${name}：${settlementWinLines.random()}"
        else -> "${name}：${settlementLoseLines.random()}"
    }

    fun settlementLine(player: PlayerState?, name: String, isWinner: Boolean, isDraw: Boolean, rank: Int, netGain: Int): String {
        if (isDraw) return "${name}：${settlementDrawLines.random()}"
        val style = player?.let { styleOf(it) } ?: "random"
        val key = if (isWinner || rank == 1 || netGain > 0) "win_$style" else "lose_$style"
        val fallback = if (isWinner || netGain >= 0) settlementWinLines else settlementLoseLines
        val line = (styleSettlementLines[key] ?: fallback).random()
        return "${name}：$line"
    }

    fun assistantSay(): String = listOf(
        "要不要考虑打这张？","感觉留万字比较好","这手牌有点难处理",
        "如果我是你就打左边那张","注意防守哦","随便打打，开心就好",
        "听牌了，稳一点","这张危险，别打","对手好像在做大牌"
    ).random()

    fun help(player: PlayerState, hand: List<Tile>, shanten: Int): String {
        val style = helpStyleOf(player)
        val lines = helpReplies[style] ?: helpReplies["随机"]!!
        val tpl = lines.random()
        val t1 = AiDiscard.recommendedDiscard(hand)?.let(Tile::tileName) ?: "这张"
        val t2 = hand.firstOrNull { Tile.tileName(it) != t1 }?.let(Tile::tileName) ?: t1
        return tpl.replace("{1}", t1).replace("{2}", t2)
    }

    fun chatEmoji(event: String): String = when (event) {
        "discard" -> "\uD83C\uDC04"; "pon","chi","kan" -> "\uD83D\uDCA5"
        "ron","tsumo" -> "\uD83C\uDF89"; "exhaustive" -> "\uD83C\uDF00"; else -> "\uD83D\uDCAC"
    }

    fun msgPrefix(sender: String, isAssistant: Boolean, isSystem: Boolean): Pair<String, String> {
        return if (isSystem) Pair("\uD83D\uDCE2","#666666")
        else if (isAssistant) Pair("⭐","#FF8F00")
        else if (sender == "博士" || sender.contains("你")) Pair("\uD83D\uDC64","#4CAF50")
        else Pair("","#FFFFFF")
    }

    private fun fill(template: String, context: TableTalkContext): String {
        val tile = context.tile?.let { Tile.tileName(it) }.orEmpty()
        return template
            .replace("{name}", context.player.name)
            .replace("{tile}", tile.ifBlank { "这张牌" })
            .replace("{round}", context.roundLabel.ifBlank { "这一局" })
            .replace("{wall}", context.wallLeft.toString())
            .replace("{rank}", context.rank.toString())
            .replace("{gain}", context.netGain.toString())
            .replace("{winner}", context.winnerName)
    }

    private fun styleOf(player: PlayerState): String = when {
        player.specialTraits.any { it.contains("激进") || it.contains("冲") } || player.attack > 0.65f -> "aggressive"
        player.specialTraits.any { it.contains("稳健") || it.contains("谨慎") } || player.defense > 0.65f -> "defensive"
        player.specialTraits.any { it.contains("随机") || it.contains("疯") || it.contains("捣乱") } -> "random"
        else -> "random"
    }

    private fun helpStyleOf(player: PlayerState): String = when (styleOf(player)) {
        "aggressive" -> "激进"
        "defensive" -> "稳健"
        else -> if (player.specialTraits.any { it.contains("严谨") || it.contains("认真") }) "严谨" else "随机"
    }
}
