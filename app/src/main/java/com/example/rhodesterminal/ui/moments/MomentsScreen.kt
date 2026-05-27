package com.example.rhodesterminal.ui.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.rhodesterminal.data.db.entity.MomentCommentEntity
import com.example.rhodesterminal.data.db.entity.MomentEntity
import com.example.rhodesterminal.data.db.entity.MomentLikeEntity
import androidx.compose.ui.window.Dialog
import com.example.rhodesterminal.ui.theme.*
import com.example.rhodesterminal.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MomentsScreen(viewModel: MainViewModel, onBack: () -> Unit, onOperatorClick: (String) -> Unit = {}, onUnreadMessages: () -> Unit = {}, modifier: Modifier = Modifier) {
    val moments by viewModel.moments.collectAsState()
    var showPost by remember { mutableStateOf(false) }
    var batchGenerating by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var replyData by remember { mutableStateOf<Triple<Long, Long, String>?>(null) }
    var showReplyDialog by remember { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var commentMomentId by remember { mutableLongStateOf(0L) }
    val listState = rememberLazyListState()
    val prevCount = remember { mutableIntStateOf(0) }
    var unreadMsgCount by remember { mutableIntStateOf(viewModel.getUnreadCommentCount()) }
    LaunchedEffect(Unit) {
        while (true) {
            unreadMsgCount = viewModel.getUnreadCommentCount()
            delay(10_000)
        }
    }

    LaunchedEffect(moments.size) {
        if (moments.size > prevCount.intValue && moments.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
        prevCount.intValue = moments.size
    }

    Column(modifier = modifier.fillMaxSize().background(BG)) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("罗德岛动态", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f).padding(start = 4.dp))
            if (batchGenerating) Text(progressText.ifBlank { "生成中..." }, fontSize = 12.sp, color = AccentOrange)
            TextButton(onClick = { batchGenerating = true; viewModel.generateAllMoments { progressText = it; if (it == "全部完成") batchGenerating = false } }) { Text(if (batchGenerating) "..." else "一键催发", fontSize = 12.sp, color = Primary) }
            TextButton(onClick = onUnreadMessages) {
                Text(if (unreadMsgCount > 0) "未读消息 $unreadMsgCount" else "未读消息", fontSize = 12.sp, color = if (unreadMsgCount > 0) AccentOrange else TextSecondary, fontWeight = if (unreadMsgCount > 0) FontWeight.Bold else FontWeight.Normal)
            }
            IconButton(onClick = { showPost = true }) { Icon(Icons.Default.Create, "发动态", tint = Primary) }
        }
        HorizontalDivider(color = Divider)
        LazyColumn(state = listState) {
            if (moments.isEmpty()) { item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { Text("暂无动态\n点击右上角发布第一条", fontSize = 14.sp, color = TextTertiary, textAlign = TextAlign.Center) } } }
            items(moments, key = { it.id }) { moment ->
                MomentCardWithInteraction(moment = moment, viewModel = viewModel,
                    onReply = { commentId, name ->
                        replyData = Triple(moment.id, commentId, name)
                        showReplyDialog = true
                    },
                    onComment = {
                        commentMomentId = moment.id
                        showCommentDialog = true
                    },
                    onOperatorClick = onOperatorClick)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
    if (showPost) PostDialog(operators = viewModel.operators.collectAsState().value, onDismiss = { showPost = false }, onPost = { content, mentioned -> viewModel.postUserMoment(content, mentioned); showPost = false })

    // Reply dialog at screen level
    // Reply dialog - with key to prevent flash
    if (showReplyDialog) {
        val dialogKey = "${replyData?.first ?: 0}_${replyData?.second ?: 0}_${System.currentTimeMillis()}"
        val (momentId, parentId, parentName) = replyData ?: Triple(0L, 0L, "")
        var replyText by remember { mutableStateOf("") }
        LaunchedEffect(Unit) { /* stabilize initial composition */ }
        AlertDialog(onDismissRequest = { showReplyDialog = false }, title = { Text("回复 $parentName", color = TextPrimary) }, text = {
            OutlinedTextField(value = replyText, onValueChange = { replyText = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("@$parentName...") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Divider))
        }, confirmButton = {
            TextButton(onClick = {
                if (replyText.isBlank()) return@TextButton
                viewModel.commentOnMoment(momentId, "user", viewModel.getUserProfile().nickname, replyText, parentId, parentName)
                replyText = ""; showReplyDialog = false
            }) { Text("发送", color = Primary) }
        }, dismissButton = { TextButton(onClick = { showReplyDialog = false }) { Text("取消", color = TextSecondary) } })
    }

    // Comment dialog - at screen level
    if (showCommentDialog) {
        var commentText by remember { mutableStateOf("") }
        LaunchedEffect(Unit) { /* stabilize */ }
        AlertDialog(onDismissRequest = { showCommentDialog = false }, title = { Text("写评论", color = TextPrimary) }, text = {
            OutlinedTextField(value = commentText, onValueChange = { commentText = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("说点什么...", color = TextTertiary) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Divider))
        }, confirmButton = {
            TextButton(onClick = {
                if (commentText.isBlank()) return@TextButton
                viewModel.commentOnMoment(commentMomentId, "user", viewModel.getUserProfile().nickname, commentText, 0, "")
                commentText = ""; showCommentDialog = false
            }) { Text("发布", color = Primary) }
        }, dismissButton = { TextButton(onClick = { showCommentDialog = false }) { Text("取消", color = TextSecondary) } })
    }
}

@Composable
private fun MomentCardWithInteraction(moment: MomentEntity, viewModel: MainViewModel, onReply: (Long, String) -> Unit = { _, _ -> }, onComment: () -> Unit = {}, onOperatorClick: (String) -> Unit = {}) {
    val likes by viewModel.getLikes(moment.id).collectAsState(initial = emptyList())
    val comments by viewModel.getCommentsForMoment(moment.id).collectAsState(initial = emptyList())
    val (liked, setLiked) = remember { mutableStateOf(false) }

    val userProfile by viewModel.userProfile.collectAsState()
    val operatorList by viewModel.operators.collectAsState()
    val momentOp = remember(moment, operatorList) { operatorList.find { it.name == moment.operatorName || it.id == moment.operatorId } }
    Column(Modifier.fillMaxWidth().background(Surface).padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            if (moment.isUserPost && userProfile.avatarUri.isNotBlank()) {
                coil.compose.AsyncImage(model = userProfile.avatarUri, contentDescription = null, modifier = Modifier.size(44.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else if (!moment.isUserPost && !momentOp?.avatarUri.isNullOrBlank()) {
                AsyncImage(model = momentOp!!.avatarUri, contentDescription = null, modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onOperatorClick(moment.operatorName) }, contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.size(44.dp).clip(CircleShape).background(if (moment.isUserPost) Gray500 else Primary).clickable { onOperatorClick(moment.operatorName) }, contentAlignment = Alignment.Center) {
                    Text(if (moment.isUserPost) userProfile.nickname.take(1) else moment.operatorName.take(1), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(moment.operatorName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Primary, modifier = Modifier.clickable { onOperatorClick(moment.operatorName) })
                Spacer(Modifier.height(4.dp))
                Text(moment.content, fontSize = 15.sp, color = TextPrimary, lineHeight = 22.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(moment.createdAt), fontSize = 11.sp, color = TextTertiary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { setLiked(!liked); viewModel.likeMoment(moment.id, "user", userProfile.nickname) }.padding(4.dp)) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (liked) ErrorRed else TextTertiary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(2.dp)); Text("${moment.likeCount + if (liked) 1 else 0}", fontSize = 11.sp, color = if (liked) ErrorRed else TextTertiary) }
                        Spacer(Modifier.width(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onComment() }.padding(4.dp)) { Icon(Icons.Default.Message, null, tint = TextTertiary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(2.dp)); Text("${comments.size}", fontSize = 11.sp, color = TextTertiary) }
                    }
                }
                // 点赞者列表
                if (likes.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(SurfaceVariant).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        val likeNames = likes.take(3).joinToString("、") { it.operatorName }
                        Text(if (likes.size > 3) "${likeNames}等${likes.size}人点了赞" else "${likeNames}点了赞", fontSize = 12.sp, color = Primary)
                    }
                }
                // 评论列表
                if (comments.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(SurfaceVariant).padding(horizontal = 8.dp, vertical = 6.dp)) {
                        comments.forEachIndexed { idx, c ->
                            if (idx > 0) Spacer(Modifier.height(1.dp))
                            val isReply = c.parentCommentId > 0
                            val userName = userProfile.nickname
                            Row(
                                modifier = Modifier.padding(start = if (isReply && c.operatorName != userName) 20.dp else 0.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Primary)) { append(c.operatorName) }
                                    if (c.replyToName.isNotBlank()) {
                                        withStyle(SpanStyle(color = TextTertiary)) { append(" 回复 ") }
                                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Primary)) { append(c.replyToName) }
                                    }
                                    append("：${c.content}")
                                }, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f, fill = false))
                                if (!isReply) {
                                    Spacer(Modifier.width(4.dp))
                                    Text("回复", fontSize = 11.sp, color = Primary, modifier = Modifier.clickable { onReply(c.id, c.operatorName) }.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    HorizontalDivider(color = Divider, thickness = 6.dp)
}

@Composable private fun PostDialog(operators: List<com.example.rhodesterminal.data.db.entity.OperatorEntity>, onDismiss: () -> Unit, onPost: (String, List<String>) -> Unit) {
    var text by remember { mutableStateOf("") }; val atOps = remember { mutableStateListOf<String>() }; var showAtPicker by remember { mutableStateOf(false) }; val names = remember(operators) { operators.filter { it.name != "系统" }.map { it.name } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("发布动态", color = TextPrimary) }, text = {
        Column {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().height(100.dp), placeholder = { Text("想说点什么...", color = TextTertiary) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Divider))
            Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("艾特好友：", fontSize = 12.sp, color = TextSecondary); Spacer(Modifier.weight(1f)); TextButton(onClick = { showAtPicker = true }) { Text("选择干员", fontSize = 13.sp, color = Primary) } }
            if (atOps.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { atOps.forEach { Row(Modifier.clip(RoundedCornerShape(12.dp)).background(PrimaryContainer).clickable { atOps.remove(it) }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text("@$it", fontSize = 12.sp, color = Primary); Spacer(Modifier.width(4.dp)); Icon(Icons.Default.Close, null, tint = Primary, modifier = Modifier.size(12.dp)) } } } }
        }
    }, confirmButton = { TextButton(onClick = {
        if (text.isNotBlank()) {
            val atText = if (atOps.isNotEmpty()) " ${atOps.joinToString(" ") { "@$it" }}" else ""
            onPost(text + atText, atOps.toList())
        } else onDismiss()
    }) { Text("发布", color = if (text.isNotBlank()) Primary else TextTertiary) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) } })
    if (showAtPicker) AlertDialog(onDismissRequest = { showAtPicker = false }, title = { Text("@谁？", color = TextPrimary) }, text = { Column { names.forEach { val sel = atOps.contains(it); Row(Modifier.fillMaxWidth().clickable { if (sel) atOps.remove(it) else atOps.add(it) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(32.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) { Text(it.take(1), color = Color.White, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(8.dp)); Text(it, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f)); if (sel) Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(18.dp)) } } } }, confirmButton = { TextButton(onClick = { showAtPicker = false }) { Text("完成", color = Primary) } })
}

private fun formatTime(ts: Long): String { val diff = System.currentTimeMillis() - ts; return when { diff < 60_000 -> "刚刚"; diff < 3_600_000 -> "${diff/60_000}分钟前"; diff < 86_400_000 -> "${diff/3_600_000}小时前"; else -> "${diff/86_400_000}天前" } }
