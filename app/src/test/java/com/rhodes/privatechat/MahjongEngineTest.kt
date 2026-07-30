package com.rhodes.privatechat

import com.rhodes.privatechat.game.mahjong.Engine
import com.rhodes.privatechat.game.mahjong.GameState
import com.rhodes.privatechat.game.mahjong.PlayerState
import com.rhodes.privatechat.game.mahjong.Meld
import com.rhodes.privatechat.game.mahjong.MeldType
import com.rhodes.privatechat.game.mahjong.Seat
import com.rhodes.privatechat.game.mahjong.Suit
import com.rhodes.privatechat.game.mahjong.Tile
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class MahjongEngineTest {
    @Test
    fun simplifiedScoringUsesFixedPointsAndHighestPatternOnly() {
        val normalHand = listOf(
            Tile(Suit.MAN, 1), Tile(Suit.MAN, 2), Tile(Suit.MAN, 3),
            Tile(Suit.PIN, 1), Tile(Suit.PIN, 2), Tile(Suit.PIN, 3),
            Tile(Suit.SOU, 1), Tile(Suit.SOU, 2), Tile(Suit.SOU, 3),
            Tile(Suit.MAN, 4), Tile(Suit.MAN, 5), Tile(Suit.MAN, 6),
            Tile(Suit.DRAGON, 1), Tile(Suit.DRAGON, 1)
        )
        val fullFlushHand = listOf(
            Tile(Suit.MAN, 1), Tile(Suit.MAN, 1), Tile(Suit.MAN, 1),
            Tile(Suit.MAN, 2), Tile(Suit.MAN, 2), Tile(Suit.MAN, 2),
            Tile(Suit.MAN, 3), Tile(Suit.MAN, 3), Tile(Suit.MAN, 3),
            Tile(Suit.MAN, 4), Tile(Suit.MAN, 4), Tile(Suit.MAN, 4),
            Tile(Suit.MAN, 5), Tile(Suit.MAN, 5)
        )

        assertEquals(1000, Engine.checkYakuLocal(normalHand, emptyList(), Seat.EAST, Seat.EAST, true, false).points)
        assertEquals(1500, Engine.checkYakuLocal(normalHand, emptyList(), Seat.EAST, Seat.EAST, true, true).points)
        assertEquals(4000, Engine.checkYakuLocal(fullFlushHand, emptyList(), Seat.EAST, Seat.EAST, true, false).points)
        assertEquals(200, Engine.OPEN_KAN_POINTS)
        assertEquals(400, Engine.CONCEALED_KAN_POINTS)
    }

    @Test
    fun currentVersionGamePassesSaveIntegrityCheck() {
        val game = GameState.create(
            opIds = listOf("a", "b", "c"),
            opNames = listOf("甲", "乙", "丙"),
            styles = List(3) { Triple(0.5f, 0.5f, "medium") },
            userId = "user", userName = "用户", assistantId = "helper"
        )
        Engine.deal(game)

        assertTrue(game.isCompatibleSave())
    }

    @Test
    fun oldVersionGameIsNotResumed() {
        val game = GameState.create(
            opIds = listOf("a", "b", "c"),
            opNames = listOf("甲", "乙", "丙"),
            styles = List(3) { Triple(0.5f, 0.5f, "medium") },
            userId = "user", userName = "用户", assistantId = "helper"
        ).copy(saveVersion = 1)

        assertFalse(game.isCompatibleSave())
    }

    @Test
    fun malformedMeldSaveIsNotResumed() {
        val game = GameState.create(
            opIds = listOf("a", "b", "c"),
            opNames = listOf("甲", "乙", "丙"),
            styles = List(3) { Triple(0.5f, 0.5f, "medium") },
            userId = "user", userName = "用户", assistantId = "helper"
        ).also(Engine::deal)
        game.players.first().melds += Meld(
            MeldType.CHI,
            listOf(Tile(Suit.MAN, 1), Tile(Suit.MAN, 1), Tile(Suit.MAN, 3)),
            Seat.SOUTH
        )

        assertFalse(game.isCompatibleSave())
    }

    @Test
    fun dealerCanWinWithTheInitialFourteenTiles() {
        val hand = listOf(
            Tile(Suit.MAN, 1), Tile(Suit.MAN, 2), Tile(Suit.MAN, 3),
            Tile(Suit.PIN, 1), Tile(Suit.PIN, 2), Tile(Suit.PIN, 3),
            Tile(Suit.SOU, 1), Tile(Suit.SOU, 2), Tile(Suit.SOU, 3),
            Tile(Suit.MAN, 4), Tile(Suit.MAN, 5), Tile(Suit.MAN, 6),
            Tile(Suit.DRAGON, 1), Tile(Suit.DRAGON, 1)
        )

        assertTrue(Engine.canTsumo(hand, emptyList()))
    }

    @Test
    fun tenpaiWithOpenMeldUsesTheMeldWhenCountingTiles() {
        val hand = listOf(
            Tile(Suit.MAN, 1), Tile(Suit.MAN, 2), Tile(Suit.MAN, 3),
            Tile(Suit.MAN, 4), Tile(Suit.MAN, 5), Tile(Suit.MAN, 6),
            Tile(Suit.PIN, 2), Tile(Suit.PIN, 3), Tile(Suit.PIN, 4),
            Tile(Suit.SOU, 7)
        )
        val melds = listOf(Meld(MeldType.PON, List(3) { Tile(Suit.SOU, 7) }, Seat.EAST))

        assertTrue(Engine.isTenpaiState(hand, melds))
    }
}
