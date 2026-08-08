package com.rhodes.privatechat.ui.contacts

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rhodes.privatechat.data.db.entity.ChatSessionEntity
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import com.rhodes.privatechat.ui.common.WechatIconTile
import com.rhodes.privatechat.ui.common.WechatListGroup
import com.rhodes.privatechat.ui.common.WechatTopBar
import com.rhodes.privatechat.ui.common.softTextFieldColors
import com.rhodes.privatechat.data.db.entity.OperatorEntity
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.MainViewModel

@Composable
fun ContactsScreen(viewModel: MainViewModel, onOperatorClick: (OperatorEntity) -> Unit, onNewOperator: () -> Unit = {}, onNewGroup: () -> Unit = {}, onGroupClick: (String, String) -> Unit = { _, _ -> }, modifier: Modifier = Modifier) {
    val operators by viewModel.operators.collectAsState()
    val allSessions by viewModel.allSessions.collectAsState()
    LaunchedEffect(Unit) {
        // This is a visible recovery point: do not leave the contacts screen empty while a
        // background state flow is unavailable after an in-place upgrade.
        if (operators.isEmpty() || allSessions.isEmpty()) viewModel.ensureAppStateLoaded("contacts_open")
    }
    val groups = allSessions.filter { it.operatorId.startsWith("group_") }
    var searchText by remember { mutableStateOf("") }
    val filteredGroups = if (searchText.isBlank()) groups else groups.filter { it.operatorName.contains(searchText, ignoreCase = true) }
    val filtered = if (searchText.isBlank()) operators else operators.filter { it.name.contains(searchText, ignoreCase = true) || it.title.contains(searchText, ignoreCase = true) }

    Column(modifier = modifier.fillMaxSize().background(BG)) {
        WechatTopBar("通讯录")
        SearchBar(text = searchText, onTextChange = { searchText = it })
        LazyColumn {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                WechatListGroup {
                    ContactActionItem(Icons.Default.PersonAdd, "新建干员", Color(0xFF07C160), onNewOperator, showDivider = true)
                    ContactActionItem(Icons.Default.Groups, "新建群聊", Color(0xFF1989FA), onNewGroup, showDivider = false)
                }
            }
            item { SectionHeader("群聊", filteredGroups.size) }
            if (filteredGroups.isEmpty()) {
                item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Text(if (searchText.isBlank()) "暂无群聊" else "没有匹配的群聊", fontSize = 13.sp, color = TextTertiary) } }
            } else {
                items(filteredGroups, key = { it.id }) { g -> GroupItem(g) { onGroupClick(g.operatorName, g.id) } }
            }
            item { Spacer(modifier = Modifier.height(8.dp)); SectionHeader("干员", filtered.size) }
            items(filtered, key = { it.id }) { op -> OperatorItem(op) { onOperatorClick(op) } }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable private fun SearchBar(text: String, onTextChange: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = text, onValueChange = onTextChange, modifier = Modifier.fillMaxWidth(), placeholder = { Text("搜索干员或群聊...", fontSize = 14.sp, color = TextTertiary) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(18.dp)) }, trailingIcon = { if (text.isNotBlank()) IconButton(onClick = { onTextChange("") }, modifier = Modifier.size(18.dp)) { Icon(Icons.Default.Clear, "清除", tint = TextTertiary, modifier = Modifier.size(14.dp)) } }, shape = RoundedCornerShape(8.dp), singleLine = true, colors = softTextFieldColors(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { }))
    }
}

@Composable private fun ContactActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, color: Color, onClick: () -> Unit, showDivider: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().background(Surface).clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            WechatIconTile(icon, color)
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, fontSize = 16.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        }
        if (showDivider) HorizontalDivider(color = Divider.copy(alpha = 0.45f), modifier = Modifier.padding(start = 66.dp))
    }
}

@Composable private fun SectionHeader(title: String, count: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary); Text(" ($count)", fontSize = 12.sp, color = TextTertiary) }
}

@Composable private fun OperatorItem(op: OperatorEntity, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(Surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        OperatorAvatarImage(avatarUri = op.avatarUri, name = op.name, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) { Text(op.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary); Text(op.title, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    HorizontalDivider(color = Divider.copy(alpha = 0.45f), modifier = Modifier.padding(start = 66.dp))
}

@Composable private fun GroupItem(session: ChatSessionEntity, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(Surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        if (session.avatarUri.isNotBlank()) {
            AsyncImage(model = session.avatarUri, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) { Icon(Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column { Row(verticalAlignment = Alignment.CenterVertically) { Text(session.operatorName, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary); Spacer(modifier = Modifier.width(4.dp)); Text("群", fontSize = 9.sp, color = Primary, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Primary.copy(alpha = 0.1f)).padding(horizontal = 4.dp, vertical = 1.dp)) } }
    }
    HorizontalDivider(color = Divider.copy(alpha = 0.45f), modifier = Modifier.padding(start = 66.dp))
}
