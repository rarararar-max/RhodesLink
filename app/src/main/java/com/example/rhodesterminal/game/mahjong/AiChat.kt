package com.example.rhodesterminal.game.mahjong

import kotlin.random.Random

object AiChat {
    private val eventLines = mapOf(
        "discard" to listOf(
            "打出了{t}","想了想，扔出{t}","毫不犹豫打出{t}","笑着打出{t}"
        ),
        "draw" to listOf(
            "摸了一张牌","新牌到手","看了一眼摸到的牌","手指摩挲着新牌"
        ),
        "pon" to listOf("碰！","这个我要了","等的就是这张"),
        "chi" to listOf("吃！","借用了~","这个组合不错"),
        "kan" to listOf("杠！","又凑齐了","杠上开花！"),
        "riichi" to listOf("立直！","听牌了，立直","推出一千点棒"),
        "ron" to listOf("和了！","赢了！","就等这一刻"),
        "tsumo" to listOf("自摸！","没想到自摸了","好运气"),
        "exhaustive" to listOf("流局了…","又流局了","大家都太稳了")
    )

    private val helpReplies = mapOf(
        "严谨" to listOf(
            "打{1}，断幺九保留","{1}安全，牌河已见2张","拆{1}，留{2}好型","防立直，打{1}安全"
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

    fun assistantSay(): String = listOf(
        "要不要考虑打这张？","感觉留万字比较好","这手牌有点难处理",
        "如果我是你就打左边那张","注意防守哦","随便打打，开心就好"
    ).random()

    fun help(player: PlayerState, hand: List<Tile>, shanten: Int): String {
        val r = Random
        val style = when {
            player.specialTraits.any { it.contains("严谨") || it.contains("认真") } -> "严谨"
            player.specialTraits.any { it.contains("激进") || it.contains("冲") } -> "激进"
            player.specialTraits.any { it.contains("随机") || it.contains("疯") || it.contains("捣乱") } -> "随机"
            player.attack > 0.6f -> "激进"
            player.defense > 0.7f -> "稳健"
            else -> listOf("严谨","激进","随机","稳健")[r.nextInt(4)]
        }
        val lines = helpReplies[style] ?: helpReplies["随机"]!!
        val tpl = lines.random()
        val tiles = hand.map { Tile.tileName(it) }.distinct()
        val t1 = if (tiles.isNotEmpty()) tiles[r.nextInt(tiles.size)] else "这张"
        val t2 = if (tiles.size > 1) tiles.filter { it != t1 }[r.nextInt(tiles.size.coerceAtMost(2))] else t1
        return tpl.replace("{1}", t1).replace("{2}", t2)
    }

    fun chatEmoji(event: String): String = when (event) {
        "discard" -> "🀄"; "pon","chi","kan" -> "💥"; "riichi" -> "🎋"
        "ron","tsumo" -> "🎉"; "exhaustive" -> "🌀"; else -> "💬"
    }

    fun msgPrefix(sender: String, isAssistant: Boolean, isSystem: Boolean): Pair<String, String> {
        return if (isSystem) Pair("📢","#666666")
        else if (isAssistant) Pair("⭐","#FF8F00")
        else if (sender == "博士" || sender.contains("你")) Pair("👤","#4CAF50")
        else Pair("","#FFFFFF")
    }
}
