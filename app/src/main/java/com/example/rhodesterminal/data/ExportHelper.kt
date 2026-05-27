package com.example.rhodesterminal.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

object ExportHelper {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun exportToFile(context: Context, payload: ExportPayload, fileName: String): File {
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(gson.toJson(payload))
        return file
    }

    fun importFromFile(file: File): ExportPayload? {
        return try {
            val json = file.readText()
            gson.fromJson(json, ExportPayload::class.java)
        } catch (_: Exception) { null }
    }

    fun importFromString(json: String): ExportPayload? {
        return try { gson.fromJson(json, ExportPayload::class.java) } catch (_: Exception) { null }
    }

    fun toJson(payload: ExportPayload): String = gson.toJson(payload)

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
