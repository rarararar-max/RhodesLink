package com.rhodes.privatechat.ui.relation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.data.db.entity.OperatorEntity
import com.rhodes.privatechat.data.db.entity.RelationshipEntity
import com.rhodes.privatechat.data.db.entity.RelationshipType
import com.rhodes.privatechat.ui.theme.Divider as DividerColor
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.MainViewModel

@Composable
fun RelationshipScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val operators by viewModel.operators.collectAsState()
    var selectedOperator by remember { mutableStateOf<OperatorEntity?>(null) }
    var relationships by remember { mutableStateOf<List<RelationshipEntity>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<RelationshipType?>(null) }
    var editingRel by remember { mutableStateOf<RelationshipEntity?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).systemBarsPadding()) {
        RelationTopBar(title = if (selectedOperator != null) "${selectedOperator!!.name}的关系网" else "关系网", onBack = {
            if (selectedOperator != null) selectedOperator = null else onBack()
        })

        if (selectedOperator == null) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true,
                placeholder = { Text("搜索干员") }
            )
            val filteredOps = operators.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) || it.title.contains(search, ignoreCase = true) }
            LazyColumn {
                items(filteredOps) { op ->
                    Row(modifier = Modifier.fillMaxWidth().background(Card).clickable {
                        selectedOperator = op
                        search = ""
                        typeFilter = null
                        viewModel.loadRelationships(op.id) { relationships = it }
                    }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OperatorAvatarImage(avatarUri = op.avatarUri, name = op.name, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = op.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = op.title, fontSize = 12.sp, color = Color(0xFF888888))
                        }
                    }
                    HorizontalDivider(color = DividerColor)
                }
            }
        } else {
            RelationFilterBar(typeFilter = typeFilter, onFilter = { typeFilter = it })
            val filteredRels = relationships.filter { rel ->
                (typeFilter == null || rel.type == typeFilter) &&
                    (search.isBlank() || rel.relatedOperatorName.contains(search, ignoreCase = true) || rel.note.contains(search, ignoreCase = true))
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("搜索关系对象或备注") }
            )
            if (relationships.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "暂无自建关系。关系会影响群聊互动、共享记忆和日记内容。", fontSize = 14.sp, color = Color(0xFF999999), modifier = Modifier.padding(24.dp))
                }
            } else {
                LazyColumn { items(filteredRels, key = { it.id }) { RelationshipItem(operatorName = selectedOperator!!.name, rel = it, onClick = { editingRel = it }) } }
            }
        }
    }
        val currentOp = selectedOperator
        val rel = editingRel
        if (currentOp != null && rel != null) {
            RelationshipEditDialog(
                operator = currentOp,
                rel = rel,
                operators = operators,
                onDismiss = { editingRel = null },
                onSave = { updated, reciprocal ->
                    viewModel.saveRelationship(updated, reciprocal) {
                        viewModel.loadRelationships(currentOp.id) { relationships = it }
                    }
                    editingRel = null
                }
            )
        }
    }
}

