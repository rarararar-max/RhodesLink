package com.rhodes.privatechat.ui.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import com.rhodes.privatechat.data.db.entity.MomentCommentEntity
import com.rhodes.privatechat.data.db.entity.MomentEntity
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MomentDetailScreen(
    viewModel: MainViewModel,
    momentId: Long,
    replyToCommentId: Long = 0,
    replyToName: String = "",
    onBack: () -> Unit,
    onOperatorClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val moments by viewModel.moments.collectAsState()
    val moment = remember(moments) { moments.find { it.id == momentId } }
    val likes by viewModel.getLikes(momentId).collectAsState(initial = emptyList())
    val comments by viewModel.getCommentsForMoment(momentId).collectAsState(initial = emptyList())
    var inputText by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf(Triple(0L, "", "")) }
    val profile by viewModel.userProfile.collectAsState()
    val userName = profile.nickname

    // 初始回复目标
    LaunchedEffect(replyToCommentId, replyToName) {
        if (replyToCommentId > 0 && replyToName.isNotBlank()) {
            replyTarget = Triple(replyToCommentId, replyToName, "")
        }
    }

    if (moment == null) {
        Box(Modifier.fillMaxSize().background(BG), contentAlignment = Alignment.Center) { Text("动态已删除", color = TextTertiary) }
        return
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Text("动态详情", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)

        val operators by viewModel.operators.collectAsState()
        val momentOp = remember(moment, operators) { operators.find { it.id == moment.operatorId || it.name == moment.operatorName } }
        LazyColumn(Modifier.weight(1f)) {
            item {
                MomentDetailCard(moment, likes, comments, userName, onOperatorClick,
                    operatorAvatarUri = if (moment.isUserPost && viewModel.getUserProfile().avatarUri.isNotBlank())
                        viewModel.getUserProfile().avatarUri
                    else
                        momentOp?.avatarUri ?: "") { commentId, name ->
                    replyTarget = if (replyTarget.second == name) Triple(0L, "", "") else Triple(commentId, name, "")
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // 底部输入栏
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (replyTarget.second.isNotBlank()) {
                    Text("回复 ${replyTarget.second}", fontSize = 11.sp, color = Primary, modifier = Modifier.padding(start = 12.dp, bottom = 2.dp))
                }
                OutlinedTextField(value = inputText, onValueChange = { inputText = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(if (replyTarget.second.isNotBlank()) "@${replyTarget.second}..." else "说点什么...", color = TextTertiary) },
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Divider, unfocusedBorderColor = Divider))
            }
            Spacer(Modifier.width(4.dp))
            if (replyTarget.second.isNotBlank()) {
                IconButton(onClick = { replyTarget = Triple(0L, "", ""); inputText = "" }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "取消回复", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = {
                if (inputText.isBlank()) return@IconButton
                val (pid, pName, _) = replyTarget
                viewModel.commentOnMoment(momentId, "user", userName, inputText, pid, pName)
                inputText = ""; replyTarget = Triple(0L, "", "")
            }) { Icon(Icons.AutoMirrored.Filled.Send, "发送", tint = if (inputText.isNotBlank()) Primary else TextSecondary) }
        }
    }
    }
}

@Composable
private fun MomentDetailCard(
    moment: MomentEntity,
    likes: List<*>,
    comments: List<MomentCommentEntity>,
    userName: String,
    onOperatorClick: (String) -> Unit,
    operatorAvatarUri: String = "",
    onReply: (Long, String) -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            OperatorAvatarImage(avatarUri = operatorAvatarUri, name = moment.operatorName, modifier = Modifier.size(44.dp).clickable { onOperatorClick(moment.operatorName) })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(moment.operatorName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Primary)
                Spacer(Modifier.height(4.dp))
                Text(moment.content, fontSize = 15.sp, color = TextPrimary, lineHeight = 22.sp)
                Spacer(Modifier.height(6.dp))
                Text(SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") }.format(Date(moment.createdAt)), fontSize = 11.sp, color = TextTertiary)
            }
        }
        if (likes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${likes.size}人赞了", fontSize = 12.sp, color = TextSecondary)
            }
        }
        if (comments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(SurfaceVariant).padding(horizontal = 8.dp, vertical = 6.dp)) {
                val topComments = comments.filter { it.parentCommentId == 0L }
                topComments.forEach { top ->
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
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
