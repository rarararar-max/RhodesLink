package com.rhodes.privatechat.ui.startup

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.*

private data class CrystalPoint(val x: Float, val y: Float, val z: Float) {
    operator fun plus(v: CrystalPoint) = CrystalPoint(x + v.x, y + v.y, z + v.z)
    operator fun times(v: Float) = CrystalPoint(x * v, y * v, z * v)
}
private data class CrystalFace(val a: CrystalPoint, val b: CrystalPoint, val c: CrystalPoint, val color: Color)

/** Irregular radial cluster of closed, four-sided crystal spikes, rather than a flat star. */
private fun crystalCluster(): List<CrystalFace> = buildList {
    val palette = listOf(Color(0xFF84281E), Color(0xFFE34C22), Color(0xFFFFB77D), Color(0xFFAC3520))
    repeat(13) { i ->
        val angle = i * 2.399963f
        val dz = -0.82f + (i % 5) * .4f
        val axis = CrystalPoint(cos(angle), sin(angle), dz)
        val length = .68f + (i % 4) * .12f
        val tip = axis * length
        val base = axis * .12f
        val across = CrystalPoint(-sin(angle), cos(angle), 0f)
        val depth = CrystalPoint(-cos(angle) * dz * .5f, -sin(angle) * dz * .5f, 1f)
        val width = .13f + i % 3 * .025f
        val corners = listOf(base + across * width, base + depth * width, base + across * -width, base + depth * -width)
        repeat(4) { side -> add(CrystalFace(tip, corners[side], corners[(side + 1) % 4], palette[(i + side) % palette.size])) }
    }
}

