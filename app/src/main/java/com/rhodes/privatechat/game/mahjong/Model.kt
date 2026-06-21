package com.rhodes.privatechat.game.mahjong

import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

object GameSerializer {
    fun serialize(game: GameState): String = json.encodeToString(game)
    fun deserialize(jsonStr: String): GameState = json.decodeFromString(jsonStr)
}

@Serializable
enum class Suit { MAN, PIN, SOU, WIND, DRAGON }

@Serializable
data class Tile(val suit: Suit, val number: Int) : Comparable<Tile> {
    companion object {
        fun allTiles() = listOf(
            *(1..9).flatMap { listOf(Tile(Suit.MAN, it), Tile(Suit.PIN, it), Tile(Suit.SOU, it)) }.toTypedArray(),
            *(1..4).map { Tile(Suit.WIND, it) }.toTypedArray(),
            *(1..3).map { Tile(Suit.DRAGON, it) }.toTypedArray()
        )
        fun fullWall(): MutableList<Tile> = MutableList(4) { allTiles() }.flatten().toMutableList()
        fun suitName(suit: Suit, number: Int): String = when (suit) {
            Suit.MAN -> "${number}万"; Suit.PIN -> "${number}筒"; Suit.SOU -> "${number}条"
            Suit.WIND -> when (number) { 1 -> "东"; 2 -> "南"; 3 -> "西"; 4 -> "北"; else -> "?" }
            Suit.DRAGON -> when (number) { 1 -> "白"; 2 -> "发"; 3 -> "中"; else -> "?" }
        }
        fun tileName(t: Tile): String = suitName(t.suit, t.number)
    }
    fun isHonor() = suit == Suit.WIND || suit == Suit.DRAGON
    fun isTerminal() = number == 1 || number == 9
    fun isYaochu() = isHonor() || isTerminal()
    fun ordinalForSort(): Int = when (suit) {
        Suit.MAN -> number - 1; Suit.PIN -> number + 8; Suit.SOU -> number + 17
        Suit.WIND -> number + 26; Suit.DRAGON -> number + 30
    }
    override fun compareTo(other: Tile): Int {
        val s = suit.compareTo(other.suit)
        return if (s != 0) s else number.compareTo(other.number)
    }
    override fun toString() = tileName(this)
}

@Serializable
enum class MeldType { CHI, PON, KAN, ANKAN }
@Serializable
data class Meld(val type: MeldType, val tiles: List<Tile>, val fromSeat: Seat)

@Serializable
enum class Seat { EAST, SOUTH, WEST, NORTH }

@Serializable
data class PlayerState(
    val opId: String, val name: String, val seat: Seat,
    val hand: MutableList<Tile> = mutableListOf(),
    val discards: MutableList<Tile> = mutableListOf(),
    val melds: MutableList<Meld> = mutableListOf(),
    var points: Int = 25000,
    var isFuriten: Boolean = false, var isRiichi: Boolean = false,
    var isTenpai: Boolean = false, val isHuman: Boolean = false,
    val attack: Float = 0.5f, val defense: Float = 0.5f,
    val meldPref: String = "medium", val specialTraits: List<String> = emptyList()
)

@Serializable
enum class MatchMode { QUICK, EAST, HALF }

@Serializable
data class GameState(
    val players: MutableList<PlayerState> = mutableListOf(),
    val ruleType: String = "basic_cn",
    val roundWind: Seat = Seat.EAST,
    var round: Int = 1, var honba: Int = 0, var riichiSticks: Int = 0,
    var dealerIdx: Int = 0, var currentTurn: Int = 0,
    var wall: MutableList<Tile> = mutableListOf(),
    val doraIndicators: MutableList<Tile> = mutableListOf(),
    val uraDoraIndicators: MutableList<Tile> = mutableListOf(),
    var assistantOpId: String = "",
    var lastDiscard: Tile? = null,
    var lastDiscardSeat: Seat? = null,
    var winnerSeat: Seat? = null,
    var drawnIdx: Int = -1,
    var ippatsuPlayerIdx: Int = -1,
    var matchMode: MatchMode = MatchMode.QUICK,
    var maxRounds: Int = 1,
    var chatLog: MutableList<String> = mutableListOf(),
    var matchStatus: String = "playing"
) {
    fun currentPlayer() = players[currentTurn]
    fun dealer() = players[dealerIdx]
    fun isDealer(seat: Seat) = players[dealerIdx].seat == seat
    fun humanPlayer() = players.find { it.isHuman }
    fun assistantPlayer() = players.find { it.opId == assistantOpId }
    fun roundLabel(): String = when (matchMode) {
        MatchMode.QUICK -> "快速局"
        MatchMode.EAST -> "东风战·东${round}"
        MatchMode.HALF -> if (round <= 4) "半庄·东${round}" else "半庄·南${round - 4}"
    }

    companion object {
        fun create(
            opIds: List<String>, opNames: List<String>,
            styles: List<Triple<Float, Float, String>>,
            userId: String, userName: String,
            assistantId: String,
            matchMode: MatchMode = MatchMode.QUICK
        ): GameState {
            val seats = listOf(Seat.EAST, Seat.SOUTH, Seat.WEST, Seat.NORTH)
            val players = mutableListOf<PlayerState>()
            val di = Random.nextInt(4)
            for (i in 0 until 3) players.add(PlayerState(
                opId = opIds[i], name = opNames[i], seat = seats[i],
                attack = styles[i].first, defense = styles[i].second, meldPref = styles[i].third
            ))
            players.add(PlayerState(opId = userId, name = userName, seat = seats[3], isHuman = true))
            val maxR = when (matchMode) { MatchMode.QUICK -> 1; MatchMode.EAST -> 4; MatchMode.HALF -> 8 }
            return GameState(players = players,
                dealerIdx = di, currentTurn = di,
                wall = Tile.fullWall().apply { shuffle() },
                assistantOpId = assistantId,
                matchMode = matchMode, maxRounds = maxR)
        }
    }
}

@Serializable
data class SettlementResult(
    val rankings: List<PlayerResult>, val userNetGain: Int,
    val exchangeRate: Int = 100, val basePoints: Int = 25000,
    val chatLog: List<String> = emptyList(),
    val winType: String = "流局",
    val winnerName: String = "",
    val loserName: String = "",
    val summary: String = "",
    val participants: List<String> = emptyList(),
    val assistantName: String = "",
    val currentRound: Int = 1,
    val maxRounds: Int = 1,
    val matchMode: MatchMode = MatchMode.QUICK,
    val cumulativePoints: Map<String, Int> = emptyMap()
)

@Serializable
data class PlayerResult(
    val opId: String = "",
    val name: String, val finalPoints: Int, val netGain: Int, val rank: Int,
    val yakus: List<String> = emptyList(), val han: Int = 0
)

@Serializable
data class GameStateCreateParams(
    val opIds: List<String>, val opNames: List<String>,
    val styles: List<Triple<Float, Float, String>>,
    val userId: String, val userName: String, val assistantId: String,
    val matchMode: MatchMode = MatchMode.QUICK
)

@Serializable
data class MahjongHistoryEntry(
    val time: Long, val opponents: List<String>, val userRank: Int,
    val userNetGain: Int, val userPoints: Int, val winType: String = "",
    val winnerName: String = ""
)
