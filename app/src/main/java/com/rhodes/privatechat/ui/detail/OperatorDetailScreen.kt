package com.rhodes.privatechat.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rhodes.privatechat.data.db.entity.OperatorEntity
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import com.rhodes.privatechat.data.db.entity.RelationshipEntity
import com.rhodes.privatechat.data.db.entity.RelationshipType
import com.rhodes.privatechat.data.repository.BfsNode
import com.rhodes.privatechat.ui.relation.RelationshipUiMapper
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.MainViewModel
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.roundToInt

@Composable
fun OperatorDetailScreen(viewModel: MainViewModel, operator: OperatorEntity, onBack: () -> Unit, onOperatorClick: (OperatorEntity) -> Unit, modifier: Modifier = Modifier) {
    val operators by viewModel.operators.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    var graphNodes by remember { mutableStateOf<List<BfsNode>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var sharedMemories by remember { mutableStateOf("") }
    // 从状态列表获取最新数据，确保 userRelation 等字段更新
    val currentOperator = operators.find { it.id == operator.id } ?: operator
    val nearbyOps = operators.filter { it.id != currentOperator.id }.take(6)

    LaunchedEffect(currentOperator.id) {
        viewModel.loadRelationGraph(currentOperator.id) { nodes ->
            graphNodes = nodes; isLoading = false
        }
        viewModel.loadSharedMemories(currentOperator.id) { text ->
            sharedMemories = text
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Spacer(modifier = Modifier.width(4.dp))
            Text(currentOperator.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)
        LazyColumn {
            item { ProfileSection(currentOperator) }
            item { IntimacySection(currentOperator.intimacy) }
            item { StatusSection(currentOperator) }
            item { NearbySection(operators = nearbyOps, onClick = onOperatorClick) }
            item { RelationsSection(operatorName = currentOperator.name, graphNodes = graphNodes, isLoading = isLoading, userRelation = currentOperator.userRelation, operators = operators, userAvatarUri = userProfile.avatarUri, onNameClick = { name -> if (name == "用户") { /* 点击用户节点无跳转 */ } else { val op = operators.find { it.name == name }; if (op != null) onOperatorClick(op) } }) }
            item { SharedMemorySection(memories = sharedMemories) }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
    }
}

@Composable private fun ProfileSection(op: OperatorEntity) {
    Column(modifier = Modifier.fillMaxWidth().background(Surface).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        OperatorAvatarImage(avatarUri = op.avatarUri, name = op.name, modifier = Modifier.size(100.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(op.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(op.title, fontSize = 13.sp, color = TextSecondary)
        if (op.description.isNotBlank()) { Spacer(modifier = Modifier.height(6.dp)); Text(op.description, fontSize = 12.sp, color = TextTertiary, modifier = Modifier.padding(horizontal = 32.dp)) }
    }
    HorizontalDivider(color = BG, thickness = 8.dp)
}

@Composable private fun IntimacySection(intimacy: Int) {
    val level = when { intimacy >= 80 -> "亲密"; intimacy >= 60 -> "友好"; intimacy >= 40 -> "熟悉"; intimacy >= 20 -> "认识"; else -> "初识" }
    Column(modifier = Modifier.fillMaxWidth().background(Surface).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("好感度 · $level", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary); Text("$intimacy / 1000", fontSize = 12.sp, color = Primary, fontWeight = FontWeight.SemiBold) }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(progress = { intimacy / 1000f }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = Primary, trackColor = Gray100)
    }
    HorizontalDivider(color = BG, thickness = 8.dp)
}

@Composable private fun StatusSection(op: OperatorEntity) {
    Column(modifier = Modifier.fillMaxWidth().background(Surface).padding(16.dp)) {
        Text("当前状态", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) { StatusItem("心情", op.emotion.ifBlank { "平静" }); Spacer(modifier = Modifier.width(12.dp)); StatusItem("位置", op.location.ifBlank { "罗德岛" }) }
        Spacer(modifier = Modifier.height(8.dp))
        Text("状态描述", fontSize = 12.sp, color = TextSecondary)
        Text("在${op.location.ifBlank { "罗德岛" }}${op.activity.ifBlank { "休息" }}，心情${op.emotion.ifBlank { "平静" }}。", fontSize = 13.sp, color = TextPrimary)
    }
    HorizontalDivider(color = BG, thickness = 8.dp)
}

@Composable private fun StatusItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Gray100).padding(12.dp)) {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}

@Composable private fun NearbySection(operators: List<OperatorEntity>, onClick: (OperatorEntity) -> Unit) {
    if (operators.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().background(Surface).padding(16.dp)) {
        Text("附近的干员", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(operators) { op ->
                Column(modifier = Modifier.clickable { onClick(op) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    OperatorAvatarImage(avatarUri = op.avatarUri, name = op.name, modifier = Modifier.size(48.dp))
                    Text(op.name, fontSize = 11.sp, color = TextPrimary)
                }
            }
        }
    }
    HorizontalDivider(color = BG, thickness = 8.dp)
}

private fun relDesc(operatorName: String, rel: RelationshipEntity): String {
    val t = rel.type
    if (t == RelationshipType.BIG_SISTER) return "${rel.relatedOperatorName}的【姐姐】"
    if (t == RelationshipType.LITTLE_SISTER) return "${rel.relatedOperatorName}的【妹妹】"
    if (t == RelationshipType.BIG_BROTHER) return "${rel.relatedOperatorName}的【哥哥】"
    if (t == RelationshipType.LITTLE_BROTHER) return "${rel.relatedOperatorName}的【弟弟】"
    if (t == RelationshipType.MOTHER) return "${rel.relatedOperatorName}的【母亲】"
    if (t == RelationshipType.FATHER) return "${rel.relatedOperatorName}的【父亲】"
    if (t == RelationshipType.DAUGHTER) return "${rel.relatedOperatorName}的【女儿】"
    if (t == RelationshipType.SON) return "${rel.relatedOperatorName}的【儿子】"
    if (t == RelationshipType.BOSS) return "${rel.relatedOperatorName}的【上司】"
    if (t == RelationshipType.SUBORDINATE) return "${rel.relatedOperatorName}的【下属】"
    if (t == RelationshipType.GUARDIAN) return "${rel.relatedOperatorName}的【监护人】"
    if (t == RelationshipType.CAPTAIN) return "${rel.relatedOperatorName}的【队长】"
    if (t == RelationshipType.MEMBER) return "${rel.relatedOperatorName}的【队员】"
    if (t == RelationshipType.MENTOR) return "${rel.relatedOperatorName}的【导师】"
    if (t == RelationshipType.STUDENT) return "${rel.relatedOperatorName}的【学生】"
    if (t == RelationshipType.CLOSE_FRIEND) return "${rel.relatedOperatorName}的【挚友】"
    if (t == RelationshipType.FRIEND) return "${rel.relatedOperatorName}的【朋友】"
    if (t == RelationshipType.COMRADE) return "${rel.relatedOperatorName}的【战友】"
    if (t == RelationshipType.TEAMMATE) return "${rel.relatedOperatorName}的【队友】"
    if (t == RelationshipType.RIVAL) return "${rel.relatedOperatorName}的【对手】"
    if (t == RelationshipType.CRUSH) return "${rel.relatedOperatorName}的【暗恋对象】"
    if (t == RelationshipType.LOVER) return "${rel.relatedOperatorName}的【恋人】"
    if (t == RelationshipType.FAMILY) return "${rel.relatedOperatorName}的【家人】"
    return ""
}

private data class GraphDisplayNode(
    val name: String,
    val label: String,
    val color: Color,
    val depth: Int,
    val parentName: String,
    val isUser: Boolean,
    var angle: Float = 0f
)

private fun bfsLabel(parentName: String, childName: String, type: RelationshipType?, isReverse: Boolean): String {
    val t = type ?: return ""
    val ch = when (t) {
        RelationshipType.BIG_SISTER -> "姐姐"; RelationshipType.LITTLE_SISTER -> "妹妹"
        RelationshipType.BIG_BROTHER -> "哥哥"; RelationshipType.LITTLE_BROTHER -> "弟弟"
        RelationshipType.MOTHER -> "母亲"; RelationshipType.FATHER -> "父亲"
        RelationshipType.DAUGHTER -> "女儿"; RelationshipType.SON -> "儿子"
        RelationshipType.BOSS -> "上司"; RelationshipType.SUBORDINATE -> "下属"
        RelationshipType.GUARDIAN -> "监护人"; RelationshipType.CAPTAIN -> "队长"
        RelationshipType.MEMBER -> "队员"; RelationshipType.MENTOR -> "导师"
        RelationshipType.STUDENT -> "学生"; RelationshipType.CLOSE_FRIEND -> "挚友"
        RelationshipType.FRIEND -> "朋友"; RelationshipType.COMRADE -> "战友"
        RelationshipType.TEAMMATE -> "队友"; RelationshipType.RIVAL -> "对手"
        RelationshipType.CRUSH -> "暗恋对象"; RelationshipType.LOVER -> "恋人"
        RelationshipType.FAMILY -> "家人"; else -> ""
    }
    if (ch.isEmpty()) return ""
    if (isReverse) return "${childName}把你当【$ch】"
    return "${childName}的【$ch】"
}

@Composable private fun RelationsSection(operatorName: String, graphNodes: List<BfsNode>, isLoading: Boolean, userRelation: String = "", operators: List<OperatorEntity> = emptyList(), userAvatarUri: String = "", onNameClick: (String) -> Unit = {}) {
    Column(modifier = Modifier.fillMaxWidth().background(Surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("关系网", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (isLoading) { Text("加载中...", color = TextSecondary) }
        else if (graphNodes.isEmpty() && userRelation.isBlank()) { Text("暂无关系网", fontSize = 13.sp, color = TextTertiary) }
        else {
            val displayNodes = remember(graphNodes, userRelation) {
                val map = mutableMapOf<String, GraphDisplayNode>()
                // 中心节点（depth=0 的 BfsNode）
                val center = graphNodes.firstOrNull()
                if (center != null) map[center.operatorName] = GraphDisplayNode(center.operatorName, "", Primary, 0, "", false)
                // BFS 遍历得到的其他节点
                for (n in graphNodes.drop(1)) {
                    val pn = graphNodes.find { it.operatorId == n.parentId }?.operatorName ?: n.parentId
                    val lbl = bfsLabel(pn, n.operatorName, n.relType, n.isReverse)
                    val clr = n.relType?.let { relColor(it) } ?: Color(0xFFBDBDBD)
                    map[n.operatorName] = GraphDisplayNode(n.operatorName, lbl, clr, n.depth, pn, false)
                }
                // 用户关系只展示当前干员与用户的关系，避免把其他干员误挂到当前关系网。
                if (userRelation.isNotBlank()) {
                    if ("用户" !in map) {
                        map["用户"] = GraphDisplayNode("用户", "", Color(0xFF8B5CF6), 1, operatorName, true)
                    }
                    map["用户"] = map["用户"]!!.copy(label = "用户是你的【$userRelation】")
                }
                map.values.toList()
            }
            if (displayNodes.isEmpty()) { Text("暂无关系网", fontSize = 13.sp, color = TextTertiary) }
            else {
                RelationLegend()
                Spacer(modifier = Modifier.height(8.dp))
                var panOffset by remember { mutableStateOf(Offset.Zero) }
                var zoomScale by remember { mutableFloatStateOf(1f) }
                var boxWidth by remember { mutableFloatStateOf(1f) }
                var boxHeight by remember { mutableFloatStateOf(1f) }
                val density = androidx.compose.ui.platform.LocalDensity.current
                val maxDepth = displayNodes.maxOf { it.depth }.coerceAtLeast(1)
                Box(modifier = Modifier.fillMaxWidth().height(400.dp)
                    .onSizeChanged { boxWidth = it.width.toFloat(); boxHeight = it.height.toFloat() }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(0.5f, 3f)
                            panOffset += pan
                        }
                    }
                ) {
                    val cx = boxWidth / 2f + panOffset.x
                    val cy = boxHeight / 2f + panOffset.y
                    val maxR = (minOf(boxWidth, boxHeight) / 2f - 80f).coerceAtLeast(100f)
                    val baseRadius = (maxR / maxDepth).coerceIn(120f, 400f) * zoomScale
                    // 按 depth 分组并分配角度
                    val depthGroups = displayNodes.groupBy { it.depth }
                    val positioned = depthGroups.flatMap { (depth, grp) ->
                        val step = 360f / grp.size
                        // 起始角度偏移使节点散布均匀
                        val startOffset = if (depth % 2 == 0) 0f else step / 2f
                        grp.forEachIndexed { i, node -> node.angle = startOffset + step * i + 90f * (depth - 1) }
                        grp
                    }
                    // 计算所有节点坐标 {name -> Offset}
                    val namePos = mutableMapOf<String, Offset>()
                    namePos[operatorName] = Offset(cx, cy)
                    for (dn in positioned) {
                        if (dn.name == operatorName) continue
                        val r = baseRadius * dn.depth
                        val rad = Math.toRadians(dn.angle.toDouble())
                        namePos[dn.name] = Offset(cx + r * cos(rad).toFloat(), cy + r * sin(rad).toFloat())
                    }
                    // Canvas：连线 + 箭头（箭头在子节点端）
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        for (dn in positioned) {
                            val pos = namePos[dn.name] ?: continue
                            val parentPos = namePos[dn.parentName] ?: continue
                            if (dn.depth == 0) continue
                            // 从父节点到当前节点画线（缩短一点避免箭头被节点遮挡）
                            val dirX = pos.x - parentPos.x
                            val dirY = pos.y - parentPos.y
                            val dist = kotlin.math.sqrt(dirX * dirX + dirY * dirY)
                            if (dist <= 0f) continue
                            val nx = dirX / dist; val ny = dirY / dist
                            val endX = pos.x - nx * 20f
                            val endY = pos.y - ny * 20f
                            drawLine(dn.color.copy(alpha = 0.6f), parentPos, Offset(endX, endY), strokeWidth = 2f)
                            // 箭头在子节点端
                            val a = atan2(pos.y.toDouble() - parentPos.y, pos.x.toDouble() - parentPos.x)
                            val arrowLen = 18f; val arrowAngle = 0.5
                            val ax = endX; val ay = endY
                            val path = Path().apply {
                                moveTo(ax, ay)
                                lineTo(ax - arrowLen * cos(a - arrowAngle).toFloat(), ay - arrowLen * sin(a - arrowAngle).toFloat())
                                lineTo(ax - arrowLen * cos(a + arrowAngle).toFloat(), ay - arrowLen * sin(a + arrowAngle).toFloat())
                                close()
                            }
                            drawPath(path, dn.color)
                        }
                    }
                    // 标签
                    for (dn in positioned) {
                        if (dn.depth == 0 || dn.label.isBlank()) continue
                        val pos = namePos[dn.name] ?: continue
                        val parentPos = namePos[dn.parentName] ?: continue
                        val midX = (parentPos.x + pos.x) / 2f + 10f
                        val midY = (parentPos.y + pos.y) / 2f - 6f
                        val shortLabel = if (dn.label.contains("【")) {
                            dn.label.substringAfter("【").substringBefore("】").let { if (it.length > 6) it.take(6) + "…" else it }
                        } else dn.label.take(6)
                        with(density) { Text(shortLabel, color = dn.color, fontSize = 11.sp,
                            modifier = Modifier.offset { IntOffset(midX.roundToInt(), midY.roundToInt()) }) }
                    }
                    // 节点圆圈
                    val nodeAvatarMap = remember(operators) {
                        operators.associate { it.name to it.avatarUri }.toMutableMap().apply {
                            operators.forEach { put(it.id, it.avatarUri) }
                            put("用户", userAvatarUri)
                        }
                    }
                    for (dn in positioned) {
                        val pos = namePos[dn.name] ?: continue
                        val isCenter = dn.depth == 0
                        val sz = if (dn.isUser) 160f else 144f
                        val nodeAvatarUri = nodeAvatarMap[dn.name] ?: ""
                        with(density) { Box(modifier = Modifier
                            .offset { IntOffset((pos.x - sz / 2f).roundToInt(), (pos.y - sz / 2f).roundToInt()) }
                            .size((sz * zoomScale).toDp())
                            .clip(CircleShape)
                            .background(if (isCenter) Card else dn.color)
                            .then(if (isCenter) Modifier.border(3.dp, Primary, CircleShape) else Modifier)
                            .clickable { onNameClick(dn.name) },
                            contentAlignment = Alignment.Center
                        ) {
                            OperatorAvatarImage(avatarUri = nodeAvatarUri, name = dn.name, modifier = Modifier.fillMaxSize())
                        } }
                    }
                }
            }
        }
    }
    HorizontalDivider(color = BG, thickness = 8.dp)
}

@Composable private fun RelationLegend() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf(
            "家人" to ErrorRed,
            "朋友" to Color(0xFF00BCD4),
            "上下级" to Color(0xFFFFC107),
            "师生" to Color(0xFF8B5CF6),
            "对手" to Color(0xFFEF4444),
            "用户" to Color(0xFF8B5CF6)
        )) { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(item.second))
                Spacer(modifier = Modifier.width(4.dp))
                Text(item.first, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

@Composable private fun SharedMemorySection(memories: String) {
    Column(modifier = Modifier.fillMaxWidth().background(Surface).padding(16.dp)) {
        Text("共享记忆", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        if (memories.isBlank() || memories == "无") {
            Text("暂无共享记忆", fontSize = 13.sp, color = TextTertiary)
        } else {
            memories.lines().filter { it.isNotBlank() }.forEach { line ->
                Text(line, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

private fun relColor(type: RelationshipType): Color {
    return RelationshipUiMapper.color(type)
}



