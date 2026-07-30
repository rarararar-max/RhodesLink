package com.rhodes.privatechat.game.mahjong

import kotlin.random.Random

object AiDiscard {
    data class DiscardOption(val tile: Tile, val shantenAfter: Int, val dangerScore: Int, val isSafe: Boolean)

    fun decideDiscard(player: PlayerState, gameState: GameState): Tile {
        val hand = player.hand.toList()
        if (hand.isEmpty()) return Tile(Suit.WIND, 1)
        val options = hand.map { tile ->
            val remaining = hand.toMutableList().also { it.remove(tile) }
            val sh = Engine.shanten(remaining)
            val danger = gameState.players.filter { it != player }.maxOfOrNull { Engine.dangerLevel(tile, it) } ?: 0
            DiscardOption(tile, sh, danger, danger == 0)
        }
        val minShanten = options.minOf { it.shantenAfter }
        val bestOptions = options.filter { it.shantenAfter == minShanten }
        val effA = effectiveAttack(player, gameState)
        val effD = effectiveDefense(player, gameState)

        val scored = bestOptions.map { opt ->
            var score = 0f
            score += effA * (5 - opt.shantenAfter.coerceAtMost(5)) * 20f
            if (opt.isSafe) score += effD * 50f
            if (opt.shantenAfter < Engine.shanten(hand) && !opt.isSafe) score -= effD * 30f
            if (player.specialTraits.any { it.contains("认真") || it.contains("严谨") }) {
                if (opt.isSafe) score += 15f
            }
            if (player.specialTraits.any { it.contains("激进") || it.contains("冲") }) {
                if (!opt.isSafe && opt.shantenAfter <= minShanten) score += 20f
            }
            Pair(opt, score)
        }.sortedByDescending { it.second }

        if (player.specialTraits.any { it.contains("随机") || it.contains("疯狂") }) {
            if (Random.nextFloat() < 0.3f) return bestOptions.random().tile
        }
        if (player.specialTraits.any { it.contains("紧张") || it.contains("打错") }) {
            if (Random.nextFloat() < 0.15f) return hand.random()
        }
        val chosen = scored.firstOrNull()?.first?.tile ?: bestOptions.first().tile
        return chosen
    }

    fun recommendedDiscard(hand: List<Tile>): Tile? {
        if (hand.isEmpty()) return null
        return hand.distinct().minWithOrNull(
            compareBy<Tile> { tile -> Engine.shanten(hand.toMutableList().also { it.remove(tile) }) }
                .thenBy { it.ordinalForSort() }
        )
    }

    fun effectiveAttack(player: PlayerState, gameState: GameState): Float {
        var a = player.attack
        if (player.isTenpai || Engine.isTenpaiState(player.hand, player.melds)) a += 0.2f
        if (player.points > 40000) a += 0.1f
        if (player.specialTraits.any { it.contains("激进") || it.contains("冲") }) a += 0.15f
        if (player.specialTraits.any { it.contains("稳健") || it.contains("谨慎") }) a -= 0.1f
        return a.coerceIn(0f, 1f)
    }

    fun effectiveDefense(player: PlayerState, gameState: GameState): Float {
        var d = player.defense
        val dealer = gameState.currentPlayerOrNull()?.takeIf { gameState.isDealer(it.seat) }
            ?: gameState.players.getOrNull(gameState.dealerIdx)
        if (dealer != null && Engine.isTenpaiState(dealer.hand, dealer.melds)) d += 0.25f
        if (player.points < 10000) d += 0.15f
        if (player.points < 5000) d += 0.3f
        if (player.specialTraits.any { it.contains("稳健") || it.contains("谨慎") }) d += 0.15f
        if (player.specialTraits.any { it.contains("激进") || it.contains("冲") }) d -= 0.1f
        return d.coerceIn(0f, 1f)
    }

    fun decideMeld(player: PlayerState, meldType: MeldType, gameState: GameState): Boolean {
        if (player.hand.isEmpty()) return false
        val pref = player.meldPref
        val baseChance = when (meldType) {
            MeldType.KAN -> when (pref) { "high" -> 0.8f; "medium" -> 0.5f; "low" -> 0.2f; else -> 0.5f }
            MeldType.PON -> when (pref) { "high" -> 0.9f; "medium" -> 0.6f; "low" -> 0.2f; else -> Random.nextFloat() }
            MeldType.CHI -> when (pref) { "high" -> 0.7f; "medium" -> 0.4f; "low" -> 0.1f; else -> Random.nextFloat() }
            else -> 0f
        }
        if (effectiveDefense(player, gameState) > 0.7f) return false
        if (Engine.isTenpaiState(player.hand, player.melds)) return false
        if (player.specialTraits.any { it.contains("激进") || it.contains("冲") }) return Random.nextFloat() < (baseChance + 0.2f)
        return Random.nextFloat() < baseChance
    }
}
