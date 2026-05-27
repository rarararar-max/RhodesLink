package com.example.rhodesterminal.game.mahjong

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 麻将牌面颜色 */
object TileColors {
    val man = Color(0xFF1565C0)      // 万：深蓝
    val pin = Color(0xFFC62828)      // 饼：红
    val sou = Color(0xFF2E7D32)      // 条：绿
    val honor = Color(0xFF37474F)    // 字牌：深灰
    val back = Color(0xFF1A237E)     // 牌背：深蓝
    val ivory = Color(0xFFFFF8E1)    // 象牙白底色
    val edge = Color(0xFF8D6E63)     // 边框浅棕
    val selected = Color(0xFFFFC107) // 选中高亮
    val shadow = Color(0x33000000)   // 阴影

    fun tileColor(tile: Tile): Color = when (tile.suit) {
        Suit.MAN -> man; Suit.PIN -> pin; Suit.SOU -> sou
        Suit.WIND, Suit.DRAGON -> honor
    }

    fun tileText(tile: Tile): String = when (tile.suit) {
        Suit.MAN -> "${tile.number}万"
        Suit.PIN -> "${tile.number}筒"
        Suit.SOU -> "${tile.number}条"
        Suit.WIND -> when (tile.number) { 1->"東"; 2->"南"; 3->"西"; 4->"北"; else->"?" }
        Suit.DRAGON -> when (tile.number) { 1->"白"; 2->"發"; 3->"中"; else->"?" }
    }
}

@Composable
fun MahjongTile(
    tile: Tile,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isFaceDown: Boolean = false,
    size: Dp = 48.dp
) {
    Canvas(modifier = modifier.size(size * 1.33f, size)) {
        val w = size.toPx()
        val h = size.toPx() * 1.33f
        val cr = CornerRadius(4.dp.toPx())
        val centerY = h / 2f

        // 阴影
        drawRoundRect(TileColors.shadow, topLeft = Offset(2.dp.toPx(), 2.dp.toPx()), size = Size(w, h), cornerRadius = cr)

        // 牌面底色
        drawRoundRect(TileColors.ivory, topLeft = Offset.Zero, size = Size(w, h), cornerRadius = cr)

        // 选中高亮
        if (isSelected) {
            drawRoundRect(TileColors.selected.copy(alpha = 0.3f), topLeft = Offset.Zero, size = Size(w, h), cornerRadius = cr)
            // 上浮阴影
            drawRoundRect(TileColors.selected, topLeft = Offset.Zero, size = Size(w, h), cornerRadius = cr)
        }

        // 边框
        drawRoundRect(TileColors.edge, topLeft = Offset.Zero, size = Size(w, h), cornerRadius = cr, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))

        if (isFaceDown) {
            drawRoundRect(TileColors.back, topLeft = Offset(3.dp.toPx(), 3.dp.toPx()), size = Size(w - 6.dp.toPx(), h - 6.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()))
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = Color.White.toArgb(); textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 14.dp.toPx()
                }
                canvas.nativeCanvas.drawText("🀫", w / 2, centerY + 5.dp.toPx(), paint)
            }
        } else {
            val color = TileColors.tileColor(tile)
            val text = TileColors.tileText(tile)

            // 左上角小字
            if (tile.suit != Suit.WIND && tile.suit != Suit.DRAGON) {
                drawIntoCanvas { canvas ->
                    val p = android.graphics.Paint().apply {
                        this.color = color.copy(alpha = 0.6f).toArgb()
                        textSize = 9.dp.toPx(); textAlign = android.graphics.Paint.Align.LEFT
                    }
                    canvas.nativeCanvas.drawText("${tile.number}", 4.dp.toPx(), 12.dp.toPx(), p)
                }
            }

            // 中心大字
            drawIntoCanvas { canvas ->
                val p = android.graphics.Paint().apply {
                    this.color = color.toArgb(); textSize = 16.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true
                }
                canvas.nativeCanvas.drawText(text, w / 2, centerY + 5.dp.toPx(), p)
            }
        }
    }
}

fun drawTileOnCanvas(drawScope: DrawScope, tile: Tile, x: Float, y: Float, w: Float, h: Float, isSelected: Boolean = false, isFaceDown: Boolean = false) {
    with(drawScope) {
        val cr = CornerRadius(3f)
        drawRoundRect(TileColors.shadow, topLeft = Offset(x + 1.5f, y + 1.5f), size = Size(w, h), cornerRadius = cr)
        drawRoundRect(TileColors.ivory, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = cr)
        if (isSelected) drawRoundRect(TileColors.selected.copy(alpha = 0.3f), topLeft = Offset(x, y), size = Size(w, h), cornerRadius = cr)
        drawRoundRect(TileColors.edge, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = cr, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))

        if (isFaceDown) {
            drawRoundRect(TileColors.back, topLeft = Offset(x + 2f, y + 2f), size = Size(w - 4f, h - 4f), cornerRadius = CornerRadius(2f))
        } else {
            val color = TileColors.tileColor(tile)
            val text = TileColors.tileText(tile)
            drawIntoCanvas { canvas ->
                val p = android.graphics.Paint().apply {
                    this.color = color.toArgb(); textSize = 28f; textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                canvas.nativeCanvas.drawText(text, x + w / 2, y + h / 2 + 8f, p)
            }
        }
    }
}
