package com.rhodes.privatechat.data.backup

object BackupRestorePayloadRewriter {
    fun rewriteMediaUris(payload: BackupPayload, mediaFiles: Map<String, java.io.File>): BackupPayload {
        val byOriginalUri = payload.media.mapNotNull { item ->
            mediaFiles[item.mediaId]?.let { item.originalUri to it.toURI().toString() }
        }.toMap()
        fun uri(value: String): String = byOriginalUri[value] ?: value
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
                },
            ),
            giftRecords = payload.giftRecords.map { it.copy(imageUri = uri(it.imageUri)) },
            chatArchives = payload.chatArchives.map { archive ->
                archive.copy(messagesJson = content(archive.messagesJson), stateJson = content(archive.stateJson))
            },
            chatHistorySegments = payload.chatHistorySegments.map { history -> history.copy(messagesJson = content(history.messagesJson)) },
        )
    }
}
