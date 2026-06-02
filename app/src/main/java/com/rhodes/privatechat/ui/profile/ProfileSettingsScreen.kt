package com.rhodes.privatechat.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.MainViewModel
import androidx.compose.material3.ButtonDefaults

private val presetAvatars = listOf(
    Color(0xFF5B8DEF), Color(0xFF34C759), Color(0xFFFF9500),
    Color(0xFFFF3B30), Color(0xFFAF52DE)
)

@Composable
fun ProfileSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = remember { viewModel.getUserProfile() }
    var nickname by remember { mutableStateOf(profile.nickname) }
    var gender by remember { mutableStateOf(profile.gender) }
    var bio by remember { mutableStateOf(profile.bio) }
    val existingAvatar = viewModel.getUserProfile().avatarUri.ifBlank { null }
    val context = LocalContext.current
    var avatarUri by remember { mutableStateOf(existingAvatar) }
    var cropTarget by remember { mutableStateOf<android.net.Uri?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> cropTarget = uri }
    var avatarIndex by remember { mutableIntStateOf(0) }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding().imePadding()) {
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.saveUserProfile(nickname, gender, bio, avatarUri ?: existingAvatar ?: ""); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Text("身份设置", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)

        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card {
                if (avatarUri != null) {
                    AsyncImage(model = avatarUri, contentDescription = null, modifier = Modifier.size(100.dp).clip(CircleShape).border(3.dp, Blue400, CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(presetAvatars[avatarIndex]).border(3.dp, Blue400, CircleShape), contentAlignment = Alignment.Center) {
                        Text(nickname.take(1), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { avatarPicker.launch("image/*") }) {
                    Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("上传头像", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            AvatarPicker(avatarIndex = avatarIndex, onSelect = { avatarIndex = it })
            Spacer(modifier = Modifier.height(16.dp))

            Card {
                LabelField("昵称") {
                    OutlinedTextField(value = nickname, onValueChange = { nickname = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp), colors = fieldColors())
                }
                Spacer(modifier = Modifier.height(12.dp))
                LabelField("性别") {
                    OutlinedTextField(value = gender, onValueChange = { gender = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp), colors = fieldColors(), placeholder = { Text("选填", color = TextTertiary, fontSize = 14.sp) })
                }
                Spacer(modifier = Modifier.height(12.dp))
                LabelField("个人简介") {
                    OutlinedTextField(value = bio, onValueChange = { bio = it }, modifier = Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                }
            }
        }
    }
    }
    cropTarget?.let { uri ->
        com.rhodes.privatechat.ui.common.ImageCropperDialog(
            imageUri = uri, aspectX = 1f, aspectY = 1f,
            onConfirm = { cropped -> avatarUri = com.rhodes.privatechat.util.copyToInternalStorage(context, cropped); cropTarget = null },
            onCancel = { cropTarget = null }
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
}

@Composable
private fun AvatarPicker(avatarIndex: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (avatarIndex > 0) onSelect(avatarIndex - 1) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ChevronLeft, null, tint = Blue400) }
        presetAvatars.forEachIndexed { i, color ->
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(color).border(if (i == avatarIndex) 3.dp else 0.dp, if (i == avatarIndex) Blue400 else Color.Transparent, CircleShape).clickable { onSelect(i) })
        }
        IconButton(onClick = { if (avatarIndex < presetAvatars.size - 1) onSelect(avatarIndex + 1) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ChevronRight, null, tint = Blue400) }
    }
}

@Composable
private fun LabelField(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
        content()
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Blue400, unfocusedBorderColor = Divider)