@Composable
private fun RelationTopBar(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(Card).padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(Icons.Default.Hub, null, tint = Primary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
    HorizontalDivider(color = DividerColor)
}

@Composable
private fun RelationshipItem(operatorName: String, rel: RelationshipEntity, onClick: (RelationshipEntity) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Card).clickable { onClick(rel) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OperatorAvatarImage(avatarUri = "", name = rel.relatedOperatorName, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = rel.relatedOperatorName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    RelationshipBadge(type = rel.type)
                }
                Text(text = RelationshipUiMapper.description(operatorName, rel.relatedOperatorName, rel.type), fontSize = 11.sp, color = TextSecondary)
                if (rel.note.isNotBlank()) {
                    Text(text = rel.note, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { rel.intimacy / 100f },
                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = relationshipColor(rel.type),
                trackColor = DividerColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "${rel.intimacy}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = relationshipColor(rel.type))
        }
    }
    HorizontalDivider(color = DividerColor)
}

@Composable
private fun RelationshipBadge(type: RelationshipType) {
    val label = RelationshipUiMapper.label(type)
    val color = RelationshipUiMapper.color(type)
    Text(
        text = label, fontSize = 10.sp, color = color,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

private fun relationshipLabel(type: RelationshipType): Pair<String, Color> {
    val red = Color(0xFFE53935)
    if (type == RelationshipType.BIG_SISTER) return "姐姐" to red
    if (type == RelationshipType.LITTLE_SISTER) return "妹妹" to red
    if (type == RelationshipType.BIG_BROTHER) return "哥哥" to red
    if (type == RelationshipType.LITTLE_BROTHER) return "弟弟" to red
    if (type == RelationshipType.MOTHER) return "母亲" to red
    if (type == RelationshipType.FATHER) return "父亲" to red
    if (type == RelationshipType.DAUGHTER) return "女儿" to red
    if (type == RelationshipType.SON) return "儿子" to red
    if (type == RelationshipType.FAMILY) return "家人" to red
    if (type == RelationshipType.GUARDIAN) return "监护人" to red
    if (type == RelationshipType.CLOSE_FRIEND) return "挚友" to Color(0xFF00BCD4)
    if (type == RelationshipType.FRIEND) return "朋友" to Color(0xFF4CAF50)
    if (type == RelationshipType.BOSS) return "上司" to Color(0xFFFFC107)
    if (type == RelationshipType.CAPTAIN) return "队长" to Color(0xFFFFC107)
    if (type == RelationshipType.SUBORDINATE) return "下属" to Color(0xFFFFB74D)
    if (type == RelationshipType.MEMBER) return "队员" to Color(0xFFFFB74D)
    if (type == RelationshipType.MENTOR) return "导师" to Color(0xFF8B5CF6)
    if (type == RelationshipType.STUDENT) return "学生" to Color(0xFF8B5CF6)
    if (type == RelationshipType.COMRADE) return "战友" to Color(0xFF9E9E9E)
    if (type == RelationshipType.TEAMMATE) return "队友" to Color(0xFF9E9E9E)
    if (type == RelationshipType.RIVAL) return "对手" to Color(0xFFEF4444)
    if (type == RelationshipType.CRUSH) return "暗恋对象" to Color(0xFFF48FB1)
    if (type == RelationshipType.LOVER) return "恋人" to Color(0xFFE91E63)
    return "陌生" to Color(0xFFBDBDBD)
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

private fun relationshipColor(type: RelationshipType): Color = relationshipLabel(type).second

@Composable
private fun RelationFilterBar(typeFilter: RelationshipType?, onFilter: (RelationshipType?) -> Unit) {
    LazyRow(modifier = Modifier.fillMaxWidth().background(Card).padding(horizontal = 12.dp, vertical = 8.dp)) {
        item { FilterChipText("全部", selected = typeFilter == null) { onFilter(null) } }
        items(RelationshipType.values().toList()) { type ->
            FilterChipText(RelationshipUiMapper.label(type), selected = typeFilter == type) { onFilter(type) }
        }
    }
}

@Composable
private fun FilterChipText(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = if (selected) Color.White else TextSecondary,
        modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) Primary else BG).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun RelationshipEditDialog(
    operator: OperatorEntity,
    rel: RelationshipEntity,
    operators: List<OperatorEntity>,
    onDismiss: () -> Unit,
    onSave: (RelationshipEntity, RelationshipEntity?) -> Unit
) {
    var selectedType by remember(rel.id) { mutableStateOf(rel.type) }
    var intimacy by remember(rel.id) { mutableStateOf(rel.intimacy.toFloat()) }
    var note by remember(rel.id) { mutableStateOf(rel.note) }
    var createReciprocal by remember(rel.id) { mutableStateOf(false) }
    val related = operators.find { it.id == rel.relatedOperatorId || it.name == rel.relatedOperatorName }
    val reciprocalType = complementType(selectedType)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑关系", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
        text = {
            Column {
                Text("${operator.name} -> ${rel.relatedOperatorName}", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                LazyRow { items(RelationshipType.values().toList()) { type -> FilterChipText(RelationshipUiMapper.label(type), selectedType == type) { selectedType = type } } }
                Spacer(Modifier.height(10.dp))
                Text("亲密度 ${intimacy.toInt()}", fontSize = 13.sp, color = TextPrimary)
                Slider(value = intimacy, onValueChange = { intimacy = it }, valueRange = 0f..100f)
                OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("备注") })
                if (related != null && reciprocalType != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("同时创建/更新互补关系", fontSize = 13.sp, color = TextPrimary)
                            Text("${related.name} -> ${operator.name}：${RelationshipUiMapper.label(reciprocalType)}。默认不自动创建。", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(checked = createReciprocal, onCheckedChange = { createReciprocal = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val updated = rel.copy(type = selectedType, intimacy = intimacy.toInt(), note = note, isPreset = false)
                val reciprocal = if (createReciprocal && related != null && reciprocalType != null) {
                    rel.copy(id = 0, operatorId = related.id, relatedOperatorId = operator.id, relatedOperatorName = operator.name, type = reciprocalType, intimacy = intimacy.toInt(), isPreset = false, note = note)
                } else null
                onSave(updated, reciprocal)
            }) { Text("保存", color = Primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) } }
    )
}

private fun complementType(type: RelationshipType): RelationshipType? = when (type) {
    RelationshipType.BIG_SISTER -> RelationshipType.LITTLE_SISTER
    RelationshipType.LITTLE_SISTER -> RelationshipType.BIG_SISTER
    RelationshipType.BIG_BROTHER -> RelationshipType.LITTLE_BROTHER
    RelationshipType.LITTLE_BROTHER -> RelationshipType.BIG_BROTHER
    RelationshipType.MOTHER, RelationshipType.FATHER -> RelationshipType.DAUGHTER
    RelationshipType.DAUGHTER -> RelationshipType.MOTHER
    RelationshipType.SON -> RelationshipType.FATHER
    RelationshipType.BOSS -> RelationshipType.SUBORDINATE
    RelationshipType.SUBORDINATE -> RelationshipType.BOSS
    RelationshipType.CAPTAIN -> RelationshipType.MEMBER
    RelationshipType.MEMBER -> RelationshipType.CAPTAIN
    RelationshipType.MENTOR -> RelationshipType.STUDENT
    RelationshipType.STUDENT -> RelationshipType.MENTOR
    RelationshipType.FRIEND, RelationshipType.CLOSE_FRIEND, RelationshipType.COMRADE,
    RelationshipType.TEAMMATE, RelationshipType.RIVAL, RelationshipType.LOVER,
    RelationshipType.FAMILY -> type
    RelationshipType.GUARDIAN -> RelationshipType.FAMILY
    RelationshipType.CRUSH -> null
    else -> null
}
