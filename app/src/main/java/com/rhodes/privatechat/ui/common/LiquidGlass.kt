package com.rhodes.privatechat.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import com.rhodes.privatechat.ui.theme.BG
import com.rhodes.privatechat.ui.theme.isLiquidGlass

/** Soft illumination is drawn once, behind content; text is never part of a blur layer.
 * No per-frame animation or screen capture is needed while typing or scrolling.
 */
@Composable
fun LiquidGlassBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(Color(0xFF0D152B))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(
                listOf(Color(0xFF415C90), Color.Transparent),
                center = Offset(size.width * 0.06f, size.height * 0.12f),
                radius = size.width * 1.25f))
            drawRect(Brush.radialGradient(
                listOf(Color(0xFF514374), Color.Transparent),
                center = Offset(size.width * 1.04f, size.height * 0.6f),
                radius = size.width * 0.95f))
            drawRect(Brush.radialGradient(
                listOf(Color(0xFF1E5365), Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 1.08f),
                radius = size.width * 1.1f))
            // Broad out-of-focus rings suggest curved light behind the glass.
            drawCircle(Color(0x125FCBFF), size.width * 0.66f,
                Offset(size.width * 0.91f, size.height * 0.36f),
                style = Stroke(size.width * 0.10f))
            drawCircle(Color(0x0EB9A0FF), size.width * 0.76f,
                Offset(size.width * 0.04f, size.height * 0.78f),
                style = Stroke(size.width * 0.08f))
        }
        content()
    }
}

/** A clipped light film with opposing bright/dim edges; never reduces content opacity. */
fun Modifier.glassSheen(shape: Shape): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val film = Brush.linearGradient(listOf(Color(0x0AFFFFFF), Color.Transparent, Color(0x066CBBFF)))
    val glow = Brush.radialGradient(listOf(Color(0x10E9F6FF), Color.Transparent),
        Offset(size.width * 0.12f, -size.height * 0.8f), size.width.coerceAtLeast(size.height) * 1.15f)
    val rim = Brush.linearGradient(listOf(Color(0xA6EBF8FF), Color(0x145DB4ED), Color(0x556F9EBF)))
    onDrawWithContent {
        drawOutline(outline, film)
        drawOutline(outline, glow)
        drawContent()
        drawOutline(outline, rim, style = Stroke(1.1f * density))
    }
}

/** Shared screen treatment, including screens that do not use AppBackground. */
fun Modifier.skinBackground(): Modifier = if (!isLiquidGlass) background(BG) else
    background(Color(0xFF0D152B)).drawWithCache {
        val blue = Brush.radialGradient(listOf(Color(0xFF3B5481), Color.Transparent),
            Offset(size.width * 0.05f, size.height * 0.08f), size.width * 1.4f)
        val violet = Brush.radialGradient(listOf(Color(0xFF493B6C), Color.Transparent),
            Offset(size.width, size.height * 0.7f), size.width * 1.15f)
        onDrawBehind { drawRect(blue); drawRect(violet) }
    }
