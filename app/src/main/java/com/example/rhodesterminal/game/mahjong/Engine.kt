package com.example.rhodesterminal.game.mahjong

import android.util.Log

object Engine {
    private const val TAG = "麻将"

    fun deal(game: GameState) {
        Log.d(TAG, "发牌开始：${game.players.joinToString { it.name }}")
        game.wall = Tile.fullWall().apply { shuffle() }
        game.doraIndicators.clear(); game.uraDoraIndicators.clear()
        game.doraIndicators.add(game.wall.removeLast())
        game.uraDoraIndicators.add(game.wall.removeLast())
        Log.d(TAG, "宝牌指示牌：${Tile.tileName(game.doraIndicators.last())}，牌山剩余：${game.wall.size}")
        for (p in game.players) {
            p.hand.clear(); p.discards.clear(); p.melds.clear()
            p.isRiichi = false; p.isFuriten = false; p.isTenpai = false; p.points = 25000
            repeat(13) { p.hand.add(game.wall.removeLast()) }
            p.hand.sortBy { it.ordinalForSort() }
            Log.d(TAG, "${p.name}手牌：${p.hand.joinToString { Tile.tileName(it) }}")
        }
        val dealer = game.dealer()
        dealer.hand.add(game.wall.removeLast())
        dealer.hand.sortBy { it.ordinalForSort() }
        Log.d(TAG, "庄家${dealer.name}摸第14张：手牌${dealer.hand.size}张")
        game.winnerSeat = null; game.drawnIdx = -1
    }

    fun draw(game: GameState) {
        if (game.wall.isEmpty()) return
        game.lastDiscard = null
        val p = game.currentPlayer()
        p.hand.add(game.wall.removeLast())
        p.hand.sortBy { it.ordinalForSort() }
        game.drawnIdx = p.hand.size - 1
        Log.d(TAG, "${p.name}摸牌，手牌${p.hand.size}张，牌山剩余${game.wall.size}")
    }

