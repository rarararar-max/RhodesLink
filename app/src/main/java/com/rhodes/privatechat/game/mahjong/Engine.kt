package com.rhodes.privatechat.game.mahjong

import android.util.Log

object Engine {
    private const val TAG = "麻将"
    private val BASIC_WIN_PATTERNS = Tile.allTiles()

    fun deal(game: GameState) {
        Log.d(TAG, "发牌开始：${game.players.joinToString { it.name }}")
        game.wall = Tile.fullWall().apply { shuffle() }
        game.doraIndicators.clear(); game.uraDoraIndicators.clear()
        game.riichiSticks = 0
        Log.d(TAG, "基础麻将发牌，牌山剩余：${game.wall.size}")
        for (p in game.players) {
            p.hand.clear(); p.discards.clear(); p.melds.clear()
            p.isRiichi = false; p.isFuriten = false; p.isTenpai = false
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
        // 合法手牌大小：14(无副露), 11(1副露), 8(2副露), 5(3副露), 2(4副露)
        if (n < 2 || n > 14 || n % 3 == 0) return 99
        val targetMentsu = (n - 2) / 3  // 14→4, 11→3, 8→2, 5→1, 2→0
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
        var sh = (targetMentsu * 2) - 2 * mentsu - partials - (if (hasHead) 1 else 0)
        // 七对子只在13/14张时计算
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

    fun isTenpaiState(hand: List<Tile>): Boolean = isBasicTenpai(hand)
    fun canRon(hand: List<Tile>, discard: Tile, melds: List<Meld> = emptyList()): Boolean {
        val test = hand.toMutableList(); test.add(discard); test.sortBy { it.ordinalForSort() }
        return isBasicWin(test, melds)
    }
    fun canTsumo(hand: List<Tile>, melds: List<Meld>): Boolean {
        return isBasicWin(hand, melds)
    }

    fun canWin(hand: List<Tile>, melds: List<Meld>, seatWind: Seat, roundWind: Seat, isMenzen: Boolean, isTsumo: Boolean, isRiichi: Boolean = false, isIppatsu: Boolean = false): Boolean {
        return isBasicWin(hand, melds)
    }

    fun isBasicTenpai(hand: List<Tile>, melds: List<Meld> = emptyList()): Boolean {
        val totalMeldTiles = melds.sumOf { it.tiles.size }
        val needClosedTiles = 14 - totalMeldTiles
        if (hand.size != needClosedTiles - 1 && hand.size != needClosedTiles) return false
        if (hand.size == needClosedTiles && isBasicWin(hand, melds)) return true
        return BASIC_WIN_PATTERNS.any { t -> isBasicWin(hand + t, melds) }
    }

    fun isBasicWin(hand: List<Tile>, melds: List<Meld> = emptyList()): Boolean {
        val totalMeldTiles = melds.sumOf { it.tiles.size }
        if (hand.size + totalMeldTiles != 14) return false
        if (melds.isEmpty() && isSevenPairs(hand)) return true
        val neededGroups = 4 - melds.size
        if (neededGroups < 0) return false
        val counts = IntArray(34)
        hand.forEach { counts[it.ordinalForSort()]++ }
        for (i in 0 until 34) {
            if (counts[i] >= 2) {
                counts[i] -= 2
                if (canFormGroups(counts, neededGroups)) {
                    counts[i] += 2
                    return true
                }
                counts[i] += 2
            }
        }
        return false
    }

    private fun isSevenPairs(hand: List<Tile>): Boolean {
        if (hand.size != 14) return false
        val groups = hand.groupBy { it.ordinalForSort() }
        return groups.size == 7 && groups.all { it.value.size == 2 }
    }

    private fun canFormGroups(counts: IntArray, groupsLeft: Int): Boolean {
        if (groupsLeft == 0) return counts.all { it == 0 }
        val i = counts.indexOfFirst { it > 0 }
        if (i < 0) return groupsLeft == 0
        if (counts[i] >= 3) {
            counts[i] -= 3
            if (canFormGroups(counts, groupsLeft - 1)) {
                counts[i] += 3
                return true
            }
            counts[i] += 3
        }
        if (i < 27 && i % 9 <= 6 && counts[i + 1] > 0 && counts[i + 2] > 0) {
            counts[i]--; counts[i + 1]--; counts[i + 2]--
            if (canFormGroups(counts, groupsLeft - 1)) {
                counts[i]++; counts[i + 1]++; counts[i + 2]++
                return true
            }
            counts[i]++; counts[i + 1]++; counts[i + 2]++
        }
        return false
    }

    fun canPon(hand: List<Tile>, discard: Tile): Boolean = hand.count { it == discard } >= 2
    fun canKan(hand: List<Tile>, discard: Tile): Boolean = hand.count { it == discard } >= 3
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

    fun isFuriten(player: PlayerState): Boolean {
        return player.discards.any { d -> canRon(player.hand, d, player.melds) }
    }

    fun dangerLevel(tile: Tile, opponent: PlayerState): Int {
        if (opponent.discards.any { it == tile }) return 0
        if (tile.isHonor() || tile.isTerminal()) return 10
        return 30
    }

    fun safeTiles(opponents: List<PlayerState>): Set<Tile> =
        opponents.flatMap { it.discards }.toSet()

    fun calculatePoints(han: Int, isDealer: Boolean): Int = when {
        han >= 13 -> if (isDealer) 48000 else 32000
        han >= 11 -> if (isDealer) 36000 else 24000
        han >= 8 -> if (isDealer) 24000 else 16000
        han >= 6 -> if (isDealer) 18000 else 12000
        han == 5 -> if (isDealer) 12000 else 8000
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
        if (!isBasicWin(hand, melds)) return YakuResult(emptyList(), 0, false)

        yakus.add(if (isTsumo) "自摸" else "平胡"); han += 1

        if (isSevenPairs(hand) && melds.isEmpty()) { yakus.add("七对"); han += 2 }

        if (isAllTripletsBasic(hand, melds)) { yakus.add("对对胡"); han += 2 }

        val suits = allTiles.map { it.suit }.toSet()
        val numSuits = allTiles.map { it.suit }.filter { it != Suit.WIND && it != Suit.DRAGON }.toSet()

        if (numSuits.size == 1 && allTiles.none { it.isHonor() }) { yakus.add("清一色"); han += 4 }
        else if (numSuits.size == 1 && allTiles.any { it.isHonor() }) { yakus.add("混一色"); han += 2 }

        return YakuResult(yakus, han, han >= 1)
    }

    private fun isAllTripletsBasic(hand: List<Tile>, melds: List<Meld>): Boolean {
        if (melds.any { it.type == MeldType.CHI }) return false
        val counts = IntArray(34)
        hand.forEach { counts[it.ordinalForSort()]++ }
        var pairFound = false
        for (c in counts) {
            when (c) {
                0, 3 -> Unit
                2 -> if (!pairFound) pairFound = true else return false
                else -> return false
            }
        }
        return pairFound
    }

    private fun extractSequences(hand: List<Tile>): List<List<Tile>> {
        val sorted = hand.sortedBy { it.ordinalForSort() }
        val sequences = mutableListOf<List<Tile>>()
        val used = BooleanArray(sorted.size)
        for (i in sorted.indices) {
            if (used[i] || sorted[i].isHonor()) continue
            for (j in i + 1 until sorted.size) {
                if (used[j] || sorted[j].suit != sorted[i].suit || sorted[j].number != sorted[i].number + 1) continue
                for (k in j + 1 until sorted.size) {
                    if (used[k] || sorted[k].suit != sorted[i].suit || sorted[k].number != sorted[i].number + 2) continue
                    sequences.add(listOf(sorted[i], sorted[j], sorted[k]))
                    used[i] = true; used[j] = true; used[k] = true
                    break
                }
                break
            }
        }
        return sequences
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
        var winType = "流局"
        var winnerName = ""
        var loserName = ""
        var winningYakus: List<String> = emptyList()
        var winningHan = 0

        if (ws != null) {
            val winner = game.players.find { it.seat == ws }
            if (winner != null) {
                val isRon = game.lastDiscard != null && game.lastDiscardSeat != null
                winType = if (isRon) "点炮胡" else "自摸"
                winnerName = winner.name
                val testHand = winner.hand.toList()
                val totalMeldTiles = winner.melds.sumOf { it.tiles.size }
                if (testHand.size + totalMeldTiles == 14) {
                    val yakus = checkYakuLocal(testHand, winner.melds, winner.seat, game.roundWind, winner.melds.isEmpty(), !isRon, winner.isRiichi, game.ippatsuPlayerIdx == game.players.indexOf(winner))
                    if (yakus.isValid) {
                        val isDealer = game.isDealer(winner.seat)
                        var han = yakus.han
                        winningYakus = yakus.yakus
                        winningHan = han
                        val pts = calculatePoints(han.coerceAtLeast(1), isDealer)
                        if (isRon && game.lastDiscardSeat != null) {
                            val loser = game.players.find { it.seat == game.lastDiscardSeat }
                            if (loser != null) { loserName = loser.name; loser.points -= pts; winner.points += pts }
                        } else if (!isRon) {
                            val opps = game.players.filter { it != winner }
                            opps.mapIndexed { i, opp ->
                                val share = pts / opps.size
                                val remainder = if (i == 0) pts - share * (opps.size - 1) else share
                                opp.points -= remainder
                            }
                            winner.points += pts
                        }
                    }
                }
            }
        } else {
            val tenpais = game.players.filter { isTenpaiState(it.hand) }
            val notens = game.players.filter { !isTenpaiState(it.hand) }
            if (tenpais.isNotEmpty() && notens.isNotEmpty()) {
                val penalty = 3000 / notens.size
                notens.forEach { it.points -= penalty }
                tenpais.forEach { it.points += 3000 / tenpais.size }
            }
        }

        val rawResults = game.players.map { p ->
            val yakus = checkYakuLocal(p.hand, p.melds, p.seat, game.roundWind, p.melds.isEmpty(), false, p.isRiichi)
            val netGain = (p.points - 25000) / 10
            val displayYakus = if (p.name == winnerName && winningYakus.isNotEmpty()) winningYakus else yakus.yakus
            val displayHan = if (p.name == winnerName && winningHan > 0) winningHan else yakus.han
            PlayerResult(p.opId, p.name, p.points, netGain, 0, displayYakus, displayHan)
        }.sortedByDescending { it.finalPoints }
        val results = rawResults.mapIndexed { i, r -> r.copy(rank = i + 1) }
        val humanResult = results.find { it.name == human?.name }
        val cumulative = game.players.associate { it.name to it.points }
        val summary = when {
            winnerName.isBlank() -> {
                val tenpaiNames = game.players.filter { isTenpaiState(it.hand) }.map { it.name }
                if (tenpaiNames.isNotEmpty()) "流局，${tenpaiNames.joinToString("、")}听牌。" else "流局，无人听牌。"
            }
            loserName.isNotBlank() -> "${winnerName}${winType}（${winningYakus.joinToString("·")}），${loserName}点炮。"
            else -> "${winnerName}${winType}（${winningYakus.joinToString("·")}），自摸拿下！"
        }
        return SettlementResult(
            rankings = results,
            userNetGain = humanResult?.netGain ?: 0,
            winType = winType,
            winnerName = winnerName,
            loserName = loserName,
            summary = summary,
            participants = game.players.map { it.name },
            chatLog = game.chatLog.toList(),
            assistantName = game.assistantPlayer()?.name ?: "",
            currentRound = game.round,
            maxRounds = game.maxRounds,
            matchMode = game.matchMode,
            cumulativePoints = cumulative
        )
    }
}
