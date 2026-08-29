package com.rhodes.privatechat.data.backup

object BackupRestorePayloadRewriter {
    fun rewriteMediaUris(payload: BackupPayload, mediaFiles: Map<String, java.io.File>): BackupPayload {
        val byOriginalUri = payload.media.mapNotNull { item ->
            item.originalUri.takeIf { it.isNotBlank() }?.let { original ->
                mediaFiles[item.mediaId]?.let { original to it.toURI().toString() }
            }
        }.toMap()
        val byCanonicalFileUri = byOriginalUri.mapNotNull { (original, restored) ->
            runCatching { java.io.File(java.net.URI(original)).canonicalFile.toURI().toString() to restored }.getOrNull()
        }.toMap()
        val byAndroidPrivateFile = byOriginalUri.mapNotNull { (original, restored) ->
            androidPrivateFilesKey(original)?.let { it to restored }
        }.toMap()
        fun uri(value: String): String = byOriginalUri[value]
            ?: runCatching { byCanonicalFileUri[java.io.File(java.net.URI(value)).canonicalFile.toURI().toString()] }.getOrNull()
            ?: androidPrivateFilesKey(value)?.let(byAndroidPrivateFile::get)
            ?: value
        fun content(value: String): String = byOriginalUri.entries.fold(value) { result, entry -> result.replace(entry.key, entry.value) }
        return payload.copy(
            content = payload.content.copy(
                operators = payload.content.operators?.map { it.copy(avatarUri = uri(it.avatarUri)) },
                sessions = payload.content.sessions?.map { it.copy(avatarUri = uri(it.avatarUri)) },
                messages = payload.content.messages?.map { it.copy(content = content(it.content)) },
                settings = payload.content.settings?.toMutableMap()?.apply {
                    this["user_avatar_uri"] = this["user_avatar_uri"]?.let { value ->
                        if (value.startsWith("s:")) "s:${uri(value.removePrefix("s:"))}" else uri(value)
                    }.orEmpty()
                    this["support_conversation"] = this["support_conversation"]?.let { value ->
                        val prefix = if (value.startsWith("s:")) "s:" else ""
                        prefix + content(value.removePrefix("s:"))
                    }.orEmpty()
                },
            ),
            giftRecords = payload.giftRecords.map { it.copy(imageUri = uri(it.imageUri)) },
            chatArchives = payload.chatArchives.map { archive ->
                archive.copy(messagesJson = content(archive.messagesJson), stateJson = content(archive.stateJson))
            },
            chatHistorySegments = payload.chatHistorySegments.map { history -> history.copy(messagesJson = content(history.messagesJson)) },
        )
    }

    /**
     * Android exposes one app-private files directory through both /data/data and /data/user/0.
     * Match only that precise package/files identity; external paths never enter this fallback.
     */
    private fun androidPrivateFilesKey(value: String): String? = runCatching {
        val uri = java.net.URI(value)
        if (!uri.scheme.equals("file", ignoreCase = true)) return@runCatching null
        val path = uri.path ?: return@runCatching null
        val marker = "/files/"
        val markerIndex = path.indexOf(marker)
        if (markerIndex <= 0) return@runCatching null
        val prefix = path.substring(0, markerIndex)
        val packageName = when {
            prefix.startsWith("/data/data/") -> prefix.removePrefix("/data/data/")
            prefix.startsWith("/data/user/0/") -> prefix.removePrefix("/data/user/0/")
            else -> return@runCatching null
        }
        if (packageName.isBlank() || packageName.contains('/')) return@runCatching null
        "$packageName/files/${path.substring(markerIndex + marker.length)}"
    }.getOrNull()
}