    fun shanten(hand: List<Tile>): Int {
        val n = hand.size
        if (n != 13 && n != 14) return 99
        val counts = IntArray(34) { 0 }
        hand.forEach { counts[it.ordinalForSort()]++ }
        val memo = HashMap<String, Triple<Int, Int, Boolean>>()
        fun encode(cnt: IntArray): String {
            val sb = StringBuilder(64)
            for (i in 0 until 34) if (cnt[i] > 0) sb.append("${i},${cnt[i]};")
            return sb.toString()
        }
        fun dfs(cnt: IntArray): Triple<Int, Int, Boolean> {
            val k = encode(cnt); memo[k]?.let { return it }
            var i = 0; while (i < 34 && cnt[i] == 0) i++
            if (i == 34) return Triple(0, 0, false)
            var best = Triple(0, 0, false)
            if (cnt[i] >= 3) {
                cnt[i] -= 3; val r = dfs(cnt); cnt[i] += 3
                if (r.first + 1 > best.first || (r.first + 1 == best.first && (r.second > best.second || (r.second == best.second && r.third && !best.third)))) best = Triple(r.first + 1, r.second, r.third)
            }
            if (i < 27 && i % 9 <= 6 && cnt[i + 1] > 0 && cnt[i + 2] > 0) {
                cnt[i]--; cnt[i + 1]--; cnt[i + 2]--; val r = dfs(cnt); cnt[i]++; cnt[i + 1]++; cnt[i + 2]++
                if (r.first + 1 > best.first || (r.first + 1 == best.first && (r.second > best.second || (r.second == best.second && r.third && !best.third)))) best = Triple(r.first + 1, r.second, r.third)
            }
            if (cnt[i] >= 2) {
                cnt[i] -= 2; val r = dfs(cnt); cnt[i] += 2
                if (!r.third && (r.first > best.first || (r.first == best.first && (r.second > best.second || (r.second == best.second && !best.third))))) best = Triple(r.first, r.second, true)
            }
            if (i < 27 && i % 9 <= 7 && cnt[i + 1] > 0) {
                cnt[i]--; cnt[i + 1]--; val r = dfs(cnt); cnt[i]++; cnt[i + 1]++
                if (r.first > best.first || (r.first == best.first && (r.second + 1 > best.second || (r.second + 1 == best.second && r.third && !best.third)))) best = Triple(r.first, r.second + 1, r.third)
            }
            if (i < 27 && i % 9 <= 6 && cnt[i + 2] > 0) {
                cnt[i]--; cnt[i + 2]--; val r = dfs(cnt); cnt[i]++; cnt[i + 2]++
                if (r.first > best.first || (r.first == best.first && (r.second + 1 > best.second || (r.second + 1 == best.second && r.third && !best.third)))) best = Triple(r.first, r.second + 1, r.third)
            }
            if (cnt[i] >= 2) {
                cnt[i] -= 2; val r = dfs(cnt); cnt[i] += 2
                if (r.first > best.first || (r.first == best.first && (r.second + 1 > best.second || (r.second + 1 == best.second && r.third && !best.third)))) best = Triple(r.first, r.second + 1, r.third)
            }
            cnt[i]--; val r = dfs(cnt); cnt[i]++
            if (r.first > best.first || (r.first == best.first && (r.second > best.second || (r.second == best.second && r.third && !best.third)))) best = Triple(r.first, r.second, r.third)
            memo[k] = best; return best
        }
        val (mentsu, partials, hasHead) = dfs(counts)
        var sh = 8 - 2 * mentsu - partials - (if (hasHead) 1 else 0)
        // 七对子向听数（特殊规则：6 - 对数）
        if (n == 14) {
            val pairs = (0 until 34).count { counts[it] >= 2 }
            sh = minOf(sh, 6 - pairs)
        } else if (n == 13) {
            val pairs = (0 until 34).count { counts[it] >= 2 }
            val lonely = (0 until 34).count { counts[it] == 1 }
            sh = minOf(sh, 6 - pairs + (if (lonely > 0) 1 else 0))
        }
        return sh.coerceAtLeast(-1)
    }

    fun isTenpai(hand: List<Tile>): Boolean = hand.size == 14 && shanten(hand) == 0

    /** 检查是否听牌：13张向听0或14张向听-1/0均为听牌 */
    fun isTenpaiState(hand: List<Tile>): Boolean = when (hand.size) {
        13 -> shanten(hand) == 0
        14 -> shanten(hand) <= 0
        else -> false
    }
    fun canRon(hand: List<Tile>, discard: Tile): Boolean {
        val test = hand.toMutableList(); test.add(discard); test.sortBy { it.ordinalForSort() }
        return test.size == 14 && shanten(test) == -1
    }
    fun canTsumo(hand: List<Tile>): Boolean = hand.size == 14 && shanten(hand) == -1

    fun canWin(hand: List<Tile>, melds: List<Meld>, seatWind: Seat, roundWind: Seat, isMenzen: Boolean, isTsumo: Boolean, isRiichi: Boolean = false, isIppatsu: Boolean = false): Boolean {
        if (hand.size != 14 || shanten(hand) != -1) return false
        val yaku = checkYakuLocal(hand, melds, seatWind, roundWind, isMenzen, isTsumo, isRiichi, isIppatsu)
        return yaku.isValid && yaku.han >= 1
    }

    fun canPon(hand: List<Tile>, discard: Tile): Boolean = hand.count { it == discard } >= 2
    fun canKan(hand: List<Tile>, discard: Tile): Boolean = hand.count { it == discard } >= 3
    /** 暗杠检测：返回手牌中有4张的牌 */
    fun canAnkan(hand: List<Tile>): List<Tile> = hand.distinct().filter { t -> hand.count { it == t } == 4 }

