package com.rhodes.privatechat.ui.gift

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rhodes.privatechat.ui.common.ImageCropperDialog
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.util.copyToCache
import com.rhodes.privatechat.util.copyToInternalStorageAsync
import com.rhodes.privatechat.viewmodel.MainViewModel
import kotlinx.coroutines.launch

data class GiftTarget(val id: String, val name: String)

@Composable
fun GiftDialog(
    viewModel: MainViewModel,
    targets: List<GiftTarget>,
    price: Int = 100,
    onDismiss: () -> Unit,
    onSend: (String, String, List<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = viewModel.settings
    val balance by settings.lmbFlow.collectAsState(initial = settings.lmb)
    var selectedIds by remember { mutableStateOf(targets.take(1).map { it.id }.toSet()) }
    var giftName by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf("") }
    var cropTarget by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            cropTarget = try { copyToCache(context, uri) } catch (_: Exception) { null }
            if (cropTarget == null) Toast.makeText(context, "无法读取图片，请选择JPG或PNG图片", Toast.LENGTH_SHORT).show()
        }
    }
    val total = selectedIds.size * price
    val validName = giftName.trim().length in 1..10
    val canConfirm = imageUri.isNotBlank() && validName && selectedIds.isNotEmpty() && balance >= total

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ElevatedSurface,
        title = { Text("送礼", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("余额：$balance 龙门币 · 总价：$total 龙门币", color = if (balance >= total) AccentOrange else ErrorRed, fontSize = 13.sp)
                if (targets.size > 1) {
                    Text("选择收礼成员", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(targets) { target ->
                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                selectedIds = if (target.id in selectedIds) selectedIds - target.id else selectedIds + target.id
                            }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = target.id in selectedIds, onCheckedChange = { checked -> selectedIds = if (checked) selectedIds + target.id else selectedIds - target.id })
                                Text(target.name, color = TextPrimary)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (imageUri.isBlank()) "选择并裁剪礼物图片" else "重新选择礼物图片") }
                if (imageUri.isNotBlank()) AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.size(92.dp).clip(RoundedCornerShape(10.dp)).align(Alignment.CenterHorizontally), contentScale = ContentScale.Crop)
                OutlinedTextField(value = giftName, onValueChange = { giftName = it.take(10) }, label = { Text("礼物名字（1-10个字符）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (balance < total) Text("余额不足", color = ErrorRed, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(enabled = canConfirm, onClick = {
            onSend(imageUri, giftName.trim(), selectedIds.toList()); onDismiss()
        }) { Text("送出", color = if (canConfirm) Primary else TextTertiary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) } }
    )
    cropTarget?.let { uri ->
        ImageCropperDialog(imageUri = uri, aspectX = 1f, aspectY = 1f,
            onConfirm = { cropped ->
                scope.launch {
                    try { imageUri = copyToInternalStorageAsync(context, cropped) }
                    catch (_: Exception) { Toast.makeText(context, "保存礼物图片失败", Toast.LENGTH_SHORT).show() }
                    cropTarget = null
                }
            }, onCancel = { cropTarget = null })
    }
}
