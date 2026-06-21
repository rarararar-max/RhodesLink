package com.rhodes.privatechat.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.util.copyToCache
import com.rhodes.privatechat.util.decodeSampledBitmap
import com.rhodes.privatechat.util.scaleBitmapToMax
import java.io.File
import java.io.FileOutputStream

@Composable
fun ImageCropperDialog(
    imageUri: Uri,
    aspectX: Float,
    aspectY: Float,
    onConfirm: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var showUseOriginal by remember { mutableStateOf(false) }
    val bitmap = remember(imageUri) { decodeSampledBitmap(context, imageUri, 1024) }

    fun confirmOriginal() {
        val safeUri = copyToCache(context, imageUri) ?: imageUri
        onConfirm(safeUri)
        showUseOriginal = false
    }

    Dialog(onDismissRequest = onCancel) {
        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1C1C1E)).padding(8.dp)) {
            if (bitmap == null) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("加载图片失败", color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
                    androidx.compose.foundation.layout.Row {
                        TextButton(onClick = onCancel) { Text("取消", color = TextSecondary) }
                        TextButton(onClick = { confirmOriginal() }) { Text("直接使用原图", color = Primary) }
                    }
                }
                return@Dialog
            }
            val bmp = bitmap
            var viewSize by remember { mutableStateOf(IntSize.Zero) }
            Box(
                modifier = Modifier.fillMaxWidth().height(450.dp).background(Color.Black)
                    .onSizeChanged { viewSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            panX += pan.x
                            panY += pan.y
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val vw = viewSize.width.toFloat()
                    val vh = viewSize.height.toFloat()
                    if (vw <= 0 || vh <= 0) return@Canvas
                    val cropH = if (aspectY > 0) vw * aspectY / aspectX else vh
                    val cropTop = (vh - cropH) / 2f
                    val drawW = bmp.width * scale
                    val drawH = bmp.height * scale
                    val drawLeft = (vw - drawW) / 2f + panX
                    val drawTop = (vh - drawH) / 2f + panY
                    clipRect(0f, cropTop, vw, cropTop + cropH) {
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawBitmap(bmp, null,
                                android.graphics.RectF(drawLeft, drawTop, drawLeft + drawW, drawTop + drawH), null)
                        }
                    }
                    drawRect(Color(0x99000000), Offset.Zero, Size(vw, cropTop))
                    drawRect(Color(0x99000000), Offset(0f, cropTop + cropH), Size(vw, vh - cropTop - cropH))
                }
            }
            Button(
                onClick = {
                    val vw = viewSize.width.toFloat()
                    val vh = viewSize.height.toFloat()
                    if (vw <= 0 || vh <= 0) {
                        Toast.makeText(context, "图片尚未加载完成，请稍后再试", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val cropH = if (aspectY > 0) vw * aspectY / aspectX else vh
                    val cropTop = (vh - cropH) / 2f
                    val drawW = bmp.width * scale
                    val drawH = bmp.height * scale
                    val drawLeft = (vw - drawW) / 2f + panX
                    val drawTop = (vh - drawH) / 2f + panY
                    val srcLeft = ((0f - drawLeft) / scale).coerceIn(0f, bmp.width.toFloat())
                    val srcTop = ((cropTop - drawTop) / scale).coerceIn(0f, bmp.height.toFloat())
                    val srcW = (vw / scale).coerceIn(1f, bmp.width - srcLeft)
                    val srcH = (cropH / scale).coerceIn(1f, bmp.height - srcTop)
                    if (srcW > 0 && srcH > 0) {
                        try {
                            val cropX = srcLeft.toInt().coerceIn(0, bmp.width)
                            val cropY = srcTop.toInt().coerceIn(0, bmp.height)
                            val cropW = srcW.toInt().coerceAtMost(bmp.width - cropX)
                            val cropH = srcH.toInt().coerceAtMost(bmp.height - cropY)
                            if (cropW <= 0 || cropH <= 0) {
                                com.rhodes.privatechat.util.DebugLogger.log("Crop/WARN", "裁剪区域无效: $cropW x $cropH")
                                showUseOriginal = true
                                return@Button
                            }
                            val cropped = Bitmap.createBitmap(bmp, cropX, cropY, cropW, cropH)
                            val output = scaleBitmapToMax(cropped, 1024)
                            val destFile = File(context.cacheDir, "crop_${System.currentTimeMillis()}.jpg")
                            FileOutputStream(destFile).use { out -> output.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                            if (output !== cropped) output.recycle()
                            cropped.recycle()
                            onConfirm(Uri.fromFile(destFile))
                        } catch (_: OutOfMemoryError) {
                            com.rhodes.privatechat.util.DebugLogger.log("Crop/ERROR", "裁剪内存不足")
                            showUseOriginal = true
                        } catch (_: Exception) {
                            com.rhodes.privatechat.util.DebugLogger.log("Crop/ERROR", "裁剪失败")
                            showUseOriginal = true
                        }
                    } else {
                        Toast.makeText(context, "裁剪区域无效，请调整图片位置", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B8DEF))
            ) { Text("确认裁剪", fontSize = 16.sp) }
        }
    }
    if (showUseOriginal) {
        AlertDialog(
            onDismissRequest = { showUseOriginal = false },
            title = { Text("裁剪失败", color = TextPrimary) },
            text = { Text("无法裁剪此图片，是否直接使用原图？", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmOriginal() }) {
                    Text("直接使用", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUseOriginal = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }
}
