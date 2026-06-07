package com.rhodes.privatechat.ui.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animate
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
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.rhodes.privatechat.data.db.entity.MomentCommentEntity
import com.rhodes.privatechat.data.db.entity.MomentEntity
import com.rhodes.privatechat.data.db.entity.MomentLikeEntity
import androidx.compose.ui.window.Dialog
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MomentsScreen(viewModel: MainViewModel, onBack: () -> Unit, onOperatorClick: (String) -> Unit = {}, onUnreadMessages: () -> Unit = {}, modifier: Modifier = Modifier) {
    val moments by viewModel.moments.collectAsState()
    var showPost by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var isForceGenerating by remember { mutableStateOf(false) }
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

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Text("罗德岛动态", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f).padding(start = 4.dp))
            TextButton(onClick = onUnreadMessages) {
                Text(if (unreadMsgCount > 0) "未读消息 $unreadMsgCount" else "未读消息", fontSize = 12.sp, color = if (unreadMsgCount > 0) AccentOrange else TextSecondary, fontWeight = if (unreadMsgCount > 0) FontWeight.Bold else FontWeight.Normal)
            }
            if (!isForceGenerating) {
                TextButton(onClick = {
                    isForceGenerating = true
                    viewModel.forceGenerateMoments { if (it == "全部完成") isForceGenerating = false }
                }) { Text("催发", fontSize = 12.sp, color = AccentOrange, fontWeight = FontWeight.SemiBold) }
            } else {
                Text("催发中...", fontSize = 12.sp, color = AccentOrange, modifier = Modifier.padding(horizontal = 12.dp))
            }
            IconButton(onClick = { showPost = true }) { Icon(Icons.Default.Create, "发动态", tint = Primary) }
        }
        HorizontalDivider(color = Divider)
        PullToRefreshOverscroll(
            pullToRefresh = isGenerating,
            listState = listState,
            onRefresh = {
                if (!isGenerating) {
                    isGenerating = true
                    viewModel.generateOneMoment { progress ->
                        if (progress == "全部完成") isGenerating = false
                    }
                }
            }
        ) {
            LazyColumn(state = listState) {
                if (moments.isEmpty()) { item { Box(Modifier.fillMaxWidth().fillParentMaxHeight(), contentAlignment = Alignment.Center) { Text("暂无动态\n下拉刷新生成动态", fontSize = 14.sp, color = TextTertiary, textAlign = TextAlign.Center) } } }
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
    val userProfile by viewModel.userProfile.collectAsState()
    val liked = likes.any { it.operatorName == userProfile.nickname }
    val operatorList by viewModel.operators.collectAsState()
    val momentOp = remember(moment, operatorList) { operatorList.find { it.name == moment.operatorName || it.id == moment.operatorId } }
    Column(Modifier.fillMaxWidth().background(Surface).padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            if (moment.isUserPost && userProfile.avatarUri.isNotBlank()) {
                coil3.compose.AsyncImage(model = userProfile.avatarUri, contentDescription = null, modifier = Modifier.size(44.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.likeMoment(moment.id, "user", userProfile.nickname) }.padding(4.dp)) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (liked) ErrorRed else TextTertiary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(2.dp)); Text("${moment.likeCount}", fontSize = 11.sp, color = if (liked) ErrorRed else TextTertiary) }
                        Spacer(Modifier.width(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onComment() }.padding(4.dp)) { Icon(Icons.AutoMirrored.Filled.Message, null, tint = TextTertiary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(2.dp)); Text("${comments.size}", fontSize = 11.sp, color = TextTertiary) }
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
                // 评论列表（嵌套：一级→二级）
                if (comments.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(SurfaceVariant).padding(horizontal = 8.dp, vertical = 6.dp)) {
                        val topComments = comments.filter { it.parentCommentId == 0L }
                        topComments.forEachIndexed { idx, top ->
                            if (idx > 0) Spacer(Modifier.height(4.dp))
                            // 一级评论
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Primary)) { append(top.operatorName) }
                                    append("：${top.content}")
                                }, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f, fill = false))
                                Spacer(Modifier.width(4.dp))
                                Text("回复", fontSize = 11.sp, color = Primary, modifier = Modifier.clickable { onReply(top.id, top.operatorName) }.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                            // 该一级下的二级评论
                            val replies = comments.filter { it.parentCommentId == top.id }
                            replies.forEach { reply ->
                                Spacer(Modifier.height(2.dp))
                                Row(modifier = Modifier.padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Primary)) { append(reply.operatorName) }
                                        if (reply.replyToName.isNotBlank()) {
                                            withStyle(SpanStyle(color = TextTertiary)) { append(" 回复 ") }
                                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Primary)) { append(reply.replyToName) }
                                        }
                                        append("：${reply.content}")
                                    }, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f, fill = false))
                                    Spacer(Modifier.width(4.dp))
                                    Text("回复", fontSize = 11.sp, color = Primary, modifier = Modifier.clickable { onReply(reply.id, reply.operatorName) }.padding(horizontal = 4.dp, vertical = 2.dp))
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