    fun canChi(hand: List<Tile>, discard: Tile): List<List<Tile>>? {
        if (discard.isHonor()) return null
        val s = discard.suit; val n = discard.number
        val combos = mutableListOf<List<Tile>>()
        if (n - 2 >= 1) { val t = listOf(Tile(s, n - 2), Tile(s, n - 1)); if (t.all { hand.count { h -> h == it } >= 1 }) combos.add(t) }
        if (n - 1 >= 1 && n + 1 <= 9) { val t = listOf(Tile(s, n - 1), Tile(s, n + 1)); if (t.all { hand.count { h -> h == it } >= 1 }) combos.add(t) }
        if (n + 2 <= 9) { val t = listOf(Tile(s, n + 1), Tile(s, n + 2)); if (t.all { hand.count { h -> h == it } >= 1 }) combos.add(t) }
        return if (combos.isEmpty()) null else combos
    }

    // 振听检查：如果玩家的弃牌中包含任何能完成手牌的牌，则为振听
    fun isFuriten(player: PlayerState): Boolean {
        return player.discards.any { d -> canRon(player.hand, d) }
    }

    fun dangerLevel(tile: Tile, opponent: PlayerState): Int {
        if (opponent.discards.any { it == tile }) return 0
        if (tile.isHonor() || tile.isTerminal()) return 10
        return 30
    }

    fun safeTiles(opponents: List<PlayerState>): Set<Tile> =
        opponents.flatMap { it.discards }.toSet()

    fun calculatePoints(han: Int, isDealer: Boolean): Int = when {
        han >= 5 -> if (isDealer) 12000 else 8000
        han == 4 -> if (isDealer) 8000 else 4000
        han == 3 -> if (isDealer) 4000 else 2000
        han == 2 -> if (isDealer) 2000 else 1000
        han == 1 -> if (isDealer) 1000 else 500
        else -> 0
    }

    data class YakuResult(val yakus: List<String>, val han: Int, val isValid: Boolean)

    fun checkYakuLocal(hand: List<Tile>, melds: List<Meld>, seatWind: Seat, roundWind: Seat, isMenzen: Boolean, isTsumo: Boolean, isRiichi: Boolean = false, isIppatsu: Boolean = false): YakuResult {
        val yakus = mutableListOf<String>(); var han = 0
        val allTiles = hand.toMutableList().apply { melds.forEach { addAll(it.tiles) } }
        if (isRiichi) { yakus.add("立直"); han += 1 }
        if (isRiichi && isIppatsu) { yakus.add("一发"); han += 1 }
        if (allTiles.none { it.isYaochu() }) { yakus.add("断幺九"); han += 1 }
        val head = findHead(hand)
        if (head != null && head.first == head.second) {
            if (head.first == Tile(Suit.WIND, seatToNum(seatWind))) { yakus.add("役牌·自风"); han += 1 }
            if (head.first == Tile(Suit.WIND, seatToNum(roundWind))) { yakus.add("役牌·场风"); han += 1 }
            listOf(1, 2, 3).forEach { if (head.first == Tile(Suit.DRAGON, it)) { yakus.add("役牌·三元"); han += 1 } }
        }
        if (melds.isEmpty() && hand.size == 14) {
            val groups = hand.groupBy { it.ordinalForSort() }
            if (groups.size == 7 && groups.all { it.value.size == 2 }) { yakus.add("七对子"); han = 2 }
        }
        if (isMenzen && isTsumo) { yakus.add("门前清自摸和"); han += 1 }
        if (head != null && head.first == head.second) {
            val allMeldTriplets = melds.all { it.type == MeldType.PON || it.type == MeldType.KAN || it.type == MeldType.ANKAN }
            if (allMeldTriplets) {
                val raw = hand.toMutableList()
                raw.remove(head.first); raw.remove(head.second)
                val cnt = IntArray(34) { 0 }
                raw.forEach { cnt[it.ordinalForSort()]++ }
                for (i in 0 until 34) cnt[i] %= 3
                if (cnt.all { it == 0 } && raw.size >= 3) { yakus.add("对对和"); han += 2 }
            }
        }
        val suits = allTiles.map { it.suit }.toSet()
        val numSuits = allTiles.map { it.suit }.filter { it != Suit.WIND && it != Suit.DRAGON }.toSet()
        if (suits.size == 2 && numSuits.size == 1 && suits.any { it == Suit.WIND || it == Suit.DRAGON }) { yakus.add("混一色"); han += 2 }
        return YakuResult(yakus, han, han >= 1)
    }

