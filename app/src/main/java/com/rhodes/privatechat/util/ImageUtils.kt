package com.rhodes.privatechat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

fun copyToInternalStorage(context: Context, uri: Uri): String {
    try {
        val dir = File(context.filesDir, "images")
        if (!dir.exists()) dir.mkdirs()
        val fileName = "img_${System.currentTimeMillis()}_${uri.hashCode()}.jpg"
        val dest = File(dir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return Uri.fromFile(dest).toString()
    } catch (_: Exception) {
        return uri.toString()
    }
}

fun copyToCache(context: Context, uri: Uri): Uri? {
    return try {
        val dir = File(context.cacheDir, "picked_images")
        if (!dir.exists()) dir.mkdirs()
        val dest = File(dir, "picked_${System.currentTimeMillis()}_${uri.hashCode()}.img")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        } ?: return null
        Uri.fromFile(dest)
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Exception) {
        null
    }
}

/** 加载并缩放 bitmap 到合适尺寸 */
fun decodeSampledBitmap(context: Context, uri: Uri, maxDim: Int = 1024): Bitmap? {
    return try {
        // 先读取尺寸
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val (w, h) = opts.outWidth to opts.outHeight
        if (w <= 0 || h <= 0) return null
        // 计算采样率
        val sample = maxOf(1, maxOf(w, h) / maxDim)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOpts)
        }
    } catch (_: OutOfMemoryError) { null } catch (_: Exception) { null }
}

fun scaleBitmapToMax(bitmap: Bitmap, maxDim: Int): Bitmap {
    val maxSide = maxOf(bitmap.width, bitmap.height)
    if (maxSide <= maxDim) return bitmap
    val scale = maxDim.toFloat() / maxSide.toFloat()
    val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, w, h, true)
}