/** Fixed geometry is prepared once; frames only project and illuminate it. No user data is loaded. */
@Composable
fun TerminalStartup(sound: Boolean, onFinished: () -> Unit) {
    val finish by rememberUpdatedState(onFinished)
    val progress = remember { Animatable(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val orange = Color(0xFFFF923E)
    var started by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val fingerprint = rememberVectorPainter(Icons.Outlined.Fingerprint)
    val ambient = rememberInfiniteTransition(label = "fingerprint-breath")
    val breath by ambient.animateFloat(.35f, .8f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "breath")
    val faces = remember { crystalCluster() }
    // Each particle has a stable destination on the crystal surface, avoiding random flicker.
    val particles = remember(faces) {
        List(650) { i ->
            val face = faces[i % faces.size]
            val u = sqrt(((i * 37 % 997) + 1) / 998f)
            val v = (i * 71 % 991) / 991f
            face.a * (1 - u) + face.b * (u * (1 - v)) + face.c * (u * v)
        }
    }
    BackHandler { finish() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) finish() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        progress.animateTo(1f, tween(3000, easing = LinearEasing))
        delay(250)
        finish()
    }
    DisposableEffect(sound, started) {
        val tone = if (sound && started) runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, 16).apply { startTone(ToneGenerator.TONE_PROP_ACK, 100) }
        }.getOrNull() else null
        onDispose { tone?.release() }
    }
    // Read animation inside draw/layer blocks so particles do not recompose the whole screen every frame.
    Box(Modifier.fillMaxSize().graphicsLayer {
        alpha = (1 - ((progress.value - .96f) / .04f).coerceIn(0f, 1f))
    }.background(Color(0xFF090F16)).pointerInput(started) {
        // One owner for touch events: no default rectangular ripple and no competing parent consumer.
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Child controls (e.g. Skip) keep their own gestures.
            if (down.isConsumed) return@awaitEachGesture
            down.consume()
            val region = startupTouchRegion(size.width.toFloat(), size.height.toFloat())
            var valid = !started && region.contains(down.position.x, down.position.y)
            pressed = valid
            try {
                var released = false
                while (!released) {
                    val event = awaitPointerEvent()
                    val finger = event.changes.firstOrNull { it.id == down.id }
                    if (finger == null || event.changes.any { it.isConsumed } ||
                        event.changes.any { it.id != down.id && it.pressed }) valid = false
                    if (finger != null && !region.contains(finger.position.x, finger.position.y)) valid = false
                    pressed = valid
                    event.changes.forEach { it.consume() }
                    released = finger == null || !finger.pressed
                }
                if (valid) started = true
            } finally {
                pressed = false
            }
        }
    }) {
        Canvas(Modifier.fillMaxSize().semantics {
        if (!started) {
            role = Role.Button
            contentDescription = "指纹感应区"
            onClick(label = "唤醒终端") { started = true; true }
        }
    }) {
            val p = progress.value
            val region = startupTouchRegion(size.width, size.height)
            val center = Offset(region.centerX, region.centerY)
            val radius = min(size.width * .35f, size.height * .2f)
            val gather = ((p - .15f) / .43f).coerceIn(0f, 1f)
            val eased = gather * gather * (3 - 2 * gather)
            val solid = ((p - .4f) / .22f).coerceIn(0f, 1f)
            val power = ((p - .48f) / .32f).coerceIn(0f, 1f)
            drawRect(Brush.radialGradient(listOf(Color(0xFF263343), Color(0xFF090F16)), center, size.height * .7f))
            drawCircle(Brush.radialGradient(listOf(orange.copy(alpha = .06f + power * .12f), Color.Transparent), center, radius * 1.6f), radius * 1.6f, center)
            val scanY = size.height * ((p * 1.5f) % 1f)
            val grid = 32.dp.toPx()
            for (i in 0..(size.height / grid).toInt()) {
                val y = i * grid
                val glow = (1 - abs(y - scanY) / (grid * 3)).coerceIn(0f, 1f)
                drawLine(Color(0xFF9BAEBF).copy(alpha = .018f + glow * .055f), Offset(0f, y), Offset(size.width, y))
            }
            for (i in 0..(size.width / grid).toInt()) {
                val x = i * grid
                drawLine(Color.White.copy(alpha = .025f), Offset(x, 0f), Offset(x, size.height))
            }
            val ignition = if (started) (.2f + p / .18f).coerceIn(0f, 1f) else .13f
            for (ring in 0..2) {
                val r = radius * (1.02f + ring * .14f)
                val direction = if (ring % 2 == 0) 1 else -1
                repeat(3) { part ->
                    drawArc(orange.copy(alpha = (.18f + ring * .08f) * ignition), part * 120f + p * 65 * direction, 72f * ignition, false,
                        center - Offset(r, r), Size(r * 2, r * 2), style = Stroke(if (ring == 1) 2.dp.toPx() else 1.dp.toPx()))
                }
            }
            repeat(72) { i ->
                val angle = i * PI.toFloat() / 36
                val axis = Offset(cos(angle), sin(angle))
                val lit = i / 72f < p
                drawLine(if (lit) orange.copy(alpha = .75f) else Color(0xFF425362),
                    center + axis * radius * 1.4f, center + axis * radius * (if (i % 6 == 0) 1.49f else 1.44f), 1.dp.toPx())
            }
            // Y-axis rotation, slight top view, perspective and depth-sorted faces.
            val yaw = -.4f + p * .72f
            fun rotated(point: CrystalPoint): CrystalPoint {
                val x = point.x * cos(yaw) + point.z * sin(yaw)
                val z = -point.x * sin(yaw) + point.z * cos(yaw)
                return CrystalPoint(x, point.y * .96f - z * .28f, point.y * .28f + z * .96f)
            }
            fun project(point: CrystalPoint): Offset {
                val v = rotated(point)
                val perspective = 3.8f / (3.8f - v.z)
                return center + Offset(v.x, v.y) * (radius * .79f * perspective)
            }
            if (started && p > .12f) {
            faces.sortedBy { (rotated(it.a).z + rotated(it.b).z + rotated(it.c).z) / 3 }.forEachIndexed { index, face ->
                val a = project(face.a); val b = project(face.b); val c = project(face.c)
                val path = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }
                val depthLight = ((rotated(face.a).z + 1.4f) / 2.8f).coerceIn(.25f, 1f)
                val shade = Color(face.color.red * depthLight, face.color.green * depthLight, face.color.blue * depthLight)
                drawPath(path, Brush.linearGradient(listOf(face.color, shade), a, c), alpha = solid)
                val pulse = (1 - abs(p - (.54f + (index % 13) * .016f)) / .08f).coerceIn(0f, 1f)
                drawPath(path, orange.copy(alpha = solid * (.12f + pulse * .65f)), style = Stroke((.5f + pulse) * density))
                if (pulse > 0) drawLine(Color(0xFFFFE6BF).copy(alpha = pulse * solid), a, b, 1.dp.toPx())
            }
            particles.forEachIndexed { i, target ->
                val seed = i * 2.399963f
                val scatter = Offset(cos(seed + p * 2) * (1.3f + i % 7 * .13f), sin(seed + p * 2) * (1.3f + i % 9 * .09f))
                val local = project(target)
                val at = local * eased + (center + scatter * radius) * (1 - eased)
                val alpha = (.25f + (i % 5) * .13f) * (1 - solid * .88f)
                drawCircle(if (i % 4 == 0) Color(0xFFFFE5BF).copy(alpha = alpha) else orange.copy(alpha = alpha), (if (i % 11 == 0) 1.5f else .75f) * density, at)
            }
            }
            // Fingerprint exists only before/at the beginning of the touch-driven sequence.
            val fingerprintAlpha = if (!started) 1f else (1 - p / .17f).coerceIn(0f, 1f)
            if (fingerprintAlpha > 0f) {
                val fingerRadius = radius * .61f
                if (pressed) {
                    drawCircle(Brush.radialGradient(listOf(orange.copy(alpha = .22f), Color.Transparent), center, region.radius), region.radius, center)
                    drawCircle(orange.copy(alpha = .65f), region.radius * .9f, center, style = Stroke(1.dp.toPx()))
                }
                // Use a continuous, established fingerprint vector instead of disconnected procedural arcs.
                translate(center.x - fingerRadius, center.y - fingerRadius) {
                    with(fingerprint) {
                        draw(Size(fingerRadius * 2, fingerRadius * 2),
                            alpha = fingerprintAlpha * (if (pressed) 1f else .55f + breath * .4f),
                            colorFilter = ColorFilter.tint(orange))
                    }
                }
                if (started) {
                    val scanBottom = center.y + fingerRadius * (1 - p / .17f * 2)
                    clipRect(center.x - fingerRadius, scanBottom, center.x + fingerRadius, center.y + fingerRadius) {
                        translate(center.x - fingerRadius, center.y - fingerRadius) {
                            with(fingerprint) { draw(Size(fingerRadius * 2, fingerRadius * 2), alpha = fingerprintAlpha,
                                colorFilter = ColorFilter.tint(Color(0xFFFFE6B2))) }
                        }
                    }
                }
                if (started) {
                    val sweep = center.y + fingerRadius * (1.5f - p / .17f * 3f)
                    drawLine(Color(0xFFFFE6B2).copy(alpha = fingerprintAlpha), Offset(center.x - fingerRadius * 1.2f, sweep), Offset(center.x + fingerRadius * 1.2f, sweep), 2.dp.toPx())
                    val ripple = radius * (.5f + p * 8)
                    drawCircle(orange.copy(alpha = fingerprintAlpha * .6f), ripple, center, style = Stroke(2.dp.toPx()))
                }
            }
            // Small orbital embers remain alive after assembly.
            if (started) repeat(22) { i ->
                val angle = i * 2.4f + p * .7f
                val at = center + Offset(cos(angle), sin(angle)) * radius * (1.02f + i % 4 * .08f)
                drawCircle(orange.copy(alpha = .2f + .25f * sin(i + p * 6).pow(2)), density, at)
            }
        }
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("RHODES / PERSONAL TERMINAL", color = orange.copy(alpha = .8f), fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(14.dp))
            Text("罗德岛通讯端", color = Color(0xFFECECE6), fontSize = 28.sp)
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 34.dp, vertical = 42.dp).fillMaxWidth()) {
            val p by remember { derivedStateOf { progress.value } }
            Text(if (started) "TERMINAL WAKE SEQUENCE" else "AWAITING YOUR TOUCH", color = Color(0xFF7F96A4), fontSize = 9.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(18.dp))
            listOf("核心唤醒", "界面同步", "终端就绪").forEachIndexed { i, title ->
                val lit = started && p > .2f + i * .25f
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0${i + 1}   $title", color = if (lit) Color(0xFFDCDBD1) else Color(0xFF637481), fontSize = 12.sp)
                    Text(if (lit) "●  READY" else "·  STANDBY", color = if (lit) orange else Color(0xFF536572), fontSize = 9.sp, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.height(17.dp))
            Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                val value = progress.value
                val count = 36
                val gap = 3.dp.toPx()
                val segment = (size.width - gap * (count - 1)) / count
                val y = size.height / 2
                repeat(count) { i ->
                    val lit = (value * count - i).coerceIn(0f, 1f)
                    val x = i * (segment + gap)
                    drawRect(Color(0xFF293844), Offset(x, y - 2.dp.toPx()), Size(segment, 4.dp.toPx()))
                    if (lit > 0) {
                        drawRect(orange.copy(alpha = lit * .12f), Offset(x - density, y - 5.dp.toPx()), Size(segment + 2 * density, 10.dp.toPx()))
                        drawRect(orange.copy(alpha = lit), Offset(x, y - 2.dp.toPx()), Size(segment, 4.dp.toPx()))
                        drawLine(Color(0xFFFFD6AA).copy(alpha = lit * .75f), Offset(x, y - density), Offset(x + segment, y - density), density)
                    }
                }
                if (started) {
                val head = size.width * value
                drawCircle(Brush.radialGradient(listOf(orange.copy(alpha = .7f), Color.Transparent), Offset(head, y), 13.dp.toPx()), 13.dp.toPx(), Offset(head, y))
                drawCircle(Color(0xFFFFF2D5), 2.dp.toPx(), Offset(head, y))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(if (!started) if (pressed) "松开，唤醒终端" else "轻触指纹，唤醒终端" else if (p > .83f) "欢迎回来，博士" else "终端唤醒中　${(p * 100).toInt()}%", color = Color(0xFFE9ECE8), fontSize = 22.sp)
            Text(if (p > .83f) "RHODES ISLAND / CONNECTION ESTABLISHED" else "RHODES ISLAND / PERSONAL TERMINAL", color = Color(0xFF70838F), fontSize = 8.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 10.dp))
        }
        TextButton(onClick = { finish() }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(10.dp)) {
            Text("跳过", color = Color(0xFF9DABB5), fontSize = 12.sp)
        }
    }
}