    private fun seatToNum(s: Seat) = when (s) { Seat.EAST -> 1; Seat.SOUTH -> 2; Seat.WEST -> 3; Seat.NORTH -> 4 }
    private fun findHead(hand: List<Tile>): Pair<Tile, Tile>? {
        val sorted = hand.sortedBy { it.ordinalForSort() }
        for (i in 0 until sorted.size - 1) if (sorted[i] == sorted[i + 1]) return sorted[i] to sorted[i + 1]
        return null
    }

    fun doraTile(d: Tile): Tile = when (d.suit) {
        Suit.MAN, Suit.PIN, Suit.SOU -> Tile(d.suit, if (d.number == 9) 1 else d.number + 1)
        Suit.WIND -> Tile(Suit.WIND, when (d.number) { 4 -> 1; else -> d.number + 1 })
        Suit.DRAGON -> Tile(Suit.DRAGON, when (d.number) { 3 -> 1; else -> d.number + 1 })
    }

    fun settle(game: GameState): SettlementResult {
        val human = game.humanPlayer()
        val ws = game.winnerSeat
        if (ws != null) {
            val winner = game.players.find { it.seat == ws }
            if (winner != null) {
                val isRon = game.lastDiscard != null && game.lastDiscardSeat != null
                val testHand = winner.hand.toList()
                if (testHand.size == 14) {
                    val yakus = checkYakuLocal(testHand, winner.melds, winner.seat, game.roundWind, winner.melds.isEmpty(), !isRon, winner.isRiichi, game.ippatsuPlayerIdx == game.players.indexOf(winner))
                    if (yakus.isValid) {
                        val isDealer = game.isDealer(winner.seat)
                        var han = yakus.han
                        han += testHand.count { t -> game.doraIndicators.any { d -> t == doraTile(d) } }
                        // 立直和牌者加算裏ドラ
                        if (winner.isRiichi) {
                            han += testHand.count { t -> game.uraDoraIndicators.any { d -> t == doraTile(d) } }
                        }
                        val pts = calculatePoints(han.coerceAtLeast(1), isDealer)
                        val riichiBonus = game.riichiSticks * 1000
                        if (isRon && game.lastDiscardSeat != null) {
                            val loser = game.players.find { it.seat == game.lastDiscardSeat }
                            if (loser != null) { loser.points -= pts; winner.points += pts + riichiBonus }
                        } else if (!isRon) {
                            val opps = game.players.filter { it != winner }
                            opps.mapIndexed { i, opp ->
                                val share = pts / opps.size
                                val remainder = if (i == 0) pts - share * (opps.size - 1) else share
                                opp.points -= remainder
                            }
                            winner.points += pts + riichiBonus
                        }
                    }
                }
            }
        }
        val results = game.players.map { p ->
            val yakus = checkYakuLocal(p.hand, p.melds, p.seat, game.roundWind, p.melds.isEmpty(), false, p.isRiichi)
            val dora = p.hand.count { t -> game.doraIndicators.any { d -> t == doraTile(d) } }
            val han = yakus.han + dora
            val isDealer = game.isDealer(p.seat)
            val pts = if (yakus.isValid) calculatePoints(han.coerceAtLeast(1), isDealer) else 0
            val netGain = ((p.points - 25000) * 100) / 1000
            PlayerResult(p.name, p.points, netGain, 0, yakus.yakus, han)
        }.sortedByDescending { it.finalPoints }
        return SettlementResult(
            rankings = results.mapIndexed { i, r -> r.copy(rank = i + 1) },
            userNetGain = results.find { it.name == human?.name }?.netGain ?: 0
        )
    }
}
