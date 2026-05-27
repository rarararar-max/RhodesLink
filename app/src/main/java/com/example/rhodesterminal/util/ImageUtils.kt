package com.example.rhodesterminal.util

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

/** 加载并缩放 bitmap 到合适尺寸 */
fun decodeSampledBitmap(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? {
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
    } catch (_: Exception) { null }
}
