package com.rhodes.privatechat.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object ExportHelper {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun exportToFile(context: Context, payload: ExportPayload, fileName: String): File {
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(json.encodeToString(payload))
        return file
    }

    fun importFromFile(file: File): ExportPayload? {
        return try {
            val text = file.readText()
            json.decodeFromString<ExportPayload>(text)
        } catch (_: Exception) { null }
    }

    fun importFromString(str: String): ExportPayload? {
        return try { json.decodeFromString<ExportPayload>(str) } catch (_: Exception) { null }
    }

    fun toJson(payload: ExportPayload): String = json.encodeToString(payload)

    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享导出文件"))
    }
}