@Composable private fun PostDialog(operators: List<com.rhodes.privatechat.data.db.entity.OperatorEntity>, onDismiss: () -> Unit, onPost: (String, List<String>) -> Unit) {
    var text by remember { mutableStateOf("") }; var showAtPicker by remember { mutableStateOf(false) }; val names = remember(operators) { operators.filter { it.name != "系统" }.map { it.name } }
    // 从文本中解析已被 @ 的干员
    val mentionedInText = remember(text) { names.filter { text.contains("@$it") } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("发布动态", color = TextPrimary) }, text = {
        Column {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().height(100.dp), placeholder = { Text("用 @+干员名 艾特干员，如：@能天使", color = TextTertiary) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Divider))
            Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("艾特好友：", fontSize = 12.sp, color = TextSecondary); Spacer(Modifier.weight(1f)); TextButton(onClick = { showAtPicker = true }) { Text("选择干员", fontSize = 13.sp, color = Primary) } }
            if (mentionedInText.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("已艾特：${mentionedInText.joinToString("、") { "@$it" }}", fontSize = 11.sp, color = Primary)
            }
        }
    }, confirmButton = { TextButton(onClick = {
        if (text.isNotBlank()) {
            val mentioned = names.filter { text.contains("@$it") }
            onPost(text, mentioned)
        } else onDismiss()
    }) { Text("发布", color = if (text.isNotBlank()) Primary else TextTertiary) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) } })
    if (showAtPicker) AlertDialog(onDismissRequest = { showAtPicker = false }, title = { Text("@谁？", color = TextPrimary) }, text = { Column { names.forEach { val sel = text.contains("@$it"); Row(Modifier.fillMaxWidth().clickable { if (!sel) text = "$text@$it "; showAtPicker = false }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(32.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) { Text(it.take(1), color = Color.White, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(8.dp)); Text(it, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f)); if (sel) Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(18.dp)) } } } }, confirmButton = { TextButton(onClick = { showAtPicker = false }) { Text("完成", color = Primary) } })
}

@Composable
private fun PullToRefreshOverscroll(
    pullToRefresh: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState? = null,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    val indicatorHeight = 80f
    val pullThreshold = 120f
    var offsetValue by remember { mutableStateOf(0f) }
    var isOverscrolling by remember { mutableStateOf(false) }
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    val latestPullToRefresh by rememberUpdatedState(pullToRefresh)
    val showIndicator = offsetValue > 0f || pullToRefresh

    LaunchedEffect(pullToRefresh) {
        val target = if (pullToRefresh) indicatorHeight else 0f
        animate(offsetValue, target) { value, _ -> offsetValue = value }
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 滚动上升时：收回指示器
                if (offsetValue > 0f && available.y < 0f) {
                    val consumed = available.y.coerceAtLeast(-offsetValue)
                    offsetValue += consumed
                    if (offsetValue <= 0f) {
                        offsetValue = 0f
                        isOverscrolling = false
                    }
                    return Offset(0f, consumed)
                }
                // 列表在顶部且下拉时：在 onPreScroll 中拦截，防止 overscroll effect 吞掉事件
                if (available.y > 0f && listState != null) {
                    val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                    if (atTop || offsetValue > 0f) {
                        isOverscrolling = true
                        offsetValue = (offsetValue + available.y).coerceIn(0f, 300f)
                        return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f) {
                    isOverscrolling = true
                    offsetValue = (offsetValue + available.y).coerceIn(0f, 300f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetValue >= pullThreshold) {
                    offsetValue = indicatorHeight
                    latestOnRefresh()
                    return available
                } else if (!latestPullToRefresh) {
                    offsetValue = 0f
                }
                isOverscrolling = false
                return Velocity.Zero
            }
        }
    }

    Box {
        if (showIndicator) {
            Box(
                Modifier.fillMaxWidth().height(indicatorHeight.toInt().dp).background(Surface),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                    Spacer(Modifier.width(8.dp))
                    Text("正在生成动态...", fontSize = 13.sp, color = TextSecondary)
                }
            }
        }
        Box(
            Modifier.offset(y = offsetValue.dp).nestedScroll(connection)
        ) {
            content()
        }
    }
}

private fun formatTime(ts: Long): String { val diff = System.currentTimeMillis() - ts; return when { diff < 60_000 -> "刚刚"; diff < 3_600_000 -> "${diff/60_000}分钟前"; diff < 86_400_000 -> "${diff/3_600_000}小时前"; else -> "${diff/86_400_000}天前" } }
